package com.cabinet.cabinet.entity;

import com.cabinet.cabinet.util.TextRepairListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Enfant suivi au cabinet (carnet de santé et vaccinations), rattaché au numéro de téléphone de sa mère. */
@Entity
@EntityListeners(TextRepairListener.class)
@Table(name = "enfant", indexes = @Index(name = "idx_enfant_mere", columnList = "mere_telephone"))
@Getter
@Setter
@NoArgsConstructor
public class Enfant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mere_telephone", nullable = false, length = 20)
    private String mereTelephone;

    @Column(nullable = false, length = 100)
    private String nom;

    /** F, M ou vide. */
    @Column(length = 1)
    private String sexe;

    @Column(name = "date_naissance", nullable = false)
    private LocalDate dateNaissance;

    @Column(name = "poids_naissance_g")
    private Integer poidsNaissanceG;

    @Column(name = "grossesse_id")
    private Long grossesseId;

    @Column(length = 1000)
    private String notes;

    @Column(name = "cree_le", nullable = false)
    private LocalDateTime creeLe;
}
