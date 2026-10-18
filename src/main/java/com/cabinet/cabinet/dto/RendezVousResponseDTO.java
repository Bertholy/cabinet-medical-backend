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
    private String motif;
    private Long serviceId;
    private String serviceNom;
    private Long praticienId;
    private String praticienNom;
    /** La personne attribuée a accepté le rendez-vous. */
    private boolean personnelConfirme;
    private LocalDateTime personnelConfirmeLe;
    private String clientAccessToken;
    /** Clé de suivi du rendez-vous (voir RendezVous.cleSuivi). */
    private String cleSuivi;
    private LocalDate dateRdv;
    private LocalTime heureRdv;
    private StatutRDV statut;
    private LocalDateTime dateCreation;
    private LocalDateTime deletedAt;
}
