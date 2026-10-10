package com.cabinet.cabinet.entity;

import com.cabinet.cabinet.util.TextRepairListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Vaccin administré à un enfant (une ligne par vaccin réalisé ; le calendrier est défini dans MaterniteService). */
@Entity
@EntityListeners(TextRepairListener.class)
@Table(name = "vaccination_enfant", uniqueConstraints = @UniqueConstraint(name = "uk_vaccination_enfant_code", columnNames = {"enfant_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
public class VaccinationEnfant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enfant_id", nullable = false)
    private Enfant enfant;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(name = "date_faite", nullable = false)
    private LocalDate dateFaite;

    @Column(length = 60)
    private String lot;

    @Column(name = "auteur_nom", length = 150)
    private String auteurNom;
}
