package com.cabinet.cabinet.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Patient reçu au cabinet sans rendez-vous : consultation immédiate avec une personne du personnel libre en ce moment. */
@Data
public class SansRendezVousRequestDTO {

    @NotBlank(message = "Le nom est obligatoire")
    @Size(min = 2, max = 100, message = "Le nom doit contenir entre 2 et 100 caractères")
    private String nomClient;

    @NotNull(message = "L'âge est obligatoire")
    @Min(value = 0, message = "L'âge ne peut pas être négatif")
    @Max(value = 120, message = "L'âge doit être inférieur à 120")
    private Integer ageClient;

    @NotBlank(message = "Le sexe est obligatoire")
    @Pattern(regexp = "^(HOMME|FEMME)$", message = "Le sexe doit être HOMME ou FEMME")
    private String sexe;

    // Vide autorisé : en urgence, le numéro de l'accompagnant sert alors de numéro de contact (vérifié par le service).
    @Pattern(regexp = "^$|^[+]?[0-9 .()\\-]{8,25}$", message = "Le numéro de téléphone est invalide")
    private String telephoneClient;

    @Size(max = 2000, message = "Le motif ne peut pas dépasser 2000 caractères")
    private String motif;

    private Long serviceId;

    /** Tous les services demandés (le premier devient le service principal du rendez-vous) ; remplace « serviceId » quand il est renseigné. */
    @jakarta.validation.constraints.Size(max = 10, message = "10 services au plus")
    private java.util.List<Long> serviceIds;

    /** Patient en urgence : l'équipe est prévenue comme telle, et un accompagnant peut remplir le formulaire à sa place. */
    private boolean urgence;

    /** Personne qui accompagne le patient (facultatif). */
    @Size(max = 100, message = "Le nom de l'accompagnant est trop long")
    private String accompagnantNom;

    @Pattern(regexp = "^$|^[+]?[0-9 .()\\-]{8,25}$", message = "Le numéro de l'accompagnant est invalide")
    private String accompagnantTelephone;

    @Size(max = 50, message = "Le lien avec le patient est trop long")
    private String accompagnantLien;

    /** Sexe de l'accompagnant (facultatif) : sa civilité « Mr » / « Mme » en découle. */
    @Pattern(regexp = "^(HOMME|FEMME)?$", message = "Le sexe de l'accompagnant doit être HOMME ou FEMME")
    private String accompagnantSexe;

    /** La personne du personnel qui reçoit le patient : doit être libre à ce moment. */
    @NotNull(message = "Choisissez la personne qui reçoit le patient")
    private Long praticienId;
}
