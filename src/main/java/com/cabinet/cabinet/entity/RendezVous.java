package com.cabinet.cabinet.entity;

import com.cabinet.cabinet.enums.StatutRDV;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(
    name = "rendez_vous",
    uniqueConstraints = @UniqueConstraint(columnNames = {"date_rdv", "heure_rdv"})
)
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

    @Column(name = "date_rdv", nullable = false)
    private LocalDate dateRdv;

    @Column(name = "heure_rdv", nullable = false)
    private LocalTime heureRdv;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutRDV statut;

    @Column(name = "date_creation", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime dateCreation;

    @PrePersist
    public void prePersist() {
        dateCreation = LocalDateTime.now();
        if (statut == null) statut = StatutRDV.EN_ATTENTE;
    }
}