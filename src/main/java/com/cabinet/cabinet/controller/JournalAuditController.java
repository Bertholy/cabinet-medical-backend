package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.entity.JournalAudit;
import com.cabinet.cabinet.repository.JournalAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Consultation du journal d'audit (réservée au super administrateur par SecurityConfig : /api/admin/**). */
@RestController
@RequestMapping("/api/admin/journal")
@RequiredArgsConstructor
public class JournalAuditController {
    private final JournalAuditRepository repository;

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> lister(@RequestParam(defaultValue = "") String action,
                                      @RequestParam(defaultValue = "") String q,
                                      @RequestParam(required = false) LocalDate debut,
                                      @RequestParam(required = false) LocalDate fin,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "25") int size) {
        LocalDateTime from = debut == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : debut.atStartOfDay();
        LocalDateTime to = fin == null ? LocalDateTime.of(2999, 1, 1, 0, 0) : fin.plusDays(1).atStartOfDay();
        Page<JournalAudit> result = repository.rechercher(action.trim(), q.trim(), from, to,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
        return Map.of(
                "contenu", result.getContent(),
                "page", result.getNumber(),
                "totalPages", result.getTotalPages(),
                "total", result.getTotalElements(),
                "actions", repository.actionsConnues());
    }
}
