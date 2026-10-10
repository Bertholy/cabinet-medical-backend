package com.cabinet.cabinet.service;

import com.cabinet.cabinet.entity.ReinitialisationMotDePasse;
import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.enums.Role;
import com.cabinet.cabinet.repository.ReinitialisationMotDePasseRepository;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import com.cabinet.cabinet.security.LoginAttemptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * « Mot de passe oublié » du personnel.
 * - La réponse à la demande est toujours la même (on ne révèle pas si une adresse existe) et immédiate : le traitement se fait à part.
 * - Le lien est secret (256 bits), valable 30 minutes, à usage unique ; seule son empreinte SHA-256 est stockée.
 * - Les super administrateurs reçoivent une notification : même sans serveur d'e-mails, ils peuvent réinitialiser le mot de passe.
 */
@Slf4j
@Service
public class MotDePasseOublieService {
    private static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");
    private static final SecureRandom ALEA = new SecureRandom();
    private static final Duration VALIDITE = Duration.ofMinutes(30);
    private static final int MAX_PAR_COMPTE = 3;       // par 15 minutes
    private static final int MAX_PAR_ADRESSE = 10;     // par 15 minutes
    private static final Duration FENETRE = Duration.ofMinutes(15);
    private static final int MIN = 10, MAX = 72;

    private final UtilisateurRepository utilisateurs;
    private final ReinitialisationMotDePasseRepository liens;
    private final PasswordEncoder encodeur;
    private final CourrierService courrier;
    private final NotificationService notifications;
    private final AuditService audit;
    private final LoginAttemptService tentatives;
    private final TransactionTemplate transaction;
    private final String urlSite;
    private final boolean synchrone;
    private final ExecutorService executeur = Executors.newFixedThreadPool(2, r -> { Thread t = new Thread(r, "mot-de-passe-oublie"); t.setDaemon(true); return t; });
    private final Map<String, Deque<Instant>> parAdresse = new ConcurrentHashMap<>();

    public MotDePasseOublieService(UtilisateurRepository utilisateurs, ReinitialisationMotDePasseRepository liens, PasswordEncoder encodeur,
                                   CourrierService courrier, NotificationService notifications, AuditService audit, LoginAttemptService tentatives,
                                   PlatformTransactionManager gestionnaire,
                                   @Value("${app.frontend-url:http://localhost:5173}") String urlSite,
                                   @Value("${app.mdp-oublie.synchrone:false}") boolean synchrone) {
        this.utilisateurs = utilisateurs;
        this.liens = liens;
        this.encodeur = encodeur;
        this.courrier = courrier;
        this.notifications = notifications;
        this.audit = audit;
        this.tentatives = tentatives;
        this.transaction = new TransactionTemplate(gestionnaire);
        this.urlSite = urlSite.endsWith("/") ? urlSite.substring(0, urlSite.length() - 1) : urlSite;
        this.synchrone = synchrone;
    }

