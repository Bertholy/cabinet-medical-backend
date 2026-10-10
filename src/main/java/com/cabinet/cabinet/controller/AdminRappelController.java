package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.service.AuditService;
import com.cabinet.cabinet.service.RappelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Déclenchement manuel des rappels de la veille (utile pour la démonstration et en cas de panne à 18h). */
@RestController
@RequestMapping("/api/admin/rappels")
@RequiredArgsConstructor
public class AdminRappelController {
    private final RappelService rappels;
    private final AuditService audit;

    @PostMapping("/envoyer")
    public Map<String, Integer> envoyer() {
        int envoyes = rappels.envoyerRappelsPourDemain();
        audit.log("RAPPELS_MANUELS", "RendezVous", null, envoyes + " rappel(s) envoyé(s) manuellement");
        return Map.of("envoyes", envoyes);
    }
}
