package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.service.MaterniteService;
import com.cabinet.cabinet.util.PhoneUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Suivi de grossesse et carnet de santé de l'enfant. Les routes sont sous /api/dossiers : mêmes droits que le dossier patient
 * (personnel soignant ; la secrétaire n'y a pas accès ; la suppression est réservée au médecin et au super administrateur).
 */
@RestController
@RequestMapping("/api/dossiers")
@RequiredArgsConstructor
public class MaterniteController {
    private final MaterniteService maternite;

    public record GrossesseRequest(LocalDate ddr, LocalDate dpa, @Size(max = 10) String groupeSanguin,
                                   @Size(max = 1000) String risques, @Size(max = 2000) String notes) { }

    public record FinRequest(String statut, LocalDate dateFin, @Size(max = 300) String issue) { }

    public record ConsultationRequest(LocalDate date, BigDecimal poidsKg, Integer tensionSystolique, Integer tensionDiastolique, Integer hauteurUterineCm,
                                      Integer bcf, boolean oedemes, @Size(max = 10) String albuminurie, @Size(max = 40) String presentation,
                                      List<String> actes, @Size(max = 2000) String observations, LocalDate prochainRdv) { }

    public record EnfantRequest(@Size(max = 100) String nom, @Size(max = 1) String sexe, LocalDate dateNaissance, Integer poidsNaissanceG,
                                Long grossesseId, @Size(max = 1000) String notes) { }

    public record VaccinRequest(LocalDate dateFaite, @Size(max = 60) String lot) { }

    @GetMapping("/{telephone}/maternite")
    public Map<String, Object> vue(@PathVariable String telephone) {
        return maternite.vue(canonique(telephone));
    }

    @GetMapping("/maternite/rappels")
    public Map<String, Object> rappels() {
        return maternite.rappels();
    }

    @PostMapping("/{telephone}/grossesses")
    public ResponseEntity<Map<String, Object>> creer(@PathVariable String telephone, @Valid @RequestBody GrossesseRequest r, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(maternite.creerGrossesse(canonique(telephone), r.ddr(), r.dpa(), r.groupeSanguin(), r.risques(), r.notes(), auth.getName()));
    }

    @PutMapping("/grossesses/{id}")
    public Map<String, Object> modifier(@PathVariable Long id, @Valid @RequestBody GrossesseRequest r) {
        return maternite.modifierGrossesse(id, r.ddr(), r.dpa(), r.groupeSanguin(), r.risques(), r.notes());
    }

    @PutMapping("/grossesses/{id}/terminer")
    public Map<String, Object> terminer(@PathVariable Long id, @Valid @RequestBody FinRequest r) {
        return maternite.terminerGrossesse(id, r.statut(), r.dateFin(), r.issue());
    }

    @DeleteMapping("/grossesses/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        maternite.supprimerGrossesse(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/grossesses/{id}/consultations")
    public ResponseEntity<Map<String, Object>> ajouterConsultation(@PathVariable Long id, @Valid @RequestBody ConsultationRequest r, Authentication auth) {
        MaterniteService.MesuresCpn mesures = new MaterniteService.MesuresCpn(r.date(), r.poidsKg(), r.tensionSystolique(), r.tensionDiastolique(),
                r.hauteurUterineCm(), r.bcf(), r.oedemes(), r.albuminurie(), r.presentation(), r.actes(), r.observations(), r.prochainRdv());
        return ResponseEntity.status(HttpStatus.CREATED).body(maternite.ajouterConsultation(id, mesures, auth.getName()));
    }

    @DeleteMapping("/grossesses/consultations/{id}")
    public ResponseEntity<Void> supprimerConsultation(@PathVariable Long id) {
        maternite.supprimerConsultation(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{telephone}/enfants")
    public ResponseEntity<Map<String, Object>> creerEnfant(@PathVariable String telephone, @Valid @RequestBody EnfantRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(maternite.creerEnfant(canonique(telephone), r.nom(), r.sexe(), r.dateNaissance(), r.poidsNaissanceG(), r.grossesseId(), r.notes()));
    }

    @DeleteMapping("/enfants/{id}")
    public ResponseEntity<Void> supprimerEnfant(@PathVariable Long id) {
        maternite.supprimerEnfant(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/enfants/{id}/vaccins/{code}")
    public Map<String, Object> vacciner(@PathVariable Long id, @PathVariable String code, @Valid @RequestBody(required = false) VaccinRequest r, Authentication auth) {
        return maternite.enregistrerVaccin(id, code, r == null ? null : r.dateFaite(), r == null ? null : r.lot(), auth.getName());
    }

    @PutMapping("/enfants/{id}/vaccins/{code}/annuler")
    public Map<String, Object> annulerVaccin(@PathVariable Long id, @PathVariable String code) {
        return maternite.annulerVaccin(id, code);
    }

    private String canonique(String telephone) {
        String tel = PhoneUtils.canonical(telephone);
        if (tel == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Numéro de téléphone invalide.");
        return tel;
    }
}
