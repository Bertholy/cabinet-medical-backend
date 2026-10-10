package com.cabinet.cabinet.entity;

import com.cabinet.cabinet.util.TextRepairListener;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/** Jour exceptionnellement fermé (congé, jour férié, formation…). */
@Entity
@EntityListeners(TextRepairListener.class)
@Table(name = "jour_ferme")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JourFerme {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate date;

    @Column(length = 200)
    private String motif;
}
