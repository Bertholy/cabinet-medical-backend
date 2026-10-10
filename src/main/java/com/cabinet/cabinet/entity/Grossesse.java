package com.cabinet.cabinet.entity;

import com.cabinet.cabinet.util.TextRepairListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Suivi d'une grossesse. Le patient est identifié par son numéro de téléphone canonique, comme le dossier patient. */
@Entity
@EntityListeners(TextRepairListener.class)
@Table(name = "grossesse", indexes = @Index(name = "idx_grossesse_telephone", columnList = "telephone, statut"))
@Getter
@Setter
@NoArgsConstructor
public class Grossesse {
    public static final String EN_COURS = "EN_COURS";
    public static final String TERMINEE = "TERMINEE";
    public static final String INTERROMPUE = "INTERROMPUE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String telephone;

    /** Date des dernières règles (facultative). */
    private LocalDate ddr;

    /** Date prévue d'accouchement : DDR + 280 jours, ou date donnée par l'échographie. */
    @Column(nullable = false)
    private LocalDate dpa;

    @Column(name = "groupe_sanguin", length = 10)
    private String groupeSanguin;

    /** Facteurs de risque connus (césarienne précédente, hypertension, diabète, grossesse gémellaire…). */
    @Column(length = 1000)
    private String risques;

    @Column(length = 2000)
    private String notes;

    /** EN_COURS, TERMINEE (accouchement) ou INTERROMPUE (fausse couche, interruption). */
    @Column(nullable = false, length = 20)
    private String statut = EN_COURS;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(length = 300)
    private String issue;

    @Column(name = "cree_le", nullable = false)
    private LocalDateTime creeLe;

    @Column(name = "cree_par", length = 150)
    private String creePar;

    /** Premier jour de la grossesse pour le calcul des semaines d'aménorrhée. */
    public LocalDate debutCalcul() {
        return ddr != null ? ddr : dpa.minusDays(280);
    }
}
