package com.simomusic.player;

import android.content.Context;

import java.util.Locale;

public final class Fmt {
    private Fmt() {
    }

    public static String time(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60;
        s %= 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s) : String.format(Locale.US, "%d:%02d", m, s);
    }

    private static boolean arabic(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) >= 0x0600 && s.charAt(i) <= 0x06FF) return true;
        return false;
    }

    /** Arabic tags that were read as Latin-1 look like "¡Ýó": detect that pattern. */
    private static boolean broken(String s) {
        if (s == null || arabic(s)) return false;
        int hi = 0, letters = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= 0x80 && ch <= 0xFF) hi++;
            if (Character.isLetter(ch) || (ch >= 0x80 && ch <= 0xFF)) letters++;
        }
        return hi >= 2 && hi * 2 >= letters;
    }

    private static String recode(String s) {
        try {
            String r = new String(s.getBytes("ISO-8859-1"), "windows-1256");
            return arabic(r) ? r : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Best readable title: the tag, else the file name, else the tag re-decoded as Windows-1256 Arabic. */
    public static String title(String tag, String fileBase) {
        if (tag == null || tag.isEmpty()) return fileBase == null ? "" : fix(fileBase);
        if (!broken(tag)) return tag;
        if (fileBase != null && !broken(fileBase)) return fileBase;
        String r = recode(tag);
        return r != null ? r : tag;
    }

    public static String fix(String s) {
        if (!broken(s)) return s;
        String r = recode(s);
        return r != null ? r : s;
    }

    public static String artist(Context c, String a) {
        return a == null || a.trim().isEmpty() ? c.getString(R.string.unknown_artist) : a;
    }
}
