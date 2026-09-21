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
    @Min(value = 1, message = "L'âge doit être supérieur à 0")
    @Max(value = 120, message = "L'âge doit être inférieur à 120")
    private Integer ageClient;

    @NotBlank(message = "Le téléphone est obligatoire")
    @Pattern(
        regexp = "^[+]?[0-9]{8,20}$",
        message = "Le numéro de téléphone est invalide"
    )
    private String telephoneClient;

    @NotNull(message = "La date est obligatoire")
    @FutureOrPresent(message = "La date ne peut pas être dans le passé")
    private LocalDate dateRdv;

    @NotNull(message = "L'heure est obligatoire")
    private LocalTime heureRdv;
}