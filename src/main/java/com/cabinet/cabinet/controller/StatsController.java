package com.cabinet.cabinet.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Statistiques de l'activité du cabinet (super administrateur, médecin, secrétaire : voir SecurityConfig).
 * Les rendez-vous placés dans la corbeille ne sont jamais comptés.
 */
@SuppressWarnings("null") // avertissements de l'éditeur sur des références de méthode : aucun risque réel
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {
    private static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");
    private static final String ACTIFS = "coalesce(r.supprime, false) = false";
    private final JdbcTemplate jdbc;

    /** Synthèse d'une période : statuts, services, praticiens, jours de la semaine, heures, nouveaux patients. */
    @GetMapping("/synthese")
    @Transactional(readOnly = true)
    public Map<String, Object> synthese(@RequestParam(required = false) LocalDate debut, @RequestParam(required = false) LocalDate fin) {
        LocalDate aujourdHui = LocalDate.now(ZONE);
        LocalDate d = debut != null ? debut : aujourdHui.withDayOfMonth(1);
        LocalDate f = fin != null ? fin : aujourdHui.withDayOfMonth(aujourdHui.lengthOfMonth());
        if (f.isBefore(d)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de fin doit être après la date de début.");
        if (d.plusYears(5).isBefore(f)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La période ne peut pas dépasser 5 ans.");
        Date sd = Date.valueOf(d), sf = Date.valueOf(f);

        Map<String, Long> parStatut = new LinkedHashMap<>();
        for (String statut : List.of("EN_ATTENTE", "CONFIRME", "TERMINE", "ANNULE")) parStatut.put(statut, 0L);
        jdbc.query("select r.statut, count(*) from rendez_vous r where " + ACTIFS + " and r.date_rdv between ? and ? group by r.statut",
                rs -> { parStatut.put(rs.getString(1), rs.getLong(2)); }, sd, sf);
        long total = parStatut.values().stream().mapToLong(Long::longValue).sum();
        long annules = parStatut.get("ANNULE");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("debut", d);
        body.put("fin", f);
        body.put("total", total);
        body.put("parStatut", parStatut);
        body.put("tauxAnnulation", total == 0 ? 0.0 : Math.round(annules * 1000.0 / total) / 10.0);
        body.put("patientsDistincts", jdbc.queryForObject(
                "select count(distinct r.telephone_client) from rendez_vous r where " + ACTIFS + " and r.date_rdv between ? and ?", Long.class, sd, sf));
        body.put("nouveauxPatients", jdbc.queryForObject(
                "select count(*) from (select r.telephone_client, min(r.date_rdv) premier from rendez_vous r where " + ACTIFS
                        + " group by r.telephone_client) t where t.premier between ? and ?", Long.class, sd, sf));
        body.put("frequentation", frequentation(d, f));
        body.put("parService", compter("select coalesce(s.nom, 'Non précisé') libelle, count(*) nb from rendez_vous r left join service s on s.id = r.service_id where "
                + ACTIFS + " and r.statut <> 'ANNULE' and r.date_rdv between ? and ? group by 1 order by 2 desc, 1", sd, sf));
        body.put("parPraticien", compter("select coalesce(u.nom, 'Non attribué') libelle, count(*) nb from rendez_vous r left join utilisateur u on u.id = r.praticien_id where "
                + ACTIFS + " and r.statut <> 'ANNULE' and r.date_rdv between ? and ? group by 1 order by 2 desc, 1", sd, sf));
        body.put("parJourSemaine", compter("select cast(extract(isodow from r.date_rdv) as text) libelle, count(*) nb from rendez_vous r where "
                + ACTIFS + " and r.statut <> 'ANNULE' and r.date_rdv between ? and ? group by 1 order by 1", sd, sf));
        body.put("parHeure", compter("select to_char(r.heure_rdv, 'HH24:MI') libelle, count(*) nb from rendez_vous r where "
                + ACTIFS + " and r.statut <> 'ANNULE' and r.date_rdv between ? and ? group by 1 order by 1", sd, sf));
        return body;
    }

    /** Évolution mensuelle sur les N derniers mois (mois courant inclus), mois sans activité compris. */
    @GetMapping("/mensuel")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> mensuel(@RequestParam(defaultValue = "12") int mois) {
        int n = Math.max(1, Math.min(36, mois));
        YearMonth fin = YearMonth.now(ZONE);
        YearMonth debut = fin.minusMonths(n - 1L);
        Map<String, Map<String, Object>> parMois = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            YearMonth m = debut.plusMonths(i);
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("mois", m.toString());
            ligne.put("total", 0L);
            ligne.put("EN_ATTENTE", 0L);
            ligne.put("CONFIRME", 0L);
            ligne.put("TERMINE", 0L);
            ligne.put("ANNULE", 0L);
            parMois.put(m.toString(), ligne);
        }
        jdbc.query("select to_char(r.date_rdv, 'YYYY-MM') mois, r.statut, count(*) from rendez_vous r where " + ACTIFS
                        + " and r.date_rdv between ? and ? group by 1, 2",
                rs -> {
                    Map<String, Object> ligne = parMois.get(rs.getString(1));
                    if (ligne == null) return;
                    ligne.put(rs.getString(2), rs.getLong(3));
                    ligne.put("total", (Long) ligne.get("total") + rs.getLong(3));
                }, Date.valueOf(debut.atDay(1)), Date.valueOf(fin.atEndOfMonth()));
        return new ArrayList<>(parMois.values());
    }

    /**
     * Fréquentation de la période : passages (rendez-vous non annulés), clients distincts, nouveaux (premier passage de leur vie dans la période) et anciens,
     * puis la même chose par catégorie : femmes, hommes, adolescents (12 à 17 ans), enfants (moins de 12 ans) et « non précisé » (adulte dont le sexe n'est pas renseigné).
     * Un client = même nom et même numéro (voir ClientHistorique) ; sa catégorie est celle de son dernier passage de la période.
     */
    private Map<String, Object> frequentation(LocalDate debut, LocalDate fin) {
        record Client(String categorie, int passages, boolean nouveau) { }
        Map<String, java.time.LocalDate> premierPassage = new java.util.HashMap<>();
        Map<String, Client> clients = new LinkedHashMap<>();
        jdbc.query("select r.nom_client, r.telephone_client, r.age_client, r.sexe, r.date_rdv from rendez_vous r where " + ACTIFS
                + " and r.statut <> 'ANNULE' and r.date_rdv <= ? order by r.date_rdv, r.heure_rdv, r.id", rs -> {
            String cle = com.cabinet.cabinet.util.ClientHistorique.cle(rs.getString(1), rs.getString(2));
            LocalDate jour = rs.getDate(5).toLocalDate();
            premierPassage.putIfAbsent(cle, jour);
            if (jour.isBefore(debut)) return;
            int age = rs.getInt(3);
            String categorie = com.cabinet.cabinet.util.Civilite.categorie(age, rs.getString(4));
            Client avant = clients.get(cle);
            clients.put(cle, new Client(categorie, (avant == null ? 0 : avant.passages()) + 1, !premierPassage.get(cle).isBefore(debut)));
        }, Date.valueOf(fin));

        Map<String, String> libelles = new LinkedHashMap<>();
        libelles.put("FEMME", "Femmes"); libelles.put("HOMME", "Hommes"); libelles.put("ADOLESCENT", "Adolescents"); libelles.put("ENFANT", "Enfants"); libelles.put("NON_PRECISE", "Non précisé");
        Map<String, long[]> parCategorie = new LinkedHashMap<>();   // patients, passages, nouveaux
        libelles.keySet().forEach(code -> parCategorie.put(code, new long[3]));
        long passages = 0, nouveaux = 0;
        for (Client c : clients.values()) {
            long[] ligne = parCategorie.get(c.categorie());
            ligne[0]++; ligne[1] += c.passages(); if (c.nouveau()) { ligne[2]++; nouveaux++; }
            passages += c.passages();
        }
        List<Map<String, Object>> lignes = new ArrayList<>();
        parCategorie.forEach((code, v) -> {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("code", code); l.put("libelle", libelles.get(code));
            l.put("patients", v[0]); l.put("passages", v[1]); l.put("nouveaux", v[2]); l.put("anciens", v[0] - v[2]);
            lignes.add(l);
        });
        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("passages", passages);
        resultat.put("patients", (long) clients.size());
        resultat.put("nouveaux", nouveaux);
        resultat.put("anciens", clients.size() - nouveaux);
        resultat.put("parCategorie", lignes);
        return resultat;
    }

    private List<Map<String, Object>> compter(String sql, Object... args) {
        return jdbc.query(sql, (rs, i) -> {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("libelle", rs.getString("libelle"));
            ligne.put("nombre", rs.getLong("nb"));
            return ligne;
        }, args);
    }
}
