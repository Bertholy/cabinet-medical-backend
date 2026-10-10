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

    @GetMapping("/recherche")
    public ResponseEntity<List<Map<String, String>>> rechercher(@RequestParam String q) {
        String terme = q == null ? "" : q.trim();
        if (terme.length() < 2) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(rendezVousService.rechercherPatients(terme));
    }
}
