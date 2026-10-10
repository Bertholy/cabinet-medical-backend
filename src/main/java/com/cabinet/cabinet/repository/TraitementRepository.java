package com.cabinet.cabinet.repository;

import com.cabinet.cabinet.entity.Traitement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TraitementRepository extends JpaRepository<Traitement, Long> {
    List<Traitement> findByMaladieId(Long maladieId);
}