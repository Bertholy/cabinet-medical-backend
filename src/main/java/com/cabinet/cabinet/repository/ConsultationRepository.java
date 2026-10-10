package com.cabinet.cabinet.repository;

import com.cabinet.cabinet.entity.Consultation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsultationRepository extends JpaRepository<Consultation, Long> {
    List<Consultation> findByTelephoneOrderByDateConsultationDescIdDesc(String telephone);
    Optional<Consultation> findFirstByRendezVousId(Long rendezVousId);
}
