package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.enums.Role;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import com.cabinet.cabinet.security.PermissionService;
import com.cabinet.cabinet.security.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Petits nombres du menu du personnel : ce qui est nouveau depuis la dernière visite de chaque page.
 * Le navigateur garde l'heure de sa dernière visite par page et la renvoie ; une page sans heure connue n'a pas de nombre (0).
 * Chaque compteur n'est calculé que si la personne a le droit de voir la page correspondante. L'heure de référence est celle du serveur
 * (« maintenant » est renvoyé), pour ne pas dépendre de l'horloge de l'appareil.
 */
@RestController
@RequestMapping("/api/nouveautes")
@RequiredArgsConstructor
public class NouveautesController {
    private static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");

    private final JdbcTemplate jdbc;
    private final UtilisateurRepository utilisateurs;
    private final PermissionService permissions;

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> lire(Authentication authentication,
                                    @RequestParam(required = false) String rdv,
                                    @RequestParam(required = false) String calendrier,
                                    @RequestParam(required = false) String corbeille,
                                    @RequestParam(required = false) String dossiers,
                                    @RequestParam(required = false) String maternite,
                                    @RequestParam(required = false) String journal) {
        Utilisateur moi = utilisateurs.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
        Set<String> droits = permissions.de(moi);
        // comme pour la liste des rendez-vous : le super administrateur et la secrétaire voient tout, les soignants seulement les leurs
        boolean tous = moi.getRole() == Role.SUPER_ADMIN || moi.getRole() == Role.SECRETAIRE;
        LocalDateTime maintenant = LocalDateTime.now(ZONE);

        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("maintenant", maintenant.toString());
        if (droits.contains(Permissions.RDV_VOIR)) {
            corps.put("rdv", nouveauxRdv(depuis(rdv), tous, moi.getId()));
            corps.put("calendrier", nouveauxRdv(depuis(calendrier), tous, moi.getId()));
        }
        if (droits.contains(Permissions.RDV_CORBEILLE)) {
            LocalDateTime d = depuis(corbeille);
            corps.put("corbeille", d == null ? 0 : compter("select count(*) from rendez_vous where supprime = true and deleted_at > ?", d));
        }
        if (droits.contains(Permissions.DOSSIERS_LIRE)) {
            LocalDateTime d = depuis(dossiers);
            corps.put("dossiers", d == null ? 0 : compter(
                    "select count(*) from consultation where cree_le > ? and (auteur_email is null or lower(auteur_email) <> lower(?))", d, moi.getEmail()));
            LocalDateTime m = depuis(maternite);
            corps.put("maternite", m == null ? 0 : compter("select (select count(*) from grossesse where cree_le > ?) + (select count(*) from consultation_prenatale where cree_le > ?)"
                    + " + (select count(*) from enfant where cree_le > ?)", m, m, m));
        }
        if (droits.contains(Permissions.JOURNAL_VOIR)) {
            LocalDateTime d = depuis(journal);
            corps.put("journal", d == null ? 0 : compter("select count(*) from journal_audit where date_heure > ?", d));
        }
        if (droits.contains(Permissions.RDV_ACCEPTER)) {
            // état, pas « nouveau » : les rendez-vous confiés à cette personne qu'elle n'a pas encore acceptés
            corps.put("aAccepter", compter("select count(*) from rendez_vous where (supprime = false or supprime is null) and praticien_id = ? and personnel_confirme_le is null"
                    + " and statut in ('EN_ATTENTE', 'CONFIRME') and date_rdv >= ?", moi.getId(), LocalDate.now(ZONE)));
        }
        return corps;
    }

    private int nouveauxRdv(LocalDateTime depuis, boolean tous, Long moiId) {
        if (depuis == null) return 0;
        return tous
                ? compter("select count(*) from rendez_vous where (supprime = false or supprime is null) and date_creation > ?", depuis)
                : compter("select count(*) from rendez_vous where (supprime = false or supprime is null) and date_creation > ? and praticien_id = ?", depuis, moiId);
    }

    private int compter(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private static LocalDateTime depuis(String valeur) {
        if (valeur == null || valeur.isBlank()) return null;
        try { return LocalDateTime.parse(valeur.trim()); }
        catch (DateTimeParseException e) { return null; }
    }
}
