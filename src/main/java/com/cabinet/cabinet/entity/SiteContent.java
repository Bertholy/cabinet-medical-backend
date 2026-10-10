package com.cabinet.cabinet.entity;

import com.cabinet.cabinet.util.TextRepairListener;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Personnalisation d'un élément du site public par le super administrateur :
 * texte remplacé et/ou décalage de position (en pixels) par rapport à l'emplacement d'origine.
 * La clé identifie l'élément dans le code du site, ex. « accueil.hero.titre ».
 */
@Entity
@EntityListeners(TextRepairListener.class)
@Table(name = "site_content")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SiteContent {
    @Id
    @Column(length = 100)
    private String cle;

    @Column(length = 2000)
    private String texte;

    @Column(name = "offset_x")
    private Integer offsetX;

    @Column(name = "offset_y")
    private Integer offsetY;

    @Column(name = "modifie_le")
    private LocalDateTime modifieLe;
}
