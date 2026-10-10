package com.cabinet.cabinet.repository;

import com.cabinet.cabinet.entity.JourFerme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JourFermeRepository extends JpaRepository<JourFerme, Long> {
    Optional<JourFerme> findByDate(LocalDate date);
    List<JourFerme> findByDateGreaterThanEqualOrderByDateAsc(LocalDate date);
    List<JourFerme> findByDateBetweenOrderByDateAsc(LocalDate debut, LocalDate fin);
}
