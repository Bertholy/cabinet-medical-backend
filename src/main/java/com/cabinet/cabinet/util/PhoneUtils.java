package com.cabinet.cabinet.util;

/**
 * Numéros de téléphone : les patients tapent leur numéro comme ils le connaissent (« 034 12 345 67 », « 34 12 345 67 »,
 * avec ou sans indicatif du pays…) et le système retrouve toujours le même patient.
 *
 * Forme enregistrée (canonique) : numéro malgache local à 10 chiffres « 0341234567 » ; tout autre numéro est conservé
 * en chiffres, avec « + » s'il était international.
 */
public final class PhoneUtils {
    private PhoneUtils() { }

    /** Indicatif de Madagascar, sans « + » : nécessaire seulement pour reconnaître les anciens formats et pour l'envoi des SMS. */
    private static final String INDICATIF = "261";

    public static final int MIN_DIGITS = 8;
    public static final int MAX_DIGITS = 15;

    public static String digits(String raw) {
        return raw == null ? "" : raw.replaceAll("[^0-9]", "");
    }

    /** @return la forme canonique, ou null si le numéro n'a pas entre 8 et 15 chiffres. */
    public static String canonical(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        String digits = digits(trimmed);
        if (digits.startsWith("00" + INDICATIF) && digits.length() == 14) return "0" + digits.substring(5);
        if (digits.startsWith(INDICATIF) && digits.length() == 12) return "0" + digits.substring(3);
        if (digits.length() == 10 && digits.startsWith("0")) return digits;
        if (digits.length() == 9 && digits.startsWith("3")) return "0" + digits; // « 34 12 345 67 » sans le zéro
        if (digits.length() < MIN_DIGITS || digits.length() > MAX_DIGITS) return null;
        return trimmed.startsWith("+") ? "+" + digits : digits;
    }

    /**
     * Clé de comparaison : numéro malgache complet avec indicatif (12 chiffres), sinon les chiffres tels quels.
     * Elle correspond à la normalisation SQL appliquée aux numéros déjà enregistrés (0… → indicatif + …).
     */
    public static String searchKey(String raw) {
        String digits = digits(raw);
        if (digits.startsWith("00" + INDICATIF) && digits.length() == 14) return digits.substring(2);
        if (digits.length() == 9 && digits.startsWith("3")) return INDICATIF + digits;
        if (digits.startsWith("0")) return INDICATIF + digits.substring(1);
        return digits;
    }

    /** Format international exigé par les opérateurs SMS (E.164) : « + », indicatif du pays, numéro sans le zéro initial. */
    public static String toInternational(String raw) {
        String digits = digits(raw);
        if (digits.isEmpty()) return raw;
        String key = searchKey(raw);
        return "+" + key;
    }
}
