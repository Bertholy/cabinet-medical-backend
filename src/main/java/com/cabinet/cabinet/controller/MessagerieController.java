package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import com.cabinet.cabinet.service.MessagerieService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Messagerie interne du personnel : chacun ne voit que ses propres conversations. */
@RestController
@RequestMapping("/api/messagerie")
@RequiredArgsConstructor
public class MessagerieController {
    private final MessagerieService messagerie;
    private final UtilisateurRepository utilisateurs;

    public record Envoi(@NotBlank(message = "Écrivez un message avant d'envoyer.") @Size(max = 4000) String contenu) { }

    @GetMapping("/contacts")
    public Map<String, Object> contacts(Authentication authentication) {
        return messagerie.contacts(moi(authentication));
    }

    @GetMapping("/non-lus")
    public Map<String, Long> nonLus(Authentication authentication) {
        return Map.of("nonLus", messagerie.nonLus(moi(authentication)));
    }

    @GetMapping("/conversation/{id}")
    public MessagerieService.Conversation conversation(@PathVariable Long id, Authentication authentication) {
        return messagerie.conversation(moi(authentication), id);
    }

    @PostMapping("/conversation/{id}")
    public ResponseEntity<MessagerieService.MessageDto> envoyer(@PathVariable Long id, @Valid @RequestBody Envoi corps, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(messagerie.envoyer(moi(authentication), id, corps.contenu()));
    }

    private Utilisateur moi(Authentication authentication) {
        return utilisateurs.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non authentifié."));
    }
}
