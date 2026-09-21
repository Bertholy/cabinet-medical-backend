package com.cabinet.cabinet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "maladie")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Maladie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(length = 1000)
    private String description;

    @Column(length = 500)
    private String symptomes;

    // Relation : une maladie a plusieurs traitements
    @OneToMany(mappedBy = "maladie", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Traitement> traitements = new ArrayList<>();
}