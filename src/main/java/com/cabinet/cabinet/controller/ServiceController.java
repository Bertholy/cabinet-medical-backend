package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.entity.Service;
import com.cabinet.cabinet.repository.RendezVousRepository;
import com.cabinet.cabinet.repository.ServiceRepository;
import com.cabinet.cabinet.service.AuditService;
import com.cabinet.cabinet.service.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceRepository serviceRepository;
    private final ImageStorageService imageStorageService;
    private final RendezVousRepository rendezVousRepository;
    private final AuditService audit;

    // Lister tous les services (public)
    @GetMapping
    public ResponseEntity<List<Service>> getAll() {
        return ResponseEntity.ok(serviceRepository.findAll());
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Service> create(
            @RequestParam String nom,
            @RequestParam String description,
            @RequestParam(required = false) String duree,
            @RequestParam MultipartFile photo) {
        String cleanName = nom == null ? "" : nom.trim();
        String cleanDescription = description == null ? "" : description.trim();
        if (cleanName.isBlank() || cleanName.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom est obligatoire (100 caractères maximum).");
        if (cleanDescription.isBlank() || cleanDescription.length() > 500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La description est obligatoire (500 caractères maximum).");
        if (duree != null && duree.length() > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La durée ne doit pas dépasser 50 caractères.");

        String imageUrl = imageStorageService.storeImage("services", photo);
        try {
            Service service = new Service();
            service.setNom(cleanName);
            service.setDescription(cleanDescription);
            service.setDuree(StringUtils.hasText(duree) ? duree.trim() : null);
            service.setImageUrl(imageUrl);
            Service saved = serviceRepository.save(service);
            audit.log("SERVICE_CREE", "Service", saved.getId(), saved.getNom());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (RuntimeException exception) {
            imageStorageService.deleteStoredImage("services", imageUrl);
            throw exception;
        }
    }

    /** Modifie un service ; la photo n'est remplacée que si une nouvelle image est envoyée. */
    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    @Transactional
    public ResponseEntity<Service> update(
            @PathVariable Long id,
            @RequestParam String nom,
            @RequestParam String description,
            @RequestParam(required = false) String duree,
            @RequestParam(required = false) MultipartFile photo) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service introuvable."));
        String cleanName = nom == null ? "" : nom.trim();
        String cleanDescription = description == null ? "" : description.trim();
        if (cleanName.isBlank() || cleanName.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom est obligatoire (100 caractères maximum).");
        if (cleanDescription.isBlank() || cleanDescription.length() > 500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La description est obligatoire (500 caractères maximum).");
        if (duree != null && duree.length() > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La durée ne doit pas dépasser 50 caractères.");

        String ancienneImage = service.getImageUrl();
        String nouvelleImage = photo != null && !photo.isEmpty() ? imageStorageService.storeImage("services", photo) : null;
        service.setNom(cleanName);
        service.setDescription(cleanDescription);
        service.setDuree(StringUtils.hasText(duree) ? duree.trim() : null);
        if (nouvelleImage != null) service.setImageUrl(nouvelleImage);
        Service saved = serviceRepository.saveAndFlush(service);
        if (nouvelleImage != null) imageStorageService.deleteStoredImage("services", ancienneImage);
        audit.log("SERVICE_MODIFIE", "Service", id, saved.getNom());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service introuvable."));
        // Les anciens rendez-vous gardent leur historique mais ne pointent plus vers le service supprimé.
        rendezVousRepository.detachService(id);
        serviceRepository.delete(service);
        serviceRepository.flush();
        audit.log("SERVICE_SUPPRIME", "Service", id, service.getNom());
        imageStorageService.deleteStoredImage("services", service.getImageUrl());
        return ResponseEntity.noContent().build();
    }
}
