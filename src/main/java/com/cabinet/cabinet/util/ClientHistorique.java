package com.cabinet.cabinet.util;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reconnaît un client qui revient : même numéro de téléphone ET même nom (sans tenir compte des majuscules ni des accents).
 * Le nom compte parce qu'un parent donne le même numéro pour tous ses enfants, qui ne sont pas la même personne.
 * Un « passage » est un rendez-vous non annulé ; le premier passage d'un client en fait un « nouveau », les suivants un « ancien ».
 */
public final class ClientHistorique {
    private ClientHistorique() { }

    /** Un passage d'un client (rendez-vous non annulé) : quand, et quel rendez-vous. */
    public record Passage(LocalDateTime quand, long id) {
        public static Passage de(LocalDate date, LocalTime heure, Long id) {
            return new Passage(LocalDateTime.of(date, heure == null ? LocalTime.MIDNIGHT : heure), id == null ? 0 : id);
        }
        boolean precede(Passage autre) { return quand.isBefore(autre.quand) || (quand.isEqual(autre.quand) && id < autre.id); }
    }

    /** Clé d'un client : téléphone normalisé + nom normalisé. */
    public static String cle(String nom, String telephone) {
        String n = Normalizer.normalize(nom == null ? "" : nom, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
        return PhoneUtils.searchKey(telephone == null ? "" : telephone) + "|" + n;
    }

    /** Les passages de chaque client, regroupés par clé. */
    public static final class Index {
        private final Map<String, List<Passage>> parClient = new HashMap<>();

        public void ajouter(String nom, String telephone, LocalDate date, LocalTime heure, Long id) {
            parClient.computeIfAbsent(cle(nom, telephone), k -> new ArrayList<>()).add(Passage.de(date, heure, id));
        }

        /** Nombre de passages (non annulés) du client avant celui-ci. */
        public int passagesAvant(String nom, String telephone, LocalDate date, LocalTime heure, Long id) {
            Passage ici = Passage.de(date, heure, id);
            int n = 0;
            for (Passage p : parClient.getOrDefault(cle(nom, telephone), List.of())) if (p.precede(ici)) n++;
            return n;
        }

        /** Nombre total de passages (non annulés) du client. */
        public int passages(String nom, String telephone) { return parClient.getOrDefault(cle(nom, telephone), List.of()).size(); }
    }
}
