package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.dto.PlanningDTO;
import com.cabinet.cabinet.dto.PlanningDTO.JourFermeDTO;
import com.cabinet.cabinet.entity.JourFerme;
import com.cabinet.cabinet.repository.RendezVousRepository;
import com.cabinet.cabinet.service.AuditService;
import com.cabinet.cabinet.service.PlanningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lecture publique des horaires (le site affiche les jours fermés) ;
 * modification réservée au super administrateur (/api/admin/** dans SecurityConfig).
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@SuppressWarnings("null") // avertissements de l'éditeur sur des références de méthode : aucun risque réel
public class PlanningController {
    private static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");
    private final PlanningService planning;
    private final RendezVousRepository rendezVous;
    private final AuditService audit;

    @GetMapping("/planning")
    public Map<String, Object> lire() {
        PlanningDTO p = planning.lire();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dureeCreneauMinutes", p.dureeCreneauMinutes());
        body.put("capaciteParCreneau", p.capaciteParCreneau());
        body.put("horaires", p.horaires());
        body.put("joursFermes", planning.joursFermesAVenir(LocalDate.now(ZONE)));
        return body;
    }

    /** Pourquoi une journée n'a-t-elle aucun créneau ? (fermeture exceptionnelle, jour non travaillé…) */
    @GetMapping("/planning/jour/{date}")
    public Map<String, Object> jour(@PathVariable LocalDate date) {
        var ferme = planning.jourFerme(date);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date", date);
        body.put("ferme", ferme.isPresent());
        body.put("motif", ferme.map(JourFerme::getMotif).orElse(null));
        body.put("ouvert", !planning.creneaux(date).isEmpty());
        return body;
    }

    @PutMapping("/admin/planning")
    public PlanningDTO enregistrer(@Valid @RequestBody PlanningDTO dto) {
        PlanningDTO saved = planning.enregistrer(dto);
        audit.log("PLANNING_MODIFIE", "Planning", 1, "Créneaux de " + saved.dureeCreneauMinutes() + " min, " + saved.capaciteParCreneau() + " RDV simultané(s)");
        return saved;
    }

    @PostMapping("/admin/jours-fermes")
    public ResponseEntity<Map<String, Object>> fermer(@Valid @RequestBody JourFermeDTO dto) {
        JourFermeDTO saved = planning.fermer(dto.date(), dto.motif());
        long impactes = rendezVous.countActifsLe(dto.date());
        audit.log("JOUR_FERME", "JourFerme", saved.id(), dto.date() + (saved.motif() == null ? "" : " — " + saved.motif()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jourFerme", saved);
        body.put("rdvImpactes", impactes);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @DeleteMapping("/admin/jours-fermes/{id}")
    public ResponseEntity<Void> rouvrir(@PathVariable Long id) {
        LocalDate date = planning.rouvrir(id);
        audit.log("JOUR_ROUVERT", "JourFerme", id, String.valueOf(date));
        return ResponseEntity.noContent().build();
    }
}
