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

    private List<Map<String, Object>> compter(String sql, Object... args) {
        return jdbc.query(sql, (rs, i) -> {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("libelle", rs.getString("libelle"));
            ligne.put("nombre", rs.getLong("nb"));
            return ligne;
        }, args);
    }
}
