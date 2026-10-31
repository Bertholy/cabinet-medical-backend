package com.cabinet.cabinet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Le tour de garde « de référence » proposé par l'administration : l'ordre des personnes, la date où le tour commence et la période de garde.
 * Il est enregistré à chaque tour généré à la main, et la « génération auto » le suit pour remplir les mois suivants. Une seule ligne (id 1).
 */
@Entity
@Table(name = "tour_garde_modele")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TourGardeModele {
    public static final Long ID_UNIQUE = 1L;

    @Id
    private Long id = ID_UNIQUE;

    /** Identifiants des personnes, dans l'ordre du tour, séparés par des virgules. */
    @Column(nullable = false, length = 500)
    private String ordre;

    /** Jour où la première personne de la liste est de garde ; les suivantes prennent les jours d'après, en boucle. */
    @Column(name = "date_depart", nullable = false)
    private LocalDate debut;

    /** « JOUR_NUIT », « JOUR » ou « NUIT ». */
    @Column(nullable = false, length = 10)
    private String periode;
}
