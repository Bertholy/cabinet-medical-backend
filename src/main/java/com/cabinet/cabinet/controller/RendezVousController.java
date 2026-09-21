package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.dto.RendezVousRequestDTO;
import com.cabinet.cabinet.dto.RendezVousResponseDTO;
import com.cabinet.cabinet.enums.StatutRDV;
import com.cabinet.cabinet.service.RendezVousService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/rdv")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class RendezVousController {

    private final RendezVousService rendezVousService;

    // ============ CRÉER UN RDV (client) ============
    @PostMapping
    public ResponseEntity<RendezVousResponseDTO> creer(@Valid @RequestBody RendezVousRequestDTO dto) {
        RendezVousResponseDTO response = rendezVousService.creerRendezVous(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ============ LISTER TOUS LES RDV (médecin) ============
    @GetMapping
    public ResponseEntity<List<RendezVousResponseDTO>> getAll() {
        return ResponseEntity.ok(rendezVousService.getAllRendezVous());
    }

    // ============ RDV D'UN CLIENT PAR TÉLÉPHONE ============
    @GetMapping("/telephone/{telephone}")
    public ResponseEntity<List<RendezVousResponseDTO>> getParTelephone(@PathVariable String telephone) {
        return ResponseEntity.ok(rendezVousService.getRendezVousParTelephone(telephone));
    }

    // ============ RDV PAR STATUT (médecin) ============
    @GetMapping("/statut/{statut}")
    public ResponseEntity<List<RendezVousResponseDTO>> getParStatut(@PathVariable StatutRDV statut) {
        return ResponseEntity.ok(rendezVousService.getRendezVousParStatut(statut));
    }

    // ============ CRÉNEAUX DISPONIBLES D'UNE JOURNÉE ============
    @GetMapping("/creneaux/{date}")
    public ResponseEntity<List<LocalTime>> getCreneauxDisponibles(@PathVariable String date) {
        LocalDate localDate = LocalDate.parse(date);
        return ResponseEntity.ok(rendezVousService.getCreneauxDisponibles(localDate));
    }

    // ============ CONFIRMER UN RDV (médecin) ============
    @PutMapping("/{id}/confirmer")
    public ResponseEntity<RendezVousResponseDTO> confirmer(@PathVariable Long id) {
        return ResponseEntity.ok(rendezVousService.confirmerRendezVous(id));
    }

    // ============ ANNULER UN RDV ============
    @PutMapping("/{id}/annuler")
    public ResponseEntity<RendezVousResponseDTO> annuler(@PathVariable Long id) {
        return ResponseEntity.ok(rendezVousService.annulerRendezVous(id));
    }
}