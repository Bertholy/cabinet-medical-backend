package com.cabinet.cabinet.repository;

import com.cabinet.cabinet.entity.JournalAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface JournalAuditRepository extends JpaRepository<JournalAudit, Long> {

    // Paramètres jamais null (valeurs « neutres » à la place) pour éviter les erreurs de typage PostgreSQL.
    @Query("select j from JournalAudit j where (:action = '' or j.action = :action) "
            + "and (lower(coalesce(j.details, '')) like lower(concat('%', :q, '%')) or lower(coalesce(j.acteurEmail, '')) like lower(concat('%', :q, '%'))) "
            + "and j.dateHeure >= :debut and j.dateHeure < :fin order by j.dateHeure desc")
    Page<JournalAudit> rechercher(@Param("action") String action, @Param("q") String q,
                                  @Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin, Pageable pageable);

    @Query("select distinct j.action from JournalAudit j order by j.action")
    List<String> actionsConnues();
}
