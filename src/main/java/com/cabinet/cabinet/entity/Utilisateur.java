package com.cabinet.cabinet.entity;

import com.cabinet.cabinet.util.TextRepairListener;
import com.cabinet.cabinet.enums.Role;
import jakarta.persistence.*;
import lombok.*;

@Entity
@EntityListeners(TextRepairListener.class)
@Table(name = "utilisateur")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(unique = true, length = 150)
    private String email;

    @Column(name = "mot_de_passe")
    private String motDePasse;

    @Column(length = 20)
    private String telephone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;
}
