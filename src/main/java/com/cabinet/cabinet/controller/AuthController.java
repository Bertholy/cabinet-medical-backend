package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.dto.LoginRequestDTO;
import com.cabinet.cabinet.dto.LoginResponseDTO;
import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import com.cabinet.cabinet.security.JwtService;
import com.cabinet.cabinet.security.LoginAttemptService;
import com.cabinet.cabinet.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final UtilisateurRepository utilisateurRepository;
    private final LoginAttemptService tentatives;
    private final AuditService audit;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request, HttpServletRequest http) {
        String email = request.getEmail().trim();
        String adresse = http.getRemoteAddr();

        if (tentatives.estBloque(email, adresse)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de tentatives de connexion. Réessayez dans " + tentatives.minutesRestantes(email, adresse) + " minute(s).");
        }

        // 1. Authentifier l'utilisateur (vérifie email + mot de passe, sans tenir compte de la casse de l'e-mail)
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.getMotDePasse()));
        } catch (AuthenticationException e) {
            tentatives.echec(email, adresse);
            audit.logAs(email.toLowerCase(), "PUBLIC", "CONNEXION_ECHEC", "Utilisateur", null, "Adresse " + adresse);
            throw new BadCredentialsException("Identifiants invalides", e);
        }
        tentatives.succes(email, adresse);

        // 2. Charger l'utilisateur
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        // 3. Charger l'entité complète (pour récupérer le rôle)
        Utilisateur utilisateur = utilisateurRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Utilisateur introuvable"));

        // 4. Générer le token JWT
        String token = jwtService.genererToken(userDetails);
        audit.logAs(utilisateur.getEmail(), utilisateur.getRole().name(), "CONNEXION", "Utilisateur", utilisateur.getId(), "Adresse " + adresse);

        // 5. Construire la réponse
        LoginResponseDTO response = new LoginResponseDTO(
                token,
                utilisateur.getEmail(),
                utilisateur.getRole().name(),
                "Connexion réussie"
        );

        return ResponseEntity.ok(response);
    }
}