    /** Point d'entrée public : réponse immédiate et identique, que le compte existe ou non. */
    public void demander(String email, String adresse) {
        if (limiteAtteinte(adresse)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Trop de demandes. Réessayez dans quelques minutes.");
        String adresseEmail = email == null ? "" : email.trim();
        Runnable travail = () -> {
            try { traiter(adresseEmail, adresse); }
            catch (RuntimeException e) { log.error("Mot de passe oublié : traitement impossible ({})", e.getMessage()); }
        };
        if (synchrone) travail.run(); else executeur.execute(travail);
    }

    private record Resultat(Utilisateur utilisateur, String lien) { }

    /** Crée le lien, prévient les super administrateurs, puis envoie l'e-mail. Visible pour les tests. */
    public void traiter(String email, String adresse) {
        if (email.isEmpty()) return;
        Resultat resultat = transaction.execute(statut -> {
            Utilisateur utilisateur = utilisateurs.findByEmailIgnoreCase(email).orElse(null);
            if (utilisateur == null) {
                audit.logAs(email.toLowerCase(), "PUBLIC", "MDP_OUBLIE_INCONNU", "Utilisateur", null, "Adresse inconnue — " + adresse);
                return null;
            }
            LocalDateTime maintenant = LocalDateTime.now(ZONE);
            if (liens.countByUtilisateurIdAndCreeLeAfter(utilisateur.getId(), maintenant.minus(FENETRE)) >= MAX_PAR_COMPTE) {
                audit.logAs(utilisateur.getEmail(), "PUBLIC", "MDP_OUBLIE_LIMITE", "Utilisateur", utilisateur.getId(), "Trop de demandes — " + adresse);
                return null;
            }
            liens.invaliderLiensDe(utilisateur.getId(), maintenant);
            String secret = HexFormat.of().formatHex(aleatoire());
            ReinitialisationMotDePasse demande = new ReinitialisationMotDePasse();
            demande.setUtilisateur(utilisateur);
            demande.setTokenHash(empreinte(secret));
            demande.setCreeLe(maintenant);
            demande.setExpireLe(maintenant.plus(VALIDITE));
            liens.save(demande);
            audit.logAs(utilisateur.getEmail(), "PUBLIC", "MDP_OUBLIE_DEMANDE", "Utilisateur", utilisateur.getId(), "Adresse " + adresse);
            return new Resultat(utilisateur, urlSite + "/medecin/reinitialiser?token=" + secret);
        });
        if (resultat == null) return;

        Utilisateur u = resultat.utilisateur();
        String sujet = "CASAFAMI — Réinitialisation de votre mot de passe";
        String texte = "Bonjour " + u.getNom() + ",\n\nUne réinitialisation du mot de passe de votre compte CASAFAMI a été demandée.\n"
                + "Pour choisir un nouveau mot de passe, ouvrez ce lien (valable 30 minutes, utilisable une seule fois) :\n\n" + resultat.lien()
                + "\n\nSi vous n'êtes pas à l'origine de cette demande, ignorez ce message : votre mot de passe actuel reste inchangé.\n\nCabinet CASAFAMI";
        boolean envoye = courrier.envoyer(u.getEmail(), sujet, texte);
        String suite = envoye ? "Un lien lui a été envoyé par e-mail." : "L'e-mail n'a pas pu être envoyé : réinitialisez son mot de passe dans « Comptes et accès ».";
        transaction.executeWithoutResult(statut -> {
            for (Utilisateur admin : utilisateurs.findByRoleIn(List.of(Role.SUPER_ADMIN))) {
                if (admin.getId().equals(u.getId())) continue;
                notifications.informer(admin, "MOT_DE_PASSE", "Mot de passe oublié",
                        u.getNom() + " (" + u.getEmail() + ") a demandé la réinitialisation de son mot de passe. " + suite, u.getNom(), "/admin/utilisateurs");
            }
        });
    }

    /** Applique le nouveau mot de passe si le lien est valide. */
    public void reinitialiser(String secret, String nouveauMotDePasse, String adresse) {
        if (nouveauMotDePasse == null || nouveauMotDePasse.length() < MIN || nouveauMotDePasse.length() > MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le mot de passe doit contenir entre " + MIN + " et " + MAX + " caractères.");
        }
        if (limiteAtteinte(adresse)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Trop de tentatives. Réessayez dans quelques minutes.");
        String hash = empreinte(secret == null ? "" : secret.trim());
        String email = transaction.execute(statut -> {
            ReinitialisationMotDePasse demande = liens.trouverParHash(hash).orElse(null);
            LocalDateTime maintenant = LocalDateTime.now(ZONE);
            if (demande == null || demande.getUtiliseLe() != null || demande.getExpireLe().isBefore(maintenant)) return null;
            Utilisateur utilisateur = demande.getUtilisateur();
            utilisateur.setMotDePasse(encodeur.encode(nouveauMotDePasse));
            utilisateurs.save(utilisateur);
            liens.invaliderLiensDe(utilisateur.getId(), maintenant);
            audit.logAs(utilisateur.getEmail(), "PUBLIC", "MDP_REINITIALISE", "Utilisateur", utilisateur.getId(), "Par lien de réinitialisation — " + adresse);
            return utilisateur.getEmail();
        });
        if (email == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce lien est invalide ou a expiré. Refaites une demande de réinitialisation.");
        tentatives.succes(email, adresse); // lève un éventuel blocage dû à des essais de connexion ratés
    }

    /** Ménage quotidien : les demandes expirées depuis plus d'un jour sont supprimées. */
    @Scheduled(cron = "0 45 3 * * *", zone = "Indian/Antananarivo")
    public void purger() {
        transaction.executeWithoutResult(statut -> liens.supprimerExpiresAvant(LocalDateTime.now(ZONE).minusDays(1)));
    }

    private boolean limiteAtteinte(String adresse) {
        String cle = adresse == null ? "?" : adresse;
        Deque<Instant> essais = parAdresse.computeIfAbsent(cle, k -> new ArrayDeque<>());
        synchronized (essais) {
            Instant limite = Instant.now().minus(FENETRE);
            while (!essais.isEmpty() && essais.peekFirst().isBefore(limite)) essais.pollFirst();
            if (essais.size() >= MAX_PAR_ADRESSE) return true;
            essais.addLast(Instant.now());
            return false;
        }
    }

    private static byte[] aleatoire() { byte[] octets = new byte[32]; ALEA.nextBytes(octets); return octets; }

    static String empreinte(String texte) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texte.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 est indisponible.", e); }
    }
}
