package com.cabinet.cabinet.repository;

import com.cabinet.cabinet.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByDestinataireIdOrderByCreeLeDescIdDesc(Long destinataireId, Pageable pageable);

    long countByDestinataireIdAndLueFalse(Long destinataireId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.lue = true where n.destinataire.id = :id and n.lue = false")
    int marquerToutesLues(@Param("id") Long destinataireId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notification n where n.destinataire.id = :id")
    int supprimerDuDestinataire(@Param("id") Long destinataireId);

    /** Ménage : les notifications lues de plus de 60 jours sont supprimées. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notification n where n.lue = true and n.creeLe < :limite")
    int supprimerLuesAvant(@Param("limite") LocalDateTime limite);
}
