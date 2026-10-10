package com.cabinet.cabinet.repository;

import com.cabinet.cabinet.entity.ConsultationPrenatale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConsultationPrenataleRepository extends JpaRepository<ConsultationPrenatale, Long> {
    List<ConsultationPrenatale> findByGrossesseIdOrderByDateConsultationAscIdAsc(Long grossesseId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ConsultationPrenatale c where c.grossesse.id = :id")
    int supprimerDeLaGrossesse(@Param("id") Long grossesseId);
}
