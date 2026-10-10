package com.cabinet.cabinet.service;

import com.cabinet.cabinet.entity.RendezVous;
import com.cabinet.cabinet.repository.RendezVousRepository;
import com.cabinet.cabinet.sms.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Rappel automatique envoyé la veille (18h, heure de Madagascar) aux patients qui ont un rendez-vous le lendemain.
 * Tant que l'envoi réel de SMS n'est pas activé (sms.mode=simulation), les rappels sont seulement écrits dans les journaux.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RappelService {
    private static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");
    private final RendezVousRepository rendezVous;
    private final SmsService sms;
    private final AuditService audit;

    @Value("${app.rappels.actifs:true}")
    private boolean actifs;

    @Scheduled(cron = "${app.rappels.cron:0 0 18 * * *}", zone = "Indian/Antananarivo")
    public void rappelsPlanifies() {
        if (!actifs) return;
        int envoyes = envoyerRappelsPourDemain();
        log.info("Rappels de rendez-vous envoyés : {}", envoyes);
    }

    /** Envoie les rappels pour demain ; chaque rendez-vous n'est rappelé qu'une fois. @return le nombre de rappels envoyés. */
    @Transactional
    public int envoyerRappelsPourDemain() {
        LocalDate demain = LocalDate.now(ZONE).plusDays(1);
        List<RendezVous> aRappeler = rendezVous.findARappeler(demain);
        for (RendezVous rdv : aRappeler) {
            sms.envoyerSms(rdv.getTelephoneClient(), "Rappel : vous avez rendez-vous demain " + rdv.getDateRdv()
                    + " à " + rdv.getHeureRdv() + " au cabinet Casafami. Pour annuler, contactez le cabinet.");
            rdv.setRappelEnvoye(true);
        }
        rendezVous.saveAll(aRappeler);
        if (!aRappeler.isEmpty()) audit.logAs("systeme", "SYSTEME", "RAPPELS_ENVOYES", "RendezVous", null, aRappeler.size() + " rappel(s) pour le " + demain);
        return aRappeler.size();
    }
}
