package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.dto.ProfilResponseDTO;
import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import com.cabinet.cabinet.service.AuditService;
import com.cabinet.cabinet.service.ImageStorageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfilController {
    private final UtilisateurRepository users;
    private final ImageStorageService imageStorageService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final com.cabinet.cabinet.security.PermissionService permissions;

    public record ChangementMotDePasse(@NotBlank(message = "Le mot de passe actuel est obligatoire") String ancienMotDePasse,
                                       @NotBlank(message = "Le nouveau mot de passe est obligatoire")
                                       @Size(min = 10, max = 72, message = "Le nouveau mot de passe doit contenir entre 10 et 72 caractères") String nouveauMotDePasse) { }

    @GetMapping
    public ProfilResponseDTO getProfile(Authentication authentication) {
        return toResponse(findUser(authentication));
    }

    @PutMapping(value = "/photo", consumes = "multipart/form-data")
    @Transactional
    public ProfilResponseDTO updatePhoto(Authentication authentication, @RequestParam MultipartFile photo) {
        Utilisateur user = findUser(authentication);
        String newPhoto = imageStorageService.storeImage("profiles", photo);
        String oldPhoto = user.getPhotoUrl();
        user.setPhotoUrl(newPhoto);
        Utilisateur saved = users.save(user);
        imageStorageService.deleteStoredImage("profiles", oldPhoto);
        return toResponse(saved);
    }

    /** Chaque membre du personnel peut changer son propre mot de passe (l'ancien est exigé). */
    @PutMapping("/password")
    @Transactional
    public ResponseEntity<Void> changePassword(Authentication authentication, @Valid @RequestBody ChangementMotDePasse request) {
        Utilisateur user = findUser(authentication);
        if (!passwordEncoder.matches(request.ancienMotDePasse(), user.getMotDePasse())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le mot de passe actuel est incorrect.");
        }
        user.setMotDePasse(passwordEncoder.encode(request.nouveauMotDePasse()));
        users.save(user);
        audit.log("MOT_DE_PASSE_CHANGE", "Utilisateur", user.getId(), user.getEmail());
        return ResponseEntity.noContent().build();
    }

    private Utilisateur findUser(Authentication authentication) {
        return users.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable."));
    }

    private ProfilResponseDTO toResponse(Utilisateur user) {
        return new ProfilResponseDTO(user.getId(), user.getNom(), user.getEmail(), user.getRole(), user.getPhotoUrl(), permissions.de(user));
    }
}
