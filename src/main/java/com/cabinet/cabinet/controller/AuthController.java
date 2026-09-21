package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.dto.LoginRequestDTO;
import com.cabinet.cabinet.dto.LoginResponseDTO;
import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import com.cabinet.cabinet.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final UtilisateurRepository utilisateurRepository;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {

        // 1. Authentifier l'utilisateur (vérifie email + mot de passe)
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getMotDePasse()
                )
        );

        // 2. Charger l'utilisateur
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());

        // 3. Charger l'entité complète (pour récupérer le rôle)
        Utilisateur utilisateur = utilisateurRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        // 4. Générer le token JWT
        String token = jwtService.genererToken(userDetails);

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