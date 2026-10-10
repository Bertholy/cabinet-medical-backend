package com.cabinet.cabinet.repository;

import com.cabinet.cabinet.entity.DossierPatient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DossierPatientRepository extends JpaRepository<DossierPatient, Long> {
    Optional<DossierPatient> findByTelephone(String telephone);
}
