package com.cabinet.cabinet.entity;

import com.cabinet.cabinet.util.TextRepairListener;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Notification affichée en haut de l'écran d'un membre du personnel (cloche) :
 * rendez-vous qui lui est attribué par le super administrateur, retiré, annulé ou déplacé.
 */
@Entity
@EntityListeners(TextRepairListener.class)
@Table(name = "notification", indexes = @Index(name = "idx_notification_destinataire", columnList = "destinataire_id, lue"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destinataire_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private Utilisateur destinataire;

    /** RDV_ATTRIBUE, RDV_RETIRE, RDV_ANNULE, RDV_DEPLACE. */
    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, length = 150)
    private String titre;

    @Column(nullable = false, length = 500)
    private String message;

    /** Numéro du rendez-vous concerné (sans clé étrangère : la notification survit à sa suppression). */
    @Column(name = "rendez_vous_id")
    private Long rendezVousId;

    @Column(nullable = false)
    private boolean lue = false;

    @Column(name = "cree_le", nullable = false)
    private LocalDateTime creeLe;

    /** Nom de la personne qui a déclenché la notification (le super administrateur, ou « Patient »). */
    @Column(name = "cree_par", length = 100)
    private String creePar;
}
