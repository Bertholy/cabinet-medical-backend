package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.entity.Maladie;
import com.cabinet.cabinet.repository.MaladieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/maladies")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class MaladieController {

    private final MaladieRepository maladieRepository;

    // Lister toutes les maladies (public)
    @GetMapping
    public ResponseEntity<List<Maladie>> getAll() {
        return ResponseEntity.ok(maladieRepository.findAll());
    }

    // Détails d'une maladie (avec ses traitements)
    @GetMapping("/{id}")
    public ResponseEntity<Maladie> getById(@PathVariable Long id) {
        return maladieRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Ajouter une maladie (public pour test)
    @PostMapping
    public ResponseEntity<Maladie> create(@RequestBody Maladie maladie) {
        return ResponseEntity.ok(maladieRepository.save(maladie));
    }
}