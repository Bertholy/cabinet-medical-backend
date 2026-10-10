package com.cabinet.cabinet.entity;

import com.cabinet.cabinet.util.TextRepairListener;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Informations médicales durables d'un patient. Le patient est identifié par son numéro de téléphone canonique
 * (voir PhoneUtils.canonical) : c'est la seule identité que le site demande aux patients.
 */
@Entity
@EntityListeners(TextRepairListener.class)
@Table(name = "dossier_patient")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DossierPatient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String telephone;

    @Column(length = 100)
    private String nom;

    @Column(length = 1000)
    private String allergies;

    @Column(length = 2000)
    private String antecedents;

    @Column(length = 2000)
    private String notes;

    @Column(name = "modifie_le")
    private LocalDateTime modifieLe;

    @Column(name = "modifie_par", length = 150)
    private String modifiePar;
}
