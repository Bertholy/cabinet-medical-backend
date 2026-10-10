package com.cabinet.cabinet.repository;

import com.cabinet.cabinet.entity.Grossesse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GrossesseRepository extends JpaRepository<Grossesse, Long> {
    List<Grossesse> findByTelephoneOrderByCreeLeDescIdDesc(String telephone);

    Optional<Grossesse> findFirstByTelephoneAndStatut(String telephone, String statut);

    List<Grossesse> findByStatut(String statut);
}
