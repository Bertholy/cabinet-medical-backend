package com.cabinet.cabinet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

/** Horaires d'ouverture d'un jour de la semaine (1 = lundi … 7 = dimanche). */
@Entity
@Table(name = "horaire_jour")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HoraireJour {
    @Id
    @Column(name = "jour_semaine")
    private Integer jourSemaine;

    @Column(nullable = false)
    private boolean ouvert;

    @Column(name = "matin_debut")
    private LocalTime matinDebut;
    @Column(name = "matin_fin")
    private LocalTime matinFin;
    @Column(name = "apres_midi_debut")
    private LocalTime apresMidiDebut;
    @Column(name = "apres_midi_fin")
    private LocalTime apresMidiFin;
}
