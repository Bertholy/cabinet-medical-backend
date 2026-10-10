package com.cabinet.cabinet.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
public class RendezVousCorbeilleDTO {
    private Long id;
    private String nomClient;
    private String telephoneClient;
    private LocalDate dateRdv;
    private LocalTime heureRdv;
    private LocalDateTime deletedAt;

    public RendezVousCorbeilleDTO(Long id, String nomClient, String telephoneClient,
            LocalDate dateRdv, LocalTime heureRdv, LocalDateTime deletedAt) {
        this.id = id;
        this.nomClient = nomClient;
        this.telephoneClient = telephoneClient;
        this.dateRdv = dateRdv;
        this.heureRdv = heureRdv;
        this.deletedAt = deletedAt;
    }
}
