package com.cabinet.cabinet.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Exception personnelle : droit accordé en plus du rôle (accorde = vrai) ou retiré malgré le rôle (accorde = faux). */
@Entity
@Table(name = "permission_utilisateur", uniqueConstraints = @UniqueConstraint(name = "uk_permission_utilisateur", columnNames = {"utilisateur_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
public class PermissionUtilisateur {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false)
    private boolean accorde;
}
