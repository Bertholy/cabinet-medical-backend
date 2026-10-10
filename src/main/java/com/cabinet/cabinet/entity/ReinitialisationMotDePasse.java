package com.cabinet.cabinet.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Demande de réinitialisation de mot de passe : seule l'empreinte du lien est conservée (jamais le lien lui-même). */
@Entity
@Table(name = "reinitialisation_mot_de_passe", indexes = {
        @Index(name = "idx_reinit_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_reinit_utilisateur", columnList = "utilisateur_id, cree_le")})
@Getter
@Setter
@NoArgsConstructor
public class ReinitialisationMotDePasse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "cree_le", nullable = false)
    private LocalDateTime creeLe;

    @Column(name = "expire_le", nullable = false)
    private LocalDateTime expireLe;

    @Column(name = "utilise_le")
    private LocalDateTime utiliseLe;
}
