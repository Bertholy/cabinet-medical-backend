package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.entity.Service;
import com.cabinet.cabinet.repository.RendezVousRepository;
import com.cabinet.cabinet.repository.ServiceRepository;
import com.cabinet.cabinet.service.AuditService;
import com.cabinet.cabinet.service.ImageStorageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceRepository serviceRepository;
    private final ImageStorageService imageStorageService;
    private final RendezVousRepository rendezVousRepository;
    private final AuditService audit;

    /** Ajout rapide d'un service avec son tarif (page « Services et tarifs ») : sans photo ni description longue, qu'on peut compléter ensuite dans « Gestion des services ». */
    public record NouveauTarifDTO(
            @NotBlank(message = "Le nom est obligatoire") @Size(max = 100, message = "Le nom est trop long (100 caractères maximum)") String nom,
            @Size(max = 500, message = "La description est trop longue (500 caractères maximum)") String description,
            @Size(max = 50, message = "La durée est trop longue (50 caractères maximum)") String duree,
            @Min(value = 0, message = "Le prix ne peut pas être négatif") @Max(value = 100_000_000, message = "Le prix est trop élevé") Integer prix) { }

    /** Changement du seul tarif d'un service (null = pas de tarif). */
    public record PrixDTO(
            @Min(value = 0, message = "Le prix ne peut pas être négatif") @Max(value = 100_000_000, message = "Le prix est trop élevé") Integer prix) { }

    private static Map<String, Object> versTarif(Service s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("nom", s.getNom());
        m.put("description", s.getDescription());
        m.put("duree", s.getDuree());
        m.put("prix", s.getPrix());
        return m;
    }

    @PostMapping("/tarifs")
    public ResponseEntity<Map<String, Object>> ajouterAvecTarif(@Valid @RequestBody NouveauTarifDTO dto) {
        Service service = new Service();
        service.setNom(dto.nom().trim());
        String description = dto.description() == null ? "" : dto.description().trim();
        service.setDescription(description.isEmpty() ? "Prestation du cabinet." : description);
        service.setDuree(dureeOuNull(dto.duree()));
        service.setPrix(dto.prix());
        Service saved = serviceRepository.save(service);
        audit.log("SERVICE_CREE", "Service", saved.getId(), saved.getNom() + (saved.getPrix() == null ? "" : " — " + saved.getPrix() + " Ar"));
        return ResponseEntity.status(HttpStatus.CREATED).body(versTarif(saved));
    }

    @PutMapping("/{id}/tarif")
    @Transactional
    public Map<String, Object> changerLeTarif(@PathVariable Long id, @Valid @RequestBody PrixDTO dto) {
        Service service = serviceRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service introuvable."));
        service.setPrix(dto.prix());
        Service saved = serviceRepository.save(service);
        audit.log("SERVICE_MODIFIE", "Service", id, saved.getNom() + " — tarif " + (saved.getPrix() == null ? "supprimé" : saved.getPrix() + " Ar"));
        return versTarif(saved);
    }

    // Lister tous les services (public)
    @GetMapping
    public ResponseEntity<List<Service>> getAll() {
        return ResponseEntity.ok(serviceRepository.findAll());
    }

    /** Services avec leur tarif (en Ariary), pour le personnel : secrétariat, soignants et gestion des services (règle dans SecurityConfig). */
    @GetMapping("/tarifs")
    public List<Map<String, Object>> tarifs() {
        return serviceRepository.findAll().stream()
                .sorted(Comparator.comparing(s -> s.getNom().toLowerCase(java.util.Locale.ROOT)))
                .map(ServiceController::versTarif).toList();
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Service> create(
            @RequestParam String nom,
            @RequestParam String description,
            @RequestParam(required = false) String duree,
            @RequestParam(required = false) String prix,
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
            service.setDuree(dureeOuNull(duree));
            service.setPrix(prixOuNull(prix));
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
            @RequestParam(required = false) String prix,
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
        service.setDuree(dureeOuNull(duree));
        if (prix != null) service.setPrix(prixOuNull(prix));   // absent : le tarif reste ; vide : il est effacé
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

    /** Tarif en Ariary : un entier de 0 à 100 000 000, ou vide (pas de tarif). */
    private static Integer prixOuNull(String prix) {
        String texte = prix == null ? "" : prix.replaceAll("[\\s\u00a0\u202f]", "");
        if (texte.isEmpty()) return null;
        try {
            int valeur = Integer.parseInt(texte);
            if (valeur < 0 || valeur > 100_000_000) throw new NumberFormatException();
            return valeur;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le prix doit être un nombre entier d'Ariary (0 à 100 000 000).");
        }
    }

    private static String dureeOuNull(String duree) {
        String texte = duree == null ? "" : duree.trim();
        return texte.isEmpty() ? null : texte;
    }
}
