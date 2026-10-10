package com.cabinet.cabinet.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class RendezVousRequestDTO {

    @NotBlank(message = "Le nom est obligatoire")
    @Size(min = 2, max = 100, message = "Le nom doit contenir entre 2 et 100 caractères")
    private String nomClient;

    @NotNull(message = "L'âge est obligatoire")
    @Min(value = 0, message = "L'âge ne peut pas être négatif")
    @Max(value = 120, message = "L'âge doit être inférieur à 120")
    private Integer ageClient;

    // Les espaces, points, tirets et parenthèses sont tolérés à la saisie : le service les retire avant l'enregistrement.
    @NotBlank(message = "Le téléphone est obligatoire")
    @Pattern(
        regexp = "^[+]?[0-9 .()\\-]{8,25}$",
        message = "Le numéro de téléphone est invalide"
    )
    private String telephoneClient;

    @Size(max = 2000, message = "Le motif ne peut pas dépasser 2000 caractères")
    private String motif;

    private Long serviceId;
    private Long praticienId;

    // La règle « pas de date passée » est appliquée par le service : elle ne doit pas empêcher
    // le personnel de corriger un rendez-vous déjà passé sans changer son créneau.
    @NotNull(message = "La date est obligatoire")
    private LocalDate dateRdv;

    @NotNull(message = "L'heure est obligatoire")
    private LocalTime heureRdv;
}
