package com.cabinet.cabinet.entity;

import com.cabinet.cabinet.util.TextRepairListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Consultation prénatale (CPN) : mesures du jour et actes réalisés. */
@Entity
@EntityListeners(TextRepairListener.class)
@Table(name = "consultation_prenatale", indexes = @Index(name = "idx_cpn_grossesse", columnList = "grossesse_id, date_consultation"))
@Getter
@Setter
@NoArgsConstructor
public class ConsultationPrenatale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grossesse_id", nullable = false)
    private Grossesse grossesse;

    @Column(name = "date_consultation", nullable = false)
    private LocalDate dateConsultation;

    /** Semaines d'aménorrhée révolues le jour de la consultation (calculées à l'enregistrement). */
    @Column(name = "terme_sa", nullable = false)
    private int termeSa;

    @Column(name = "poids_kg", precision = 5, scale = 1)
    private BigDecimal poidsKg;

    @Column(name = "tension_systolique")
    private Integer tensionSystolique;

    @Column(name = "tension_diastolique")
    private Integer tensionDiastolique;

    @Column(name = "hauteur_uterine_cm")
    private Integer hauteurUterineCm;

    /** Bruits du cœur fœtal (battements par minute). */
    private Integer bcf;

    private boolean oedemes;

    /** Albuminurie (bandelette) : NEG, +, ++, +++. */
    @Column(length = 10)
    private String albuminurie;

    @Column(length = 40)
    private String presentation;

    /** Actes du jour, séparés par des virgules : VAT, SP, FER, MILDA, DEPISTAGE… */
    @Column(length = 300)
    private String actes;

    @Column(length = 2000)
    private String observations;

    @Column(name = "prochain_rdv")
    private LocalDate prochainRdv;

    @Column(name = "auteur_nom", length = 150)
    private String auteurNom;

    @Column(name = "cree_le", nullable = false)
    private LocalDateTime creeLe;
}
