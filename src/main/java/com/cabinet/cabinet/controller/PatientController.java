package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.service.RendezVousService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {
    private final RendezVousService rendezVousService;
    private final com.cabinet.cabinet.service.VerificationPatientService verification;

    /** Pendant que la secrétaire tape un nom (et un numéro) : le client est-il un patient existant, un nouveau, ou y ressemble-t-il ? */
    @GetMapping("/verification")
    public Map<String, Object> verifier(@RequestParam(required = false) String nom, @RequestParam(required = false) String telephone) {
        return verification.verifier(nom, telephone);
    }

    /** La liste des patients (un par client) : nouveau (un seul passage) ou ancien (plusieurs), avec les totaux femmes / hommes / enfants. */
    @GetMapping("/liste")
    public Map<String, Object> liste(@RequestParam(required = false) String q, @RequestParam(required = false) String statut) {
        return verification.liste(q, statut);
    }

    @GetMapping("/recherche")
    public ResponseEntity<List<Map<String, String>>> rechercher(@RequestParam String q) {
        String terme = q == null ? "" : q.trim();
        if (terme.length() < 2) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(rendezVousService.rechercherPatients(terme));
    }
}
