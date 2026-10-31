package com.cabinet.cabinet.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Rétablir le tour de garde automatique sur une période : une personne de garde par jour, à tour de rôle selon le nombre de personnes de l'équipe. */
public record RestaurationGardeRequestDTO(
        @NotNull(message = "Choisissez la date de début") LocalDate debut,
        @NotNull @Min(value = 1, message = "Au moins 1 jour") @Max(value = 93, message = "93 jours au plus") Integer nombreJours) { }
