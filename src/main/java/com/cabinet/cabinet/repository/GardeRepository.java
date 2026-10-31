package com.cabinet.cabinet.repository;

import com.cabinet.cabinet.entity.Garde;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface GardeRepository extends JpaRepository<Garde, Long> {
    List<Garde> findByDateBetweenOrderByDateAscPeriodeAscIdAsc(LocalDate debut, LocalDate fin);
    List<Garde> findByDateBetweenAndPeriode(LocalDate debut, LocalDate fin, String periode);
    boolean existsByDateAndPeriodeAndUtilisateurId(LocalDate date, String periode, Long utilisateurId);
}
