package com.cabinet.cabinet.dto;

import com.cabinet.cabinet.enums.StatutRDV;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class RendezVousResponseDTO {

    private Long id;
    private String nomClient;
    private Integer ageClient;
    private String telephoneClient;
    private LocalDate dateRdv;
    private LocalTime heureRdv;
    private StatutRDV statut;
    private LocalDateTime dateCreation;
}