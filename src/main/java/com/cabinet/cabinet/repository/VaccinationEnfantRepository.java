package com.cabinet.cabinet.repository;

import com.cabinet.cabinet.entity.VaccinationEnfant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VaccinationEnfantRepository extends JpaRepository<VaccinationEnfant, Long> {
    List<VaccinationEnfant> findByEnfantId(Long enfantId);

    Optional<VaccinationEnfant> findByEnfantIdAndCode(Long enfantId, String code);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from VaccinationEnfant v where v.enfant.id = :id")
    int supprimerDeLEnfant(@Param("id") Long enfantId);
}
