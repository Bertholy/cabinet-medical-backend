package com.cabinet.cabinet.entity;

import com.cabinet.cabinet.util.TextRepairListener;
import com.cabinet.cabinet.enums.StatutRDV;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

@Entity
@EntityListeners(TextRepairListener.class)
@Table(name = "rendez_vous")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RendezVous {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom_client", nullable = false, length = 100)
    private String nomClient;

    @Column(name = "age_client", nullable = false)
    private Integer ageClient;

    @Column(name = "telephone_client", nullable = false, length = 20)
    private String telephoneClient;

    @Column(name = "motif", length = 2000)
    private String motif;

    @Column(name = "date_rdv", nullable = false)
    private LocalDate dateRdv;

    @Column(name = "heure_rdv", nullable = false)
    private LocalTime heureRdv;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Service service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "praticien_id")
    private Utilisateur praticien;

    @Column(name = "client_access_token_hash", length = 64)
    private String clientAccessTokenHash;

    /** Clé de suivi unique (« CF-7K3M-9QX2 ») remise au patient à la prise de rendez-vous : elle sert à retrouver, annuler et télécharger le reçu. */
    @Column(name = "cle_suivi", length = 20, unique = true)
    private String cleSuivi;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutRDV statut;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    // Keep existing rendez-vous valid when Hibernate adds this column to an existing table.
    @Column(name = "supprime", columnDefinition = "boolean default false")
    private Boolean supprime = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by")
    private Utilisateur deletedBy;

    /** Moment où la personne attribuée a accepté le rendez-vous (null tant qu'elle ne l'a pas fait). Remis à zéro si on change de personne. */
    @Column(name = "personnel_confirme_le")
    private LocalDateTime personnelConfirmeLe;

    @Column(name = "rappel_envoye", columnDefinition = "boolean default false")
    private Boolean rappelEnvoye = false;

    @PrePersist
    public void prePersist() {
        dateCreation = LocalDateTime.now(ZoneId.of("Indian/Antananarivo"));
        if (statut == null) statut = StatutRDV.EN_ATTENTE;
        if (supprime == null) supprime = false;
    }
}
