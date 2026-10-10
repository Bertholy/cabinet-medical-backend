package com.cabinet.cabinet.util;

import java.util.Locale;

public final class TextUtils {
    private TextUtils() { }

    /** Caractères accentués (minuscules puis majuscules) et leur équivalent sans accent : transmis à la fonction SQL translate() des recherches. */
    public static final String ACCENTS = "àâäáãåéèêëíìîïóòôöõúùûüçñœÀÂÄÁÅÉÈÊËÍÌÎÏÓÒÔÖÕÚÙÛÜÇÑŒ";
    public static final String PLAIN =   "aaaaaaeeeeiiiiooooouuuucnoaaaaaeeeeiiiiooooouuuucno";

    /** minuscules, sans accents, espaces réduits : « Héry  RAKOTO » → « hery rakoto ». */
    public static String fold(String value) {
        if (value == null) return "";
        String lower = value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        StringBuilder out = new StringBuilder(lower.length());
        for (char c : lower.toCharArray()) {
            int index = ACCENTS.indexOf(c);
            out.append(index >= 0 ? PLAIN.charAt(index) : c);
        }
        return out.toString();
    }

    /** Échappe % et _ pour un LIKE ... ESCAPE '\'. */
    public static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
