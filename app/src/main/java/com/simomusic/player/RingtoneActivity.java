package com.simomusic.player;

import android.Manifest;
import android.app.Activity;
import android.app.RecoverableSecurityException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;

import java.util.Collections;

/**
 * Marks a song as the device's ringtone. Has no screen of its own; works from any screen (song menu, player
 * "more" menu). Two permissions are involved and Android asks for each differently depending on the version:
 *   - "Modify system settings" (WRITE_SETTINGS): needed by RingtoneManager to actually change the ringtone.
 *   - Permission to edit this particular file's MediaStore row, since the app did not create it:
 *       Android 11+ shows the system's own "allow SimoMusic to modify this song?" prompt (createWriteRequest),
 *       Android 10 asks for the one-time access the same way DeleteActivity does, older versions only need the
 *       classic storage permission.
 */
public class RingtoneActivity extends Activity {
    private static final int REQ_WRITE_SYS = 96, REQ_STORAGE_PERM = 97;

    private long id = -1;
    private Uri uri;
    private String title;
    private boolean askedWriteSettings, done;

    public static void start(Context c, Track t) {
        Intent i = new Intent(c, RingtoneActivity.class)
                .putExtra("id", t.id).putExtra("uri", t.uri).putExtra("title", t.title);
        Ui.go(c, i);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            id = getIntent().getLongExtra("id", -1);
            uri = androidx.core.content.IntentCompat.getParcelableExtra(getIntent(), "uri", Uri.class);
            title = getIntent().getStringExtra("title");
            if (id < 0 || uri == null) {
                finish();
                return;
            }
            if (b == null) begin();
        } catch (Throwable t) {
            CrashGuard.nonFatal("ringtone", t);
            fail();
        }
    }

    private void begin() {
        if (!Perms.hasWriteSettings(this)) {
            askedWriteSettings = true;
            Perms.askWriteSettings(this);
            return;
        }
        mark();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (done) return;
        if (askedWriteSettings) {
            askedWriteSettings = false;
            if (!Perms.hasWriteSettings(this)) {
                fail();
                return;
            }
            mark();
        }
    }

    private void mark() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                android.content.IntentSender s =
                        MediaStore.createWriteRequest(getContentResolver(), Collections.singletonList(uri)).getIntentSender();
                startIntentSenderForResult(s, REQ_WRITE_SYS, null, 0, 0, 0);
            } else if (Build.VERSION.SDK_INT == 29) {
                try {
                    apply();
                } catch (RecoverableSecurityException e) {
                    startIntentSenderForResult(e.getUserAction().getActionIntent().getIntentSender(), REQ_WRITE_SYS, null, 0, 0, 0);
                }
            } else if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                apply();
            } else {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE_PERM);
            }
        } catch (Exception e) {
            CrashGuard.nonFatal("ringtone", e);
            fail();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_WRITE_SYS) return;
        if (res != RESULT_OK) {
            fail();
            return;
        }
        try {
            apply();
        } catch (Exception e) {
            CrashGuard.nonFatal("ringtone", e);
            fail();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        super.onRequestPermissionsResult(req, p, r);
        if (req == REQ_STORAGE_PERM && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) {
            try {
                apply();
            } catch (Exception e) {
                CrashGuard.nonFatal("ringtone", e);
                fail();
            }
        } else {
            finish();
        }
    }

    private void apply() {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Audio.Media.IS_RINGTONE, true);
        cv.put(MediaStore.Audio.Media.IS_ALARM, false);
        cv.put(MediaStore.Audio.Media.IS_NOTIFICATION, false);
        cv.put(MediaStore.Audio.Media.IS_MUSIC, true);
        getContentResolver().update(uri, cv, null, null);
        RingtoneManager.setActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE, uri);
        done();
    }

    private void done() {
        done = true;
        Ui.toast(this, getString(R.string.ringtone_set, title));
        finish();
    }

    private void fail() {
        Ui.toast(this, R.string.ringtone_failed);
        finish();
    }
}
