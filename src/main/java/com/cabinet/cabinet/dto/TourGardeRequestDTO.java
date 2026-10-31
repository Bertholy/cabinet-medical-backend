package com.cabinet.cabinet.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.List;

/**
 * Générer un tour de garde : les personnes se relaient à tour de rôle, une par jour, dans l'ordre de la liste, à partir de la date de début.
 * « periode » : JOUR_NUIT (la personne est de garde jour et nuit), JOUR ou NUIT.
 * « remplacer » efface d'abord les gardes de cette période déjà enregistrées sur les mêmes jours.
 */
public record TourGardeRequestDTO(
        @NotNull(message = "Choisissez la date de début") LocalDate debut,
        @NotNull @Min(value = 1, message = "Au moins 1 jour") @Max(value = 93, message = "93 jours au plus") Integer nombreJours,
        @NotNull(message = "Choisissez la période de garde") @Pattern(regexp = "^(JOUR_NUIT|JOUR|NUIT)$", message = "La période doit être JOUR_NUIT, JOUR ou NUIT") String periode,
        @NotEmpty(message = "Choisissez au moins une personne") List<Long> utilisateurIds,
        Boolean remplacer) { }
