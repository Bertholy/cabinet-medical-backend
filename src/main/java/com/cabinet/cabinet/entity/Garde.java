package com.cabinet.cabinet.entity;

import com.cabinet.cabinet.util.TextRepairListener;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/** Une personne du personnel de garde un jour donné, de jour ou de nuit (tour de garde du cabinet). */
@Entity
@EntityListeners(TextRepairListener.class)
@Table(name = "garde", uniqueConstraints = @UniqueConstraint(columnNames = {"date_garde", "periode", "utilisateur_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Garde {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date_garde", nullable = false)
    private LocalDate date;

    /** « JOUR » ou « NUIT ». */
    @Column(nullable = false, length = 10)
    private String periode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(length = 200)
    private String note;
}
