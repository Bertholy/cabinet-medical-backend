package com.cabinet.cabinet.repository;

import com.cabinet.cabinet.entity.Maladie;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaladieRepository extends JpaRepository<Maladie, Long> {
}