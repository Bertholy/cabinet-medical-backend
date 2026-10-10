package com.cabinet.cabinet.repository;

import com.cabinet.cabinet.entity.Service;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRepository extends JpaRepository<Service, Long> {
}