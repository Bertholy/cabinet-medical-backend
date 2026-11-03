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
    /** « HOMME », « FEMME » ou null. */
    private String sexe;
    /** Civilité calculée d'après l'âge et le sexe : « Enfant », « Mr », « Mme » ou null. */
    private String civilite;
    private String telephoneClient;
    private String motif;
    private Long serviceId;
    private String serviceNom;
    /** Tous les services du rendez-vous : le principal d'abord, puis les autres. */
    private java.util.List<String> servicesNoms;
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
    /** Patient reçu sans rendez-vous (consultation immédiate). */
    private boolean sansRendezVous;
    /** Patient reçu en urgence. */
    private boolean urgence;
    /** Personne qui accompagne le patient (facultatif). */
    private String accompagnantNom;
    private String accompagnantTelephone;
    private String accompagnantLien;
    private String accompagnantSexe;
    /** « Mr » ou « Mme » d'après le sexe de l'accompagnant (adulte), vide si inconnu. */
    private String accompagnantCivilite;
    /** Premier passage de ce client au cabinet (aucun rendez-vous non annulé avant celui-ci) ; null quand l'historique n'est pas calculé. */
    private Boolean nouveauClient;
    /** Rang de ce passage pour ce client : 1 = premier passage, 2 = deuxième… ; null quand l'historique n'est pas calculé. */
    private Integer passage;
    /** Nombre total de passages (rendez-vous non annulés) de ce client ; null quand l'historique n'est pas calculé. */
    private Integer passagesTotal;
}
