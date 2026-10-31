package com.cabinet.cabinet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Mettre une personne de garde un jour donné. */
public record GardeRequestDTO(
        @NotNull(message = "Choisissez la date de la garde") LocalDate date,
        @NotNull(message = "Choisissez jour ou nuit") @Pattern(regexp = "^(JOUR|NUIT)$", message = "La période doit être JOUR ou NUIT") String periode,
        @NotNull(message = "Choisissez la personne de garde") Long utilisateurId,
        @Size(max = 200, message = "La note est trop longue") String note) { }
