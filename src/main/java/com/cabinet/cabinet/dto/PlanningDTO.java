package com.cabinet.cabinet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record PlanningDTO(
        @Min(value = 15, message = "La durée d'un créneau doit être d'au moins 15 minutes") @Max(value = 120, message = "La durée d'un créneau ne peut pas dépasser 120 minutes") int dureeCreneauMinutes,
        @Min(value = 1, message = "Au moins 1 rendez-vous par créneau") @Max(value = 20, message = "20 rendez-vous maximum par créneau") int capaciteParCreneau,
        @NotEmpty(message = "Les horaires des 7 jours sont obligatoires") @Valid List<HoraireDTO> horaires) {

    public record HoraireDTO(@NotNull @Min(1) @Max(7) Integer jourSemaine, boolean ouvert,
                             LocalTime matinDebut, LocalTime matinFin,
                             LocalTime apresMidiDebut, LocalTime apresMidiFin) { }

    public record JourFermeDTO(Long id, @NotNull(message = "La date est obligatoire") LocalDate date, String motif) { }
}
