package com.cabinet.cabinet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Trace d'une action sensible : qui a fait quoi, sur quoi, et quand. */
@Entity
@Table(name = "journal_audit", indexes = @Index(name = "idx_journal_audit_date", columnList = "date_heure"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JournalAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date_heure", nullable = false)
    private LocalDateTime dateHeure;

    @Column(name = "acteur_email", length = 150)
    private String acteurEmail;

    @Column(name = "acteur_role", length = 30)
    private String acteurRole;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "cible_type", length = 60)
    private String cibleType;

    @Column(name = "cible_id", length = 60)
    private String cibleId;

    @Column(length = 1000)
    private String details;
}
