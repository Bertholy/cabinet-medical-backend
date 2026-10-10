package com.cabinet.cabinet.repository;

import com.cabinet.cabinet.entity.Enfant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EnfantRepository extends JpaRepository<Enfant, Long> {
    List<Enfant> findByMereTelephoneOrderByDateNaissanceDescIdDesc(String mereTelephone);

    /** Enfants assez jeunes pour avoir encore des vaccins au calendrier. */
    List<Enfant> findByDateNaissanceAfter(LocalDate date);
}
