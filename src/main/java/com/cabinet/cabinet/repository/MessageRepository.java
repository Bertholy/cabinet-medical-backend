package com.cabinet.cabinet.repository;

import com.cabinet.cabinet.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Échanges entre deux personnes, du plus récent au plus ancien. */
    @Query("select m from Message m where (m.expediteur.id = :a and m.destinataire.id = :b) or (m.expediteur.id = :b and m.destinataire.id = :a) order by m.creeLe desc, m.id desc")
    List<Message> conversation(@Param("a") Long a, @Param("b") Long b, Pageable pageable);

    /** Tous les messages envoyés ou reçus par une personne, du plus récent au plus ancien (pour la liste des conversations). */
    @Query("select m from Message m join fetch m.expediteur join fetch m.destinataire where m.expediteur.id = :id or m.destinataire.id = :id order by m.creeLe desc, m.id desc")
    List<Message> recentsDe(@Param("id") Long id, Pageable pageable);

    long countByDestinataireIdAndLuFalse(Long destinataireId);

    /** Messages non lus par expéditeur : [idExpediteur, nombre]. */
    @Query("select m.expediteur.id, count(m) from Message m where m.destinataire.id = :id and m.lu = false group by m.expediteur.id")
    List<Object[]> nonLusParExpediteur(@Param("id") Long destinataireId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Message m set m.lu = true where m.destinataire.id = :moi and m.expediteur.id = :autre and m.lu = false")
    int marquerLus(@Param("moi") Long moi, @Param("autre") Long autre);

    long countByExpediteurIdAndCreeLeAfter(Long expediteurId, LocalDateTime depuis);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Message m where m.expediteur.id = :id or m.destinataire.id = :id")
    int supprimerDe(@Param("id") Long utilisateurId);

    /** Ménage : les messages déjà lus depuis plus d'un an sont supprimés. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Message m where m.lu = true and m.creeLe < :limite")
    int supprimerLusAvant(@Param("limite") LocalDateTime limite);
}
