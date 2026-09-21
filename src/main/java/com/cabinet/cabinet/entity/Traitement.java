package com.cabinet.cabinet.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "traitement")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Traitement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "medicament", nullable = false, length = 150)
    private String medicament;

    @Column(length = 200)
    private String posologie;

    @Column(length = 500)
    private String remarques;

    // Relation : un traitement appartient à une maladie
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maladie_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private Maladie maladie;
}