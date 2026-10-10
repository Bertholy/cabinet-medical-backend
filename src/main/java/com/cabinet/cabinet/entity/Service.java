package com.cabinet.cabinet.entity;

import com.cabinet.cabinet.util.TextRepairListener;
import jakarta.persistence.*;
import lombok.*;

@Entity
@EntityListeners(TextRepairListener.class)
@Table(name = "service")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(length = 500)
    private String description;

    @Column(length = 50)
    private String duree;

    @Column(name = "image_url", length = 500)
    private String imageUrl;
}
