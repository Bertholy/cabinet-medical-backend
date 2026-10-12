package com.cabinet.cabinet.exception;

import java.time.LocalDateTime;

/** Un même nom ne peut pas faire deux réservations rapprochées : la nouvelle est refusée jusqu'à {@code disponibleLe}. */
public class ReservationRecenteException extends RuntimeException {
    private final LocalDateTime disponibleLe;
    private final java.time.LocalDate dateRdvExistant;
    private final java.time.LocalTime heureRdvExistant;

    public ReservationRecenteException(String message, LocalDateTime disponibleLe, java.time.LocalDate dateRdvExistant, java.time.LocalTime heureRdvExistant) {
        super(message);
        this.disponibleLe = disponibleLe;
        this.dateRdvExistant = dateRdvExistant;
        this.heureRdvExistant = heureRdvExistant;
    }

    public LocalDateTime getDisponibleLe() { return disponibleLe; }
    public java.time.LocalDate getDateRdvExistant() { return dateRdvExistant; }
    public java.time.LocalTime getHeureRdvExistant() { return heureRdvExistant; }
}
