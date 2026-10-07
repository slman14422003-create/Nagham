package com.simomusic.player;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Snapshot of the connected Bluetooth headset, whatever kind it is (A2DP, LE Audio, hands-free, hearing aid). */
public final class BtInfo {
    public interface Cb {
        void done(BtInfo i);
    }

    public boolean hasDevice, needPerm;
    public String name = "", address = "";
    public int audioType;            // AudioDeviceInfo.TYPE_*
    public int btClass = -1;         // BluetoothClass.Device.*
    public boolean bonded, a2dp, hfp, playing;
    public int battery = -1;
    public int[] rates = new int[0], channels = new int[0], encodings = new int[0];

    @SuppressLint("MissingPermission")
    public static void load(final Context c, final Cb cb) {
        final BtInfo i = new BtInfo();
        final Handler main = new Handler(Looper.getMainLooper());
        AudioDeviceInfo d = BtAudio.primary(c);
        if (d == null && !BtAudio.outputs(c).isEmpty()) d = BtAudio.outputs(c).get(0);
        if (d == null) {
            cb.done(i);
            return;
        }
        i.hasDevice = true;
        i.audioType = d.getType();
        CharSequence pn = d.getProductName();
        i.name = pn == null ? "" : pn.toString();
        if (Build.VERSION.SDK_INT >= 28) i.address = d.getAddress();
        try {
            i.rates = d.getSampleRates();
            i.channels = d.getChannelCounts();
            i.encodings = d.getEncodings();
        } catch (Exception ignored) {
        }
        if (!Perms.hasBt(c)) {
            i.needPerm = true;
            cb.done(i);
            return;
        }
        final BluetoothAdapter ad;
        BluetoothDevice dev = null;
        try {
            BluetoothManager bm = (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
            ad = bm == null ? null : bm.getAdapter();
            if (ad != null && ad.getBondedDevices() != null) {
                for (BluetoothDevice x : ad.getBondedDevices()) {
                    if (!i.address.isEmpty() && i.address.equalsIgnoreCase(x.getAddress())) {
                        dev = x;
                        break;
                    }
                }
                if (dev == null && !i.name.isEmpty()) {
                    for (BluetoothDevice x : ad.getBondedDevices()) {
                        if (i.name.equals(x.getName())) {
                            dev = x;
                            break;
                        }
                    }
                }
            }
        } catch (SecurityException e) {
            i.needPerm = true;
            cb.done(i);
            return;
        }
        if (dev == null) {
            cb.done(i);
            return;
        }
        final BluetoothDevice bd = dev;
        try {
            String nm = Build.VERSION.SDK_INT >= 30 ? bd.getAlias() : bd.getName();
            if (nm != null && !nm.isEmpty()) i.name = nm;
            if (i.address.isEmpty()) i.address = bd.getAddress();
            i.bonded = bd.getBondState() == BluetoothDevice.BOND_BONDED;
            BluetoothClass bc = bd.getBluetoothClass();
            if (bc != null) i.btClass = bc.getDeviceClass();
            i.battery = battery(bd);
        } catch (SecurityException ignored) {
        }

        // connection profiles: asked from the system asynchronously, then closed again
        final AtomicInteger left = new AtomicInteger(2);
        final AtomicBoolean fin = new AtomicBoolean();
        final Runnable finish = () -> {
            if (fin.compareAndSet(false, true)) cb.done(i);
        };
        main.postDelayed(finish, 1500);
        final BluetoothProfile.ServiceListener sl = new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                try {
                    boolean on = proxy.getConnectionState(bd) == BluetoothProfile.STATE_CONNECTED;
                    if (profile == BluetoothProfile.A2DP) {
                        i.a2dp = on;
                        if (on && proxy instanceof BluetoothA2dp) i.playing = ((BluetoothA2dp) proxy).isA2dpPlaying(bd);
                    } else if (profile == BluetoothProfile.HEADSET) {
                        i.hfp = on;
                    }
                } catch (Exception ignored) {
                }
                try {
                    ad.closeProfileProxy(profile, proxy);
                } catch (Exception ignored) {
                }
                if (left.decrementAndGet() == 0) main.post(finish);
            }

            @Override
            public void onServiceDisconnected(int profile) {
            }
        };
        boolean ok1 = false, ok2 = false;
        try {
            ok1 = ad.getProfileProxy(c.getApplicationContext(), sl, BluetoothProfile.A2DP);
            ok2 = ad.getProfileProxy(c.getApplicationContext(), sl, BluetoothProfile.HEADSET);
        } catch (Exception ignored) {
        }
        if (!ok1 && left.decrementAndGet() == 0) main.post(finish);
        if (!ok2 && left.decrementAndGet() == 0) main.post(finish);
    }

    /** Hidden on some versions; shown only when the system hands it out. */
    @SuppressLint({"DiscouragedPrivateApi", "PrivateApi"})
    private static int battery(BluetoothDevice d) {
        try {
            Object r = BluetoothDevice.class.getMethod("getBatteryLevel").invoke(d);
            if (r instanceof Integer && (Integer) r >= 0 && (Integer) r <= 100) return (Integer) r;
        } catch (Throwable ignored) {
        }
        return -1;
    }
}
