package com.cabinet.cabinet.entity;

import com.cabinet.cabinet.util.TextRepairListener;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@EntityListeners(TextRepairListener.class)
@Table(name = "traitement")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Traitement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Le nom du médicament est obligatoire")
    @Size(max = 150, message = "Le nom du médicament ne peut pas dépasser 150 caractères")
    @Column(name = "medicament", nullable = false, length = 150)
    private String medicament;

    @Size(max = 200, message = "La posologie ne peut pas dépasser 200 caractères")
    @Column(length = 200)
    private String posologie;

    /** Durée du traitement, ex. « 7 jours ». */
    @Size(max = 100, message = "La durée ne peut pas dépasser 100 caractères")
    @Column(length = 100)
    private String duree;

    @Size(max = 500, message = "Les remarques ne peuvent pas dépasser 500 caractères")
    @Column(length = 500)
    private String remarques;

    // Relation : un traitement appartient à une maladie
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maladie_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private Maladie maladie;
}