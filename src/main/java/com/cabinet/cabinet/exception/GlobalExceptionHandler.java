package com.cabinet.cabinet.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ============ RDV INTROUVABLE (404) ============
    @ExceptionHandler(RendezVousIntrouvableException.class)
    public ResponseEntity<Map<String, Object>> handleRendezVousIntrouvable(
            RendezVousIntrouvableException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "Rendez-vous introuvable", ex.getMessage());
    }

    // ============ CRÉNEAU INDISPONIBLE (409) ============
    @ExceptionHandler(CreneauIndisponibleException.class)
    public ResponseEntity<Map<String, Object>> handleCreneauIndisponible(
            CreneauIndisponibleException ex) {
        return buildResponse(HttpStatus.CONFLICT, "Créneau indisponible", ex.getMessage());
    }

    // ============ STATUT INVALIDE (400) ============
    @ExceptionHandler(StatutInvalideException.class)
    public ResponseEntity<Map<String, Object>> handleStatutInvalide(
            StatutInvalideException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Statut invalide", ex.getMessage());
    }

    // ============ AUTHENTIFICATION ÉCHOUÉE (401) ============
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Authentification échouée",
                "Email ou mot de passe incorrect");
    }

    // ============ ERREURS DE VALIDATION (400) ============
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> erreursChamps = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            erreursChamps.put(error.getField(), error.getDefaultMessage())
        );

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Erreur de validation");
        body.put("erreurs", erreursChamps);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // ============ ERREUR GÉNÉRIQUE (500) ============
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne", ex.getMessage());
    }

    // ============ MÉTHODE UTILITAIRE ============
    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status, String erreur, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", erreur);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);
    }
}