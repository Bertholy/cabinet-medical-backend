package com.cabinet.cabinet.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Mise en forme d'un texte du site (gras, italique, taille, couleur, alignement), validée avant d'être enregistrée :
 * seules ces cinq options existent et leurs valeurs sont bornées, donc aucun code ne peut être injecté dans le site.
 * Stockée sous forme « gras=1;taille=28;couleur=#0f766e » (une seule colonne).
 */
public final class StyleTexte {
    private static final Pattern COULEUR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Set<String> ALIGNEMENTS = Set.of("left", "center", "right");
    public static final int TAILLE_MIN = 10, TAILLE_MAX = 120;

    private StyleTexte() { }

    /** Valide la demande ; retourne le texte à enregistrer, ou null si aucune option n'est active. */
    public static String valider(Map<String, Object> demande) {
        if (demande == null || demande.isEmpty()) return null;
        Map<String, Object> propre = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : demande.entrySet()) {
            Object valeur = e.getValue();
            if (valeur == null) continue;
            switch (e.getKey()) {
                case "gras", "italique" -> { if (booleen(valeur)) propre.put(e.getKey(), true); }
                case "taille" -> {
                    int taille = entier(valeur);
                    if (taille < TAILLE_MIN || taille > TAILLE_MAX) throw invalide("La taille doit être comprise entre " + TAILLE_MIN + " et " + TAILLE_MAX + ".");
                    propre.put("taille", taille);
                }
                case "couleur" -> {
                    String couleur = String.valueOf(valeur).trim();
                    if (!COULEUR.matcher(couleur).matches()) throw invalide("Couleur invalide (format #rrggbb).");
                    propre.put("couleur", couleur.toLowerCase());
                }
                case "alignement" -> {
                    String alignement = String.valueOf(valeur).trim();
                    if (!ALIGNEMENTS.contains(alignement)) throw invalide("Alignement invalide.");
                    propre.put("alignement", alignement);
                }
                default -> throw invalide("Option de mise en forme inconnue : " + e.getKey());
            }
        }
        if (propre.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        propre.forEach((k, v) -> sb.append(sb.length() == 0 ? "" : ";").append(k).append('=').append(v instanceof Boolean ? "1" : v));
        return sb.toString();
    }

    /** Relit la mise en forme enregistrée (ignore silencieusement ce qui serait invalide). */
    public static Map<String, Object> lire(String enregistre) {
        if (enregistre == null || enregistre.isBlank()) return null;
        Map<String, Object> style = new LinkedHashMap<>();
        for (String partie : enregistre.split(";")) {
            int i = partie.indexOf('=');
            if (i < 1) continue;
            String cle = partie.substring(0, i), valeur = partie.substring(i + 1);
            try {
                switch (cle) {
                    case "gras", "italique" -> style.put(cle, true);
                    case "taille" -> style.put(cle, Integer.parseInt(valeur));
                    case "couleur" -> { if (COULEUR.matcher(valeur).matches()) style.put(cle, valeur); }
                    case "alignement" -> { if (ALIGNEMENTS.contains(valeur)) style.put(cle, valeur); }
                    default -> { }
                }
            } catch (NumberFormatException ignore) { /* valeur illisible : ignorée */ }
        }
        return style.isEmpty() ? null : style;
    }

    private static boolean booleen(Object v) { return v instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(v)) || "1".equals(String.valueOf(v)); }

    private static int entier(Object v) {
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v).trim()); } catch (NumberFormatException e) { throw invalide("La taille doit être un nombre."); }
    }

    private static ResponseStatusException invalide(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
