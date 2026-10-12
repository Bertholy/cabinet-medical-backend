package com.cabinet.cabinet.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Toutes les erreurs de l'API sont renvoyées sous la forme { timestamp, status, error, message }.
 * La classe étend ResponseEntityExceptionHandler afin que les erreurs standard de Spring MVC
 * (404, 405, 400 sur JSON illisible, 413, ...) gardent leur vrai code HTTP au lieu de devenir des 500.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(RendezVousIntrouvableException.class)
    public ResponseEntity<Map<String, Object>> handleRendezVousIntrouvable(RendezVousIntrouvableException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "Rendez-vous introuvable", ex.getMessage());
    }

    @ExceptionHandler(CreneauIndisponibleException.class)
    public ResponseEntity<Map<String, Object>> handleCreneauIndisponible(CreneauIndisponibleException ex) {
        return buildResponse(HttpStatus.CONFLICT, "Créneau indisponible", ex.getMessage());
    }

    /** 422 (et non 409, réservé aux créneaux pris) : le site affiche un message précis avec la date à partir de laquelle on peut réserver à nouveau. */
    @ExceptionHandler(ReservationRecenteException.class)
    public ResponseEntity<Map<String, Object>> handleReservationRecente(ReservationRecenteException ex) {
        ResponseEntity<Map<String, Object>> base = buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "Réservation déjà faite", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>(base.getBody());
        body.put("code", "RESERVATION_RECENTE");
        body.put("disponibleLe", ex.getDisponibleLe().toString());
        body.put("dateRdvExistant", ex.getDateRdvExistant().toString());
        body.put("heureRdvExistant", ex.getHeureRdvExistant().toString());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(StatutInvalideException.class)
    public ResponseEntity<Map<String, Object>> handleStatutInvalide(StatutInvalideException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Statut invalide", ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Authentification échouée", "Email ou mot de passe incorrect");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Authentification échouée", "Email ou mot de passe incorrect");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "Accès refusé", ex.getMessage());
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<Map<String, Object>> handleDateInvalide(DateTimeParseException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Date invalide", "Utilisez le format AAAA-MM-JJ.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Paramètre invalide", "La valeur du paramètre « " + ex.getName() + " » est invalide.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Erreur de validation", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violation d'intégrité des données : {}", ex.getMostSpecificCause().getMessage());
        return buildResponse(HttpStatus.CONFLICT, "Conflit de données",
                "L'opération est impossible car elle entre en conflit avec des données existantes.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> erreursChamps = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                erreursChamps.put(error.getField(), error.getDefaultMessage()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Erreur de validation");
        body.put("message", erreursChamps.values().stream().findFirst().orElse("Données invalides"));
        body.put("erreurs", erreursChamps);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** Uniformise le corps de toutes les erreurs gérées par ResponseEntityExceptionHandler. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", LocalDateTime.now());
        payload.put("status", statusCode.value());
        payload.put("error", status != null ? status.getReasonPhrase() : "Erreur");
        payload.put("message", messagePourClient(ex, statusCode));
        return ResponseEntity.status(statusCode).headers(headers).body(payload);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Erreur interne non prévue", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne",
                "Une erreur inattendue s'est produite. Réessayez plus tard.");
    }

    private String messagePourClient(Exception ex, HttpStatusCode status) {
        if (status.is5xxServerError()) return "Une erreur inattendue s'est produite. Réessayez plus tard.";
        if (ex instanceof org.springframework.web.server.ResponseStatusException rse && rse.getReason() != null) return rse.getReason();
        if (ex instanceof org.springframework.http.converter.HttpMessageNotReadableException) return "Le corps de la requête est illisible ou invalide.";
        if (ex instanceof org.springframework.web.servlet.resource.NoResourceFoundException) return "Ressource introuvable.";
        if (ex instanceof org.springframework.web.HttpRequestMethodNotSupportedException) return "Méthode HTTP non autorisée sur cette ressource.";
        if (ex instanceof org.springframework.web.multipart.MaxUploadSizeExceededException) return "Le fichier est trop volumineux (5 Mo maximum).";
        return "Requête invalide.";
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String erreur, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", erreur);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
