package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.dto.UtilisateurCreateRequestDTO;
import com.cabinet.cabinet.dto.UtilisateurResponseDTO;
import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.enums.Role;
import com.cabinet.cabinet.repository.RendezVousRepository;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import com.cabinet.cabinet.service.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUtilisateurController {
    private final UtilisateurRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RendezVousRepository rendezVousRepository;
    private final AuditService audit;
    private final com.cabinet.cabinet.repository.NotificationRepository notificationRepository;
    private final com.cabinet.cabinet.repository.ReinitialisationMotDePasseRepository reinitialisations;
    private final com.cabinet.cabinet.repository.MessageRepository messageRepository;
    private final com.cabinet.cabinet.repository.PermissionUtilisateurRepository exceptionsDroits;
    private final com.cabinet.cabinet.security.PermissionService permissions;

    public record ModificationCompte(@NotBlank(message = "Le nom est obligatoire") @Size(max = 100) String nom,
                                     @NotBlank(message = "L'adresse e-mail est obligatoire") @Email(message = "Adresse e-mail invalide") @Size(max = 150) String email,
                                     @Size(max = 20, message = "Le téléphone ne peut pas dépasser 20 caractères") String telephone) { }

    public record NouveauMotDePasse(@NotBlank(message = "Le mot de passe est obligatoire")
                                    @Size(min = 10, max = 72, message = "Le mot de passe doit contenir entre 10 et 72 caractères") String motDePasse) { }

    @GetMapping
    public List<UtilisateurResponseDTO> list() {
        return users.findAll().stream().map(this::toResponse).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<UtilisateurResponseDTO> create(@Valid @RequestBody UtilisateurCreateRequestDTO request) {
        String email = request.getEmail().trim().toLowerCase();
        if (users.findByEmailIgnoreCase(email).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette adresse e-mail est déjà utilisée.");
        Utilisateur user = new Utilisateur();
        user.setNom(request.getNom().trim());
        user.setEmail(email);
        user.setTelephone(vide(request.getTelephone()));
        user.setRole(request.getRole());
        user.setMotDePasse(passwordEncoder.encode(request.getMotDePasse()));
        Utilisateur saved = users.save(user);
        audit.log("COMPTE_CREE", "Utilisateur", saved.getId(), saved.getEmail() + " (" + saved.getRole() + ")");
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    /** Modification du nom, de l'e-mail et du téléphone d'un compte. */
    @PutMapping("/{id}")
    @Transactional
    public UtilisateurResponseDTO update(@PathVariable Long id, @Valid @RequestBody ModificationCompte request) {
        Utilisateur user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable."));
        String email = request.email().trim().toLowerCase();
        users.findByEmailIgnoreCase(email).filter(autre -> !autre.getId().equals(id)).ifPresent(autre -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette adresse e-mail est déjà utilisée.");
        });
        permissions.invalider();
        boolean emailChange = !email.equalsIgnoreCase(user.getEmail());
        user.setNom(request.nom().trim());
        user.setEmail(email);
        user.setTelephone(vide(request.telephone()));
        Utilisateur saved = users.save(user);
        audit.log("COMPTE_MODIFIE", "Utilisateur", id, saved.getEmail() + (emailChange ? " (e-mail modifié)" : ""));
        return toResponse(saved);
    }

    /** Réinitialisation du mot de passe d'un collègue qui l'a oublié. */
    @PutMapping("/{id}/password")
    @Transactional
    public ResponseEntity<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody NouveauMotDePasse request) {
        Utilisateur user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable."));
        user.setMotDePasse(passwordEncoder.encode(request.motDePasse()));
        users.save(user);
        audit.log("MOT_DE_PASSE_REINITIALISE", "Utilisateur", id, user.getEmail());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/role")
    @Transactional
    public UtilisateurResponseDTO changeRole(@PathVariable Long id, @RequestBody Map<String, String> request) {
        Role role;
        try { role = Role.valueOf(request.getOrDefault("role", "")); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rôle invalide."); }
        Utilisateur user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable."));
        if (user.getRole() == Role.SUPER_ADMIN && role != Role.SUPER_ADMIN
                && users.countByRole(Role.SUPER_ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Il faut conserver au moins un super administrateur.");
        }
        Role ancien = user.getRole();
        permissions.invalider();
        user.setRole(role);
        Utilisateur saved = users.save(user);
        audit.log("ROLE_MODIFIE", "Utilisateur", id, saved.getEmail() + " : " + ancien + " → " + role);
        return toResponse(saved);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        Utilisateur user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable."));
        if (user.getEmail().equalsIgnoreCase(authentication.getName())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Vous ne pouvez pas supprimer le compte actuellement connecté.");
        if (user.getRole() == Role.SUPER_ADMIN && users.countByRole(Role.SUPER_ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Il faut conserver au moins un super administrateur.");
        }
        // Les rendez-vous gardent leur historique mais ne référencent plus ce compte (sinon la clé étrangère bloque la suppression).
        rendezVousRepository.detachPraticien(id);
        rendezVousRepository.detachDeletedBy(id);
        notificationRepository.supprimerDuDestinataire(id);
        reinitialisations.supprimerDe(id);
        messageRepository.supprimerDe(id);
        exceptionsDroits.supprimerDe(id);
        users.delete(user);
        permissions.invalider();
        audit.log("COMPTE_SUPPRIME", "Utilisateur", id, user.getEmail());
        return ResponseEntity.noContent().build();
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private UtilisateurResponseDTO toResponse(Utilisateur user) {
        return new UtilisateurResponseDTO(user.getId(), user.getNom(), user.getEmail(), user.getTelephone(), user.getRole(), user.getPhotoUrl());
    }
}
