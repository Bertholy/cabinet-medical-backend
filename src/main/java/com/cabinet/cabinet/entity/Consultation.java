package com.cabinet.cabinet.entity;

import com.cabinet.cabinet.util.TextRepairListener;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Compte rendu d'une consultation. Il référence le rendez-vous par son numéro sans clé étrangère :
 * un dossier médical doit survivre à la suppression définitive d'un rendez-vous.
 */
@Entity
@EntityListeners(TextRepairListener.class)
@Table(name = "consultation", indexes = @Index(name = "idx_consultation_telephone", columnList = "telephone"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Consultation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String telephone;

    @Column(name = "rendez_vous_id")
    private Long rendezVousId;

    @Column(name = "date_consultation", nullable = false)
    private LocalDate dateConsultation;

    @Column(length = 4000)
    private String notes;

    @Column(length = 500)
    private String diagnostic;

    /** Texte de l'ordonnance (une ligne par médicament). */
    @Column(length = 4000)
    private String ordonnance;

    @Column(name = "auteur_nom", length = 100)
    private String auteurNom;

    @Column(name = "auteur_email", length = 150)
    private String auteurEmail;

    @Column(name = "cree_le", nullable = false)
    private LocalDateTime creeLe;

    @Column(name = "modifie_le")
    private LocalDateTime modifieLe;
}
