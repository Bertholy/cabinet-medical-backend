package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.service.OccupationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/** Occupation du personnel par créneau (super administrateur uniquement : /api/admin/**). */
@RestController
@RequestMapping("/api/admin/occupation")
@RequiredArgsConstructor
public class OccupationController {
    private final OccupationService occupation;

    @GetMapping
    public Map<String, Object> jour(@RequestParam(required = false) LocalDate date) {
        return occupation.pourLeJour(date != null ? date : LocalDate.now(ZoneId.of("Indian/Antananarivo")));
    }
}
