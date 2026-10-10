package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.service.MotDePasseOublieService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Routes publiques (/api/auth/**) : demande de lien, puis choix du nouveau mot de passe. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class MotDePasseOublieController {
    private final MotDePasseOublieService service;

    public record Demande(@NotBlank(message = "L'adresse e-mail est obligatoire") @Size(max = 150) String email) { }

    public record Reinitialisation(@NotBlank(message = "Le lien est incomplet") @Size(max = 200) String token,
                                   @NotBlank(message = "Le mot de passe est obligatoire") @Size(max = 200) String motDePasse) { }

    @PostMapping("/mot-de-passe-oublie")
    public ResponseEntity<Map<String, String>> demander(@Valid @RequestBody Demande demande, HttpServletRequest http) {
        service.demander(demande.email(), http.getRemoteAddr());
        // Même réponse que le compte existe ou non.
        return ResponseEntity.accepted().body(Map.of("message",
                "Si un compte correspond à cette adresse, un lien de réinitialisation vient d'être envoyé. Le super administrateur a aussi été prévenu."));
    }

    @PostMapping("/reinitialiser")
    public ResponseEntity<Map<String, String>> reinitialiser(@Valid @RequestBody Reinitialisation corps, HttpServletRequest http) {
        service.reinitialiser(corps.token(), corps.motDePasse(), http.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "Votre mot de passe a été changé. Vous pouvez vous connecter."));
    }
}
