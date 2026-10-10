package com.cabinet.cabinet.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envoi d'e-mails. Tant que le serveur d'e-mails n'est pas configuré (CASAFAMI_MAIL_HOST), rien n'est envoyé :
 * le message est seulement écrit dans les journaux, comme pour les SMS en mode simulation.
 */
@Slf4j
@Service
public class CourrierService {
    private final ObjectProvider<JavaMailSender> expediteur;
    private final String hote;
    private final String adresseExpediteur;

    public CourrierService(ObjectProvider<JavaMailSender> expediteur,
                           @Value("${spring.mail.host:}") String hote,
                           @Value("${app.mail.from:}") String adresseExpediteur,
                           @Value("${spring.mail.username:}") String utilisateur) {
        this.expediteur = expediteur;
        this.hote = hote == null ? "" : hote.trim();
        this.adresseExpediteur = adresseExpediteur != null && !adresseExpediteur.isBlank() ? adresseExpediteur.trim() : utilisateur;
    }

    public boolean estConfigure() {
        return !hote.isEmpty() && expediteur.getIfAvailable() != null;
    }

    /** Retourne true si le message est réellement parti, false en mode simulation ou en cas d'échec. */
    public boolean envoyer(String destinataire, String sujet, String texte) {
        if (!estConfigure()) {
            log.info("[E-MAIL SIMULÉ] à {} — {}\n{}", destinataire, sujet, texte);
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (adresseExpediteur != null && !adresseExpediteur.isBlank()) message.setFrom(adresseExpediteur);
            message.setTo(destinataire);
            message.setSubject(sujet);
            message.setText(texte);
            expediteur.getObject().send(message);
            log.info("E-mail envoyé à {} ({})", destinataire, sujet);
            return true;
        } catch (RuntimeException e) {
            log.error("Envoi de l'e-mail à {} impossible : {}", destinataire, e.getMessage());
            return false;
        }
    }
}
