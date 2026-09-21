package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.entity.Service;
import com.cabinet.cabinet.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class ServiceController {

    private final ServiceRepository serviceRepository;

    // Lister tous les services (public)
    @GetMapping
    public ResponseEntity<List<Service>> getAll() {
        return ResponseEntity.ok(serviceRepository.findAll());
    }

    // Ajouter un service (public pour test)
    @PostMapping
    public ResponseEntity<Service> create(@RequestBody Service service) {
        return ResponseEntity.ok(serviceRepository.save(service));
    }
}