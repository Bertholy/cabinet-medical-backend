package com.cabinet.cabinet.entity;

import com.cabinet.cabinet.util.TextRepairListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Message privé entre deux membres du personnel (messagerie interne). */
@Entity
@EntityListeners(TextRepairListener.class)
@Table(name = "message", indexes = {
        @Index(name = "idx_message_destinataire_lu", columnList = "destinataire_id, lu"),
        @Index(name = "idx_message_expediteur", columnList = "expediteur_id, cree_le")})
@Getter
@Setter
@NoArgsConstructor
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expediteur_id", nullable = false)
    private Utilisateur expediteur;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destinataire_id", nullable = false)
    private Utilisateur destinataire;

    @Column(nullable = false, length = 2000)
    private String contenu;

    @Column(name = "cree_le", nullable = false)
    private LocalDateTime creeLe;

    /** Lu par le destinataire. */
    @Column(nullable = false)
    private boolean lu = false;
}
