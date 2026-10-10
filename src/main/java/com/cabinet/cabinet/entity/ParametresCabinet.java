package com.cabinet.cabinet.entity;

import jakarta.persistence.*;
import lombok.Data;

/** Paramètres généraux du planning (une seule ligne, id = 1). */
@Entity
@Table(name = "parametres_cabinet")
@Data
public class ParametresCabinet {
    @Id
    private Long id = 1L;

    /** Durée d'un créneau en minutes (15 à 120). */
    @Column(name = "duree_creneau_minutes", nullable = false)
    private int dureeCreneauMinutes = 30;

    /** Nombre de rendez-vous acceptés en même temps sur un créneau (1 = un seul patient à la fois). */
    @Column(name = "capacite_par_creneau", nullable = false)
    private int capaciteParCreneau = 1;
}
