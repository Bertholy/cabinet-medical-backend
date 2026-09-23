package com.cabinet.cabinet.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmsService {

    @Value("${sms.mode:simulation}")
    private String mode;

    public void envoyerSms(String numero, String message) {
        if ("twilio".equalsIgnoreCase(mode)) {
            envoyerViaTwilio(numero, message);
        } else {
            envoyerEnSimulation(numero, message);
        }
    }

    private void envoyerEnSimulation(String numero, String message) {
        log.info("");
        log.info("═══════════════════════════════════════════════════════");
        log.info("📱 SMS SIMULÉ");
        log.info("   Destinataire : {}", numero);
        log.info("   Message      : {}", message);
        log.info("═══════════════════════════════════════════════════════");
        log.info("");
    }

    private void envoyerViaTwilio(String numero, String message) {
        // À activer plus tard (octobre/novembre)
        log.warn("Mode Twilio non encore configuré. Message à {} : {}", numero, message);
        // TODO : Implémenter Twilio ici quand on aura le compte
    }
}