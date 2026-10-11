package com.cabinet.cabinet.entity;

import com.cabinet.cabinet.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Droit accordé (ou non) à un rôle : une ligne par couple rôle / permission, créée au démarrage avec les valeurs d'origine. */
@Entity
@Table(name = "permission_role", uniqueConstraints = @UniqueConstraint(name = "uk_permission_role", columnNames = {"role", "code"}))
@Getter
@Setter
@NoArgsConstructor
public class PermissionRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false)
    private boolean accorde;
}
