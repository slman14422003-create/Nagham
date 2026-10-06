package com.nagham.player;

import android.app.Activity;
import android.app.RecoverableSecurityException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deletes songs from the phone the way each Android version allows it: Android 11+ shows the system's own
 * "allow Nagham to delete this song?" prompt, Android 10 asks for the one-time access, older versions delete directly
 * (after the storage permission). It has no UI of its own and works from any screen.
 */
public class DeleteActivity extends Activity {
    private static final int REQ_SYSTEM = 91, REQ_PERM = 92;
    private final List<Uri> uris = new ArrayList<>();
    private final Set<Long> ids = new HashSet<>();

    public static void start(Context c, Track t) {
        Intent i = new Intent(c, DeleteActivity.class).putExtra("id", t.id).putExtra("uri", t.uri);
        Ui.go(c, i);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            long id = getIntent().getLongExtra("id", -1);
            Uri u = androidx.core.content.IntentCompat.getParcelableExtra(getIntent(), "uri", Uri.class);
            if (id < 0 || u == null) {
                finish();
                return;
            }
            ids.add(id);
            uris.add(u);
            if (b == null) begin();
        } catch (Throwable t) {
            CrashGuard.nonFatal("delete", t);
            fail();
        }
    }

    private void begin() throws Exception {
        ContentResolver cr = getContentResolver();
        if (Build.VERSION.SDK_INT >= 30) {
            IntentSender s = MediaStore.createDeleteRequest(cr, uris).getIntentSender();
            startIntentSenderForResult(s, REQ_SYSTEM, null, 0, 0, 0);
        } else if (Build.VERSION.SDK_INT >= 29) {
            deleteQ();
        } else if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            if (cr.delete(uris.get(0), null, null) > 0) done();
            else fail();
        } else {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERM);
        }
    }

    /** Android 10: delete; if the system says this file needs the user's consent, ask once and retry. */
    private void deleteQ() throws Exception {
        try {
            if (getContentResolver().delete(uris.get(0), null, null) > 0) done();
            else fail();
        } catch (RecoverableSecurityException e) {
            startIntentSenderForResult(e.getUserAction().getActionIntent().getIntentSender(), REQ_SYSTEM, null, 0, 0, 0);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_SYSTEM) return;
        if (res != RESULT_OK) {
            finish();
            return;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            done();                              // the system already deleted it
        } else {
            try {
                if (getContentResolver().delete(uris.get(0), null, null) > 0) done();
                else fail();
            } catch (Exception e) {
                CrashGuard.nonFatal("delete", e);
                fail();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        super.onRequestPermissionsResult(req, p, r);
        if (req == REQ_PERM && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) {
            try {
                begin();
            } catch (Exception e) {
                fail();
            }
        } else {
            finish();
        }
    }

    private void done() {
        Library.remove(ids);
        Store.forget(this, ids);
        Pb.removeIds(ids);
        Ui.toast(this, R.string.song_deleted);
        finish();
    }

    private void fail() {
        Ui.toast(this, R.string.delete_failed);
        finish();
    }
}
