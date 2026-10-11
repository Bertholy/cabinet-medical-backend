package com.cabinet.cabinet.service;

import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.enums.Role;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import com.cabinet.cabinet.sms.SmsService;
import com.cabinet.cabinet.util.PhoneUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Jours de congé : chaque membre du personnel reçoit une notification dans son compte et, s'il a un numéro de téléphone,
 * un SMS (simulé tant que le SMS réel n'est pas activé). Celui qui a posé le congé n'est pas prévenu de son propre geste.
 */
@Service
@RequiredArgsConstructor
public class CongesNotificationService {
    private static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");
    private static final DateTimeFormatter DATE_LONGUE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter DATE_COURTE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final UtilisateurRepository utilisateurs;
    private final NotificationService notifications;
    private final SmsService smsService;

    public record Resultat(int personnes, int sms) { }

    @Transactional
    public Resultat fermeture(LocalDate date, String motif, long rdvImpactes, String emailActeur, String nomActeur) {
        if (date.isBefore(LocalDate.now(ZONE))) return new Resultat(0, 0);
        String raison = motif == null || motif.isBlank() ? "" : " (" + motif.trim() + ")";
        String sms = "CASAFAMI : le cabinet sera fermé le " + date.format(DATE_COURTE) + raison + ".";
        return diffuser("CONGE", "Jour de fermeture du cabinet", emailActeur, nomActeur, sms, utilisateur -> {
            String base = "Le cabinet sera fermé le " + date.format(DATE_LONGUE) + raison + " : aucun rendez-vous ne sera pris ce jour-là.";
            return utilisateur.getRole() == Role.SUPER_ADMIN && rdvImpactes > 0
                    ? base + " " + rdvImpactes + " rendez-vous existent déjà ce jour-là : pensez à les déplacer ou à les annuler."
                    : base;
        });
    }

    @Transactional
    public Resultat reouverture(LocalDate date, String emailActeur, String nomActeur) {
        if (date.isBefore(LocalDate.now(ZONE))) return new Resultat(0, 0);
        String sms = "CASAFAMI : le cabinet sera finalement ouvert le " + date.format(DATE_COURTE) + ".";
        return diffuser("CONGE", "Fermeture annulée", emailActeur, nomActeur, sms,
                utilisateur -> "Le cabinet sera finalement ouvert le " + date.format(DATE_LONGUE) + " : la fermeture prévue est annulée.");
    }

    private Resultat diffuser(String type, String titre, String emailActeur, String nomActeur, String sms, java.util.function.Function<Utilisateur, String> message) {
        int personnes = 0, textos = 0;
        for (Utilisateur u : utilisateurs.findAll()) {
            if (emailActeur != null && emailActeur.equalsIgnoreCase(u.getEmail())) continue;
            String lien = u.getRole() == Role.SUPER_ADMIN ? "/admin/planning" : "/calendrier";
            notifications.informer(u, type, titre, message.apply(u), nomActeur, lien);
            personnes++;
            if (u.getTelephone() != null && PhoneUtils.canonical(u.getTelephone()) != null) {
                smsService.envoyerSms(u.getTelephone(), sms);
                textos++;
            }
        }
        return new Resultat(personnes, textos);
    }
}
