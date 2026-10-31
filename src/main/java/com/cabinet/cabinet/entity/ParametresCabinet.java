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

    /** Mise à niveau unique déjà faite : la secrétaire reçoit l'occupation du personnel, l'attribution des rendez-vous et le tour de garde. */
    @Column(name = "droits_secretaire_garde_migres")
    private Boolean droitsSecretaireGardeMigres = false;

    /** Les tarifs de départ des services (voir config/TarifsDeDepart) ont déjà été posés. */
    @Column(name = "tarifs_depart_ajoutes")
    private Boolean tarifsDepartAjoutes = false;

    /** Le personnel soignant a reçu le droit de confirmer ses propres rendez-vous (mise à niveau unique). */
    @Column(name = "droits_personnel_rdv_migres")
    private Boolean droitsPersonnelRdvMigres = false;
}
