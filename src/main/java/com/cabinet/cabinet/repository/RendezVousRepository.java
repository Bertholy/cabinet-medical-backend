package com.cabinet.cabinet.repository;
import com.cabinet.cabinet.entity.RendezVous;
import com.cabinet.cabinet.enums.StatutRDV;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface RendezVousRepository extends JpaRepository<RendezVous, Long> {

    List<RendezVous> findByDateRdv(LocalDate dateRdv);

    List<RendezVous> findByStatut(StatutRDV statut);

    List<RendezVous> findByTelephoneClient(String telephoneClient);

    boolean existsByDateRdvAndHeureRdv(LocalDate dateRdv, LocalTime heureRdv);

    long countByStatut(StatutRDV statut);
}