package com.cabinet.cabinet.service;

import com.cabinet.cabinet.entity.Notification;
import com.cabinet.cabinet.entity.RendezVous;
import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Création des notifications destinées au personnel (affichées dans la cloche en haut de l'écran). */
@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");
    private final NotificationRepository notifications;

    /** Nom du rôle en français, tel qu'il s'affiche aux utilisateurs. */
    public static String libelleRole(com.cabinet.cabinet.enums.Role role) {
        return switch (role) {
            case SUPER_ADMIN -> "super administrateur";
            case MEDECIN -> "médecin";
            case SAGE_FEMME -> "sage-femme";
            case SECRETAIRE -> "secrétaire";
            case INFIRMIERE -> "infirmière";
        };
    }

    /** « Marie Rasoa (sage-femme) » : qui a fait l'action, affiché dans la notification. */
    public static String origine(Utilisateur u) {
        if (u == null) return "Le cabinet";
        return u.getNom() + " (" + libelleRole(u.getRole()) + ")";
    }

    @Transactional
    public void notifier(Utilisateur destinataire, String type, String titre, RendezVous rdv, String phrase, String creePar) {
        notifier(destinataire, type, titre, rdv, phrase, creePar, null);
    }

    /** @param lien page ouverte au clic ; null = le rendez-vous sur le tableau de bord du super administrateur, ou « Mes patients » pour les autres. */
    @Transactional
    public void notifier(Utilisateur destinataire, String type, String titre, RendezVous rdv, String phrase, String creePar, String lien) {
        if (destinataire == null) return;
        Notification n = new Notification();
        n.setDestinataire(destinataire);
        n.setType(type);
        n.setTitre(titre);
        n.setMessage(limiter(phrase + " — " + rdv.getNomClient() + ", le " + rdv.getDateRdv() + " à " + String.valueOf(rdv.getHeureRdv()).substring(0, 5)
                + (rdv.getMotif() == null ? "" : " (" + resume(rdv.getMotif()) + ")"), 500));
        n.setRendezVousId(rdv.getId());
        // Un clic sur la notification ouvre la page où le rendez-vous se traite : tableau de bord du super administrateur, « Mes patients » pour les autres.
        n.setLien(lien != null ? lien : (destinataire.getRole() == com.cabinet.cabinet.enums.Role.SUPER_ADMIN ? "/admin/dashboard?rdv=" + rdv.getId() : "/espace/mes-patients?rdv=" + rdv.getId()));
        n.setCreeLe(LocalDateTime.now(ZONE));
        n.setCreePar(creePar);
        notifications.save(n);
    }

    /** Notification qui ne concerne pas un rendez-vous (sauvegarde, mot de passe oublié…) ; « lien » = page ouverte au clic. */
    @Transactional
    public void informer(Utilisateur destinataire, String type, String titre, String message, String creePar, String lien) {
        if (destinataire == null) return;
        Notification n = new Notification();
        n.setDestinataire(destinataire);
        n.setType(type);
        n.setTitre(limiter(titre, 150));
        n.setMessage(limiter(message, 500));
        n.setCreeLe(LocalDateTime.now(ZONE));
        n.setCreePar(creePar);
        n.setLien(lien);
        notifications.save(n);
    }

    /** Ménage quotidien (3h30) : les notifications déjà lues depuis plus de 60 jours sont supprimées. */
    @Scheduled(cron = "0 30 3 * * *", zone = "Indian/Antananarivo")
    @Transactional
    public void purger() {
        notifications.supprimerLuesAvant(LocalDateTime.now(ZONE).minusDays(60));
    }

    /** Première ligne du motif, sans le marqueur de priorité, pour que la notification reste courte. */
    private static String resume(String motif) {
        String ligne = motif.lines().filter(l -> !l.isBlank()).findFirst().orElse("");
        return limiter(ligne, 80);
    }

    private static String limiter(String texte, int max) {
        return texte.length() <= max ? texte : texte.substring(0, max - 1) + "…";
    }
}
