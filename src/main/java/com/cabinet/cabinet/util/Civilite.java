package com.cabinet.cabinet.util;

/**
 * Civilité d'un patient, calculée automatiquement d'après son âge et son sexe : « Enfant » avant 18 ans, puis « Mr » (homme) ou « Mme » (femme).
 * Rien n'est enregistré en base : seul le sexe l'est, la civilité suit l'âge (elle change toute seule quand un enfant devient majeur).
 */
public final class Civilite {
    private Civilite() { }

    public static final String HOMME = "HOMME";
    public static final String FEMME = "FEMME";
    /** Âge à partir duquel un patient est un adolescent (avant : un enfant). */
    public static final int AGE_ADOLESCENT = 12;
    /** Âge à partir duquel un patient est un adulte (Mr / Mme) ; de 12 à 17 ans c'est un adolescent. Un mineur reste suivi par son parent ou son responsable. */
    public static final int AGE_ADULTE = 18;

    /** « HOMME », « FEMME » ou null (valeur vide ou inconnue) : forme enregistrée du sexe. */
    public static String normaliserSexe(String sexe) {
        if (sexe == null) return null;
        String valeur = sexe.trim().toUpperCase(java.util.Locale.ROOT);
        return HOMME.equals(valeur) || FEMME.equals(valeur) ? valeur : null;
    }

    /** Catégorie d'un patient pour les listes et les statistiques : ENFANT (moins de 12 ans), ADOLESCENT (12 à 17 ans), puis FEMME, HOMME ou NON_PRECISE pour un adulte. */
    public static String categorie(Integer age, String sexe) {
        if (age != null && age < AGE_ADOLESCENT) return "ENFANT";
        if (age != null && age < AGE_ADULTE) return "ADOLESCENT";
        String s = normaliserSexe(sexe);
        return s == null ? "NON_PRECISE" : s;
    }

    /** « Enfant » (moins de 12 ans), « Adolescent » (12 à 17 ans), « Mr », « Mme », ou null quand on ne sait pas (adulte dont le sexe n'est pas précisé). */
    public static String de(Integer age, String sexe) {
        if (age != null && age < AGE_ADOLESCENT) return "Enfant";
        if (age != null && age < AGE_ADULTE) return "Adolescent";
        String s = normaliserSexe(sexe);
        if (HOMME.equals(s)) return "Mr";
        if (FEMME.equals(s)) return "Mme";
        return null;
    }
}
