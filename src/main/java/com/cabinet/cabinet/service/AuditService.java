package com.cabinet.cabinet.service;

import com.cabinet.cabinet.entity.JournalAudit;
import com.cabinet.cabinet.repository.JournalAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Journal d'audit. L'écriture se fait dans sa propre transaction et ne fait jamais échouer l'action métier :
 * une panne du journal ne doit pas empêcher de soigner un patient.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {
    private static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");
    private final JournalAuditRepository repository;

    /** Acteur = utilisateur connecté, ou « public » pour un visiteur du site. */
    public void log(String action, String cibleType, Object cibleId, String details) {
        String email = "public";
        String role = "PUBLIC";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            email = auth.getName();
            role = auth.getAuthorities().stream().findFirst().map(a -> a.getAuthority().replaceFirst("^ROLE_", "")).orElse("?");
        }
        logAs(email, role, action, cibleType, cibleId, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAs(String email, String role, String action, String cibleType, Object cibleId, String details) {
        try {
            JournalAudit entry = new JournalAudit();
            entry.setDateHeure(LocalDateTime.now(ZONE));
            entry.setActeurEmail(limiter(email, 150));
            entry.setActeurRole(limiter(role, 30));
            entry.setAction(limiter(action, 60));
            entry.setCibleType(limiter(cibleType, 60));
            entry.setCibleId(cibleId == null ? null : limiter(String.valueOf(cibleId), 60));
            entry.setDetails(limiter(details, 1000));
            repository.save(entry);
        } catch (RuntimeException e) {
            log.warn("Écriture du journal d'audit impossible ({}) : {}", action, e.getMessage());
        }
    }

    private static String limiter(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
