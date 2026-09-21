package com.cabinet.cabinet.repository;

import com.cabinet.cabinet.entity.Traitement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TraitementRepository extends JpaRepository<Traitement, Long> {
    List<Traitement> findByMaladieId(Long maladieId);
}