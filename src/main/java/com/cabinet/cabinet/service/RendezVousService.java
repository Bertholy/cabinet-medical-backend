package com.cabinet.cabinet.service;

import com.cabinet.cabinet.dto.RendezVousRequestDTO;
import com.cabinet.cabinet.dto.RendezVousResponseDTO;
import com.cabinet.cabinet.entity.RendezVous;
import com.cabinet.cabinet.enums.StatutRDV;
import com.cabinet.cabinet.exception.CreneauIndisponibleException;
import com.cabinet.cabinet.exception.RendezVousIntrouvableException;
import com.cabinet.cabinet.exception.StatutInvalideException;
import com.cabinet.cabinet.repository.RendezVousRepository;
import com.cabinet.cabinet.repository.ServiceRepository;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.enums.Role;
import com.cabinet.cabinet.sms.SmsService;
import com.cabinet.cabinet.util.PhoneUtils;
import com.cabinet.cabinet.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class RendezVousService {

    private final RendezVousRepository rendezVousRepository;
    private final SmsService smsService;
    private final ServiceRepository serviceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final PlanningService planning;
    private final AuditService audit;
    private final NotificationService notifications;
    private final JdbcTemplate jdbc;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ZoneId CABINET_ZONE = ZoneId.of("Indian/Antananarivo");

    // ============ NUMÉRO DU MÉDECIN (notification de chaque nouveau rendez-vous) ============
    @org.springframework.beans.factory.annotation.Value("${app.notification.numero-medecin:0343498888}")
    private String numeroMedecin;

    // ============ CRÉER UN RDV ============
    @Transactional
    public RendezVousResponseDTO creerRendezVous(RendezVousRequestDTO dto) {
        // Les réservations d'une même journée sont traitées l'une après l'autre : pas de dépassement de capacité en cas de clics simultanés.
        verrouillerJour(dto.getDateRdv());

        // 1. Vérifier que le créneau existe (jour ouvré, horaire du cabinet, pas dans le passé)
        if (!isBookableSlot(dto.getDateRdv(), dto.getHeureRdv())) {
            throw new CreneauIndisponibleException(motifIndisponibilite(dto.getDateRdv()));
        }

        // 2. Vérifier qu'il reste de la place (et que le praticien choisi est libre)
        verifierDisponibilite(dto.getDateRdv(), dto.getHeureRdv(), dto.getPraticienId(), 0L);

        // 3. Créer l'entité
        RendezVous rdv = new RendezVous();
        appliquerChamps(rdv, dto);
        String accessToken = createAccessToken();
        rdv.setClientAccessTokenHash(hashAccessToken(accessToken));
        // statut et dateCreation seront mis par @PrePersist

        // 4. Sauvegarder
        RendezVous saved;
        try {
            saved = rendezVousRepository.saveAndFlush(rdv);
        } catch (DataIntegrityViolationException ex) {
            throw new CreneauIndisponibleException("Ce créneau vient d’être réservé. Choisissez un autre horaire.");
        }
        audit.log("RDV_CREE", "RendezVous", saved.getId(), saved.getNomClient() + " — " + saved.getDateRdv() + " " + saved.getHeureRdv());
        if (saved.getPraticien() != null) {
            notifications.notifier(saved.getPraticien(), "RDV_ATTRIBUE", "Nouveau rendez-vous attribué", saved, "Un rendez-vous vous est confié", "Le super administrateur");
        }

        // 5. Envoyer SMS au médecin (notification)
        String message = "Nouveau RDV : " + saved.getNomClient()
                + " le " + saved.getDateRdv()
                + " à " + saved.getHeureRdv();
        smsService.envoyerSms(numeroMedecin, message);

        // 6. Convertir en DTO de réponse
        RendezVousResponseDTO response = toResponseDTO(saved);
        response.setClientAccessToken(accessToken);
        return response;
    }

    // ============ LISTER TOUS LES RDV ============
    @Transactional(readOnly = true)
    public List<RendezVousResponseDTO> getAllRendezVous() {
        return rendezVousRepository.findBySupprimeFalse()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RendezVousResponseDTO> getRendezVousDuJour(Long praticienId) {
        LocalDate aujourdHui = LocalDate.now(CABINET_ZONE);
        return getRendezVousPeriode(aujourdHui, aujourdHui, praticienId);
    }

    @Transactional(readOnly = true)
    public List<RendezVousResponseDTO> getRendezVousPeriode(LocalDate debut, LocalDate fin, Long praticienId) {
        List<RendezVous> result = praticienId == null
                ? rendezVousRepository.findBySupprimeFalseAndDateRdvBetweenOrderByDateRdvAscHeureRdvAsc(debut, fin)
                : rendezVousRepository.findBySupprimeFalseAndPraticienIdAndDateRdvBetweenOrderByDateRdvAscHeureRdvAsc(praticienId, debut, fin);
        return result.stream().map(this::toResponseDTO).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getStatistiques(LocalDate debut, LocalDate fin, Long praticienId) {
        long total = compter(null, debut, fin, praticienId);
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("pending", compter(StatutRDV.EN_ATTENTE, debut, fin, praticienId));
        stats.put("confirmed", compter(StatutRDV.CONFIRME, debut, fin, praticienId));
        stats.put("cancelled", compter(StatutRDV.ANNULE, debut, fin, praticienId));
        stats.put("done", compter(StatutRDV.TERMINE, debut, fin, praticienId));
        return stats;
    }

    private long compter(StatutRDV statut, LocalDate debut, LocalDate fin, Long praticienId) {
        if (praticienId == null) {
            return statut == null
                    ? rendezVousRepository.countBySupprimeFalseAndDateRdvBetween(debut, fin)
                    : rendezVousRepository.countBySupprimeFalseAndStatutAndDateRdvBetween(statut, debut, fin);
        }
        return statut == null
                ? rendezVousRepository.countBySupprimeFalseAndPraticienIdAndDateRdvBetween(praticienId, debut, fin)
                : rendezVousRepository.countBySupprimeFalseAndPraticienIdAndStatutAndDateRdvBetween(praticienId, statut, debut, fin);
    }

    /** Recherche rapide du personnel : par nom (sans accents, partiel) ou par numéro, quel que soit le format saisi. */
    @Transactional(readOnly = true)
    public List<Map<String, String>> rechercherPatients(String q) {
        String terme = q == null ? "" : q.trim();
        String chiffres = PhoneUtils.digits(terme);
        if (chiffres.startsWith("261")) chiffres = "0" + chiffres.substring(3);
        String nom = TextUtils.escapeLike(TextUtils.fold(terme));
        return rendezVousRepository.rechercherPatients(TextUtils.ACCENTS, TextUtils.PLAIN, nom,
                        chiffres.length() >= 3 ? chiffres : "#", 8).stream()
                .map(patient -> Map.of("nom", (String) patient[0], "telephone", (String) patient[1]))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RendezVous> getCorbeille() {
        return rendezVousRepository.findDeletedWithActor();
    }

    @Transactional
    public RendezVousResponseDTO modifierRendezVous(Long id, RendezVousRequestDTO dto, boolean peutAttribuer) {
        verrouillerJour(dto.getDateRdv());
        RendezVous rdv = rendezVousRepository.findByIdAndSupprimeFalse(id)
                .orElseThrow(() -> new RendezVousIntrouvableException("Rendez-vous introuvable avec l'id : " + id));
        // Le choix de la personne qui prend le rendez-vous appartient au super administrateur : pour les autres, il est conservé.
        if (!peutAttribuer) dto.setPraticienId(rdv.getPraticien() == null ? null : rdv.getPraticien().getId());
        String ancienneDate = rdv.getDateRdv() + " " + rdv.getHeureRdv();
        Utilisateur ancienPraticienEntite = rdv.getPraticien();
        boolean creneauChange = !dto.getDateRdv().equals(rdv.getDateRdv()) || !dto.getHeureRdv().equals(rdv.getHeureRdv());
        Long ancienPraticien = rdv.getPraticien() == null ? null : rdv.getPraticien().getId();
        boolean praticienChange = !Objects.equals(ancienPraticien, dto.getPraticienId());
        if (creneauChange && !isBookableSlot(dto.getDateRdv(), dto.getHeureRdv())) {
            throw new CreneauIndisponibleException(motifIndisponibilite(dto.getDateRdv()));
        }
        if ((creneauChange || praticienChange) && rdv.getStatut() != StatutRDV.ANNULE) {
            verifierDisponibilite(dto.getDateRdv(), dto.getHeureRdv(), dto.getPraticienId(), id);
        }
        appliquerChamps(rdv, dto);
        try {
            RendezVousResponseDTO saved = toResponseDTO(rendezVousRepository.saveAndFlush(rdv));
            audit.log("RDV_MODIFIE", "RendezVous", id, rdv.getNomClient() + " — " + rdv.getDateRdv() + " " + rdv.getHeureRdv());
            if (praticienChange) {
                notifications.notifier(rdv.getPraticien(), "RDV_ATTRIBUE", "Nouveau rendez-vous attribué", rdv, "Un rendez-vous vous est confié", "Le super administrateur");
                notifications.notifier(ancienPraticienEntite, "RDV_RETIRE", "Rendez-vous retiré", rdv, "Ce rendez-vous ne vous est plus attribué", "Le super administrateur");
            } else if (creneauChange) {
                notifications.notifier(rdv.getPraticien(), "RDV_DEPLACE", "Rendez-vous déplacé", rdv, "Ce rendez-vous (prévu le " + ancienneDate.substring(0, 16) + ") a été déplacé", "Le cabinet");
            }
            return saved;
        } catch (DataIntegrityViolationException ex) {
            throw new CreneauIndisponibleException("Ce créneau vient d’être réservé. Choisissez un autre horaire.");
        }
    }

    @Transactional
    public void mettreEnCorbeille(Long id, String emailUtilisateur) {
        RendezVous rdv = rendezVousRepository.findByIdAndSupprimeFalse(id)
                .orElseThrow(() -> new RendezVousIntrouvableException("Rendez-vous introuvable avec l'id : " + id));
        rdv.setSupprime(true);
        rdv.setDeletedAt(LocalDateTime.now(CABINET_ZONE));
        rdv.setDeletedBy(utilisateurRepository.findByEmail(emailUtilisateur)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non authentifié.")));
        rendezVousRepository.save(rdv);
        audit.log("RDV_CORBEILLE", "RendezVous", id, rdv.getNomClient() + " — " + rdv.getDateRdv() + " " + rdv.getHeureRdv());
    }

    @Transactional
    public void restaurer(Long id) {
        RendezVous rdv = rendezVousRepository.findByIdAndSupprimeTrue(id)
                .orElseThrow(() -> new RendezVousIntrouvableException("Rendez-vous introuvable dans la corbeille : " + id));
        sortirDeLaCorbeille(rdv);
        audit.log("RDV_RESTAURE", "RendezVous", id, rdv.getNomClient());
    }

    @Transactional
    public void annulerMiseEnCorbeille(Long id, String emailUtilisateur) {
        RendezVous rdv = rendezVousRepository.findByIdAndSupprimeTrue(id)
                .orElseThrow(() -> new RendezVousIntrouvableException("Rendez-vous introuvable dans la corbeille : " + id));
        if (rdv.getDeletedBy() == null || !emailUtilisateur.equals(rdv.getDeletedBy().getEmail())) {
            throw new org.springframework.security.access.AccessDeniedException("Seul l’auteur de la suppression peut l’annuler.");
        }
        sortirDeLaCorbeille(rdv);
    }

    private void sortirDeLaCorbeille(RendezVous rdv) {
        verrouillerJour(rdv.getDateRdv());
        // Le créneau a pu être repris par un autre patient pendant que ce rendez-vous était dans la corbeille.
        if (rdv.getStatut() != StatutRDV.ANNULE) {
            try {
                verifierDisponibilite(rdv.getDateRdv(), rdv.getHeureRdv(), rdv.getPraticien() == null ? null : rdv.getPraticien().getId(), rdv.getId());
            } catch (CreneauIndisponibleException e) {
                throw new CreneauIndisponibleException("Impossible de restaurer : le créneau du " + rdv.getDateRdv()
                        + " à " + rdv.getHeureRdv() + " est désormais occupé par un autre rendez-vous.");
            }
        }
        rdv.setSupprime(false);
        rdv.setDeletedAt(null);
        rdv.setDeletedBy(null);
        try {
            rendezVousRepository.saveAndFlush(rdv);
        } catch (DataIntegrityViolationException ex) {
            throw new CreneauIndisponibleException("Impossible de restaurer : ce créneau vient d’être réservé.");
        }
    }

    @Transactional
    public void supprimerDefinitivement(Long id) {
        RendezVous rdv = rendezVousRepository.findByIdAndSupprimeTrue(id)
                .orElseThrow(() -> new RendezVousIntrouvableException("Rendez-vous introuvable dans la corbeille : " + id));
        rendezVousRepository.delete(rdv);
        audit.log("RDV_SUPPRIME_DEFINITIVEMENT", "RendezVous", id, rdv.getNomClient() + " — " + rdv.getDateRdv());
    }

    @Transactional
    public void viderCorbeille() {
        List<RendezVous> aSupprimer = rendezVousRepository.findBySupprimeTrue();
        rendezVousRepository.deleteAll(aSupprimer);
        audit.log("CORBEILLE_VIDEE", "RendezVous", null, aSupprimer.size() + " rendez-vous supprimés définitivement");
    }

    // ============ RECHERCHE POUR LE CLIENT (téléphone OU nom) ============
    /**
     * Le client retrouve ses rendez-vous avec son numéro de téléphone (tout format) ou son nom.
     *
     * Par nom : il suffit de taper un ou plusieurs mots, dans n'importe quel ordre, avec ou sans accents ni majuscules
     * (« hery », « rakoto hery », « Héry Rakoto »). Chaque mot doit correspondre au début d'un mot du nom enregistré.
     * Pour protéger les patientes, un visiteur retrouve ainsi seulement les rendez-vous à venir et ceux des 30 derniers jours
     * (l'historique complet reste accessible avec le numéro de téléphone) ; le personnel connecté voit tout.
     */
    @Transactional(readOnly = true)
    public List<RendezVousResponseDTO> rechercherPourClient(String q, boolean personnel) {
        String terme = q == null ? "" : q.trim();
        if (terme.length() < 3) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saisissez au moins 3 caractères.");
        boolean contientDesLettres = terme.codePoints().anyMatch(Character::isLetter);
        List<RendezVous> trouves;
        if (!contientDesLettres) {
            trouves = rendezVousRepository.findActiveByNormalizedTelephone(cleTelephone(terme));
        } else {
            String[] mots = TextUtils.fold(terme).split("[\\s'\\-]+");
            String plusLong = java.util.Arrays.stream(mots).max(java.util.Comparator.comparingInt(String::length)).orElse("");
            if (plusLong.length() < 2) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saisissez au moins 3 caractères.");
            LocalDate depuis = personnel ? LocalDate.of(2000, 1, 1) : LocalDate.now(CABINET_ZONE).minusDays(30);
            trouves = rendezVousRepository.findCandidatsParNom(TextUtils.ACCENTS, TextUtils.PLAIN, TextUtils.escapeLike(plusLong), depuis)
                    .stream().filter(rdv -> tousLesMotsCorrespondent(mots, rdv.getNomClient())).limit(personnel ? 100 : 20).toList();
        }
        // Le motif est une information de santé : il n'est montré qu'au personnel connecté.
        return trouves.stream().map(personnel ? this::toResponseDTO : this::toClientLookupDTO).toList();
    }

    /** Chaque mot recherché doit être le début d'un mot du nom (« rak her » trouve « Héry Rakoto »). */
    private static boolean tousLesMotsCorrespondent(String[] mots, String nom) {
        String[] motsDuNom = TextUtils.fold(nom).split("[\\s'\\-]+");
        for (String mot : mots) {
            if (mot.isEmpty()) continue;
            boolean trouve = false;
            for (String candidat : motsDuNom) if (candidat.startsWith(mot)) { trouve = true; break; }
            if (!trouve) return false;
        }
        return true;
    }

    // ============ RDV PAR TÉLÉPHONE (pour le client) ============
    @Transactional(readOnly = true)
    public List<RendezVousResponseDTO> getRendezVousParTelephone(String telephone) {
        return rendezVousRepository.findActiveByNormalizedTelephone(cleTelephone(telephone))
                .stream()
                .map(this::toClientLookupDTO)
                .toList();
    }

    /** Historique complet (motif compris) d'un patient, pour le dossier médical du personnel soignant. */
    @Transactional(readOnly = true)
    public List<RendezVousResponseDTO> getRendezVousPatient(String telephone) {
        return rendezVousRepository.findActiveByNormalizedTelephone(PhoneUtils.searchKey(telephone))
                .stream().map(this::toResponseDTO).toList();
    }

    private String cleTelephone(String telephone) {
        String chiffres = PhoneUtils.digits(telephone);
        if (chiffres.length() < PhoneUtils.MIN_DIGITS || chiffres.length() > 15) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saisissez un numéro de téléphone valide.");
        }
        return PhoneUtils.searchKey(telephone);
    }

    // The lookup is public for the existing client flow; never expose medical notes there.
    private RendezVousResponseDTO toClientLookupDTO(RendezVous rdv) {
        RendezVousResponseDTO dto = toResponseDTO(rdv);
        dto.setMotif(null);
        return dto;
    }

    // ============ RDV PAR STATUT (pour le médecin) ============
    @Transactional(readOnly = true)
    public List<RendezVousResponseDTO> getRendezVousParStatut(StatutRDV statut) {
        return rendezVousRepository.findByStatut(statut)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    // ============ CONFIRMER UN RDV (le super administrateur décide qui le prend) ============
    /**
     * Confirme un rendez-vous en attente.
     * - Super administrateur : il choisit la personne qui prendra le rendez-vous (praticienId). Elle doit être libre à ce créneau
     *   (voir OccupationService) et elle reçoit une notification.
     * - Médecin / sage-femme : peut seulement confirmer un rendez-vous qui lui a déjà été attribué.
     */
    @Transactional
    public RendezVousResponseDTO confirmerRendezVous(Long id, Long praticienId, String emailActeur) {
        RendezVous rdv = rendezVousRepository.findByIdAndSupprimeFalse(id)
                .orElseThrow(() -> new RendezVousIntrouvableException("Rendez-vous introuvable avec l'id : " + id));
        if (rdv.getStatut() != StatutRDV.EN_ATTENTE) {
            throw new StatutInvalideException("Seul un RDV en attente peut être confirmé");
        }
        Utilisateur acteur = acteur(emailActeur);
        verrouillerJour(rdv.getDateRdv());
        if (acteur.getRole() == Role.SUPER_ADMIN) {
            Long cible = praticienId != null ? praticienId : (rdv.getPraticien() == null ? null : rdv.getPraticien().getId());
            if (cible == null && !utilisateurRepository.findByRoleIn(ROLES_PRATICIENS).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choisissez la personne qui prendra ce rendez-vous.");
            }
            if (cible != null) attribuer(rdv, cible, acteur);
        } else {
            verifierQueLeRdvMEstAttribue(rdv, acteur);
        }

        rdv.setStatut(StatutRDV.CONFIRME);
        RendezVous updated = rendezVousRepository.save(rdv);
        audit.log("RDV_CONFIRME", "RendezVous", id, rdv.getNomClient() + " — " + rdv.getDateRdv() + " " + rdv.getHeureRdv()
                + (rdv.getPraticien() == null ? "" : " — avec " + rdv.getPraticien().getNom()));

        // Envoyer SMS au client (confirmation)
        String message = "Votre RDV du " + updated.getDateRdv()
                + " à " + updated.getHeureRdv()
                + " est CONFIRMÉ" + (rdv.getPraticien() == null ? "" : " avec " + rdv.getPraticien().getNom()) + ". Cabinet médical.";
        smsService.envoyerSms(updated.getTelephoneClient(), message);

        return toResponseDTO(updated);
    }

    /** Attribue (ou retire, si praticienId est null) un rendez-vous à un membre du personnel : réservé au super administrateur. */
    @Transactional
    public RendezVousResponseDTO attribuerRendezVous(Long id, Long praticienId, String emailActeur) {
        RendezVous rdv = rendezVousRepository.findByIdAndSupprimeFalse(id)
                .orElseThrow(() -> new RendezVousIntrouvableException("Rendez-vous introuvable avec l'id : " + id));
        if (rdv.getStatut() == StatutRDV.ANNULE || rdv.getStatut() == StatutRDV.TERMINE) {
            throw new StatutInvalideException("Impossible de changer la personne d'un rendez-vous annulé ou terminé");
        }
        Utilisateur acteur = acteur(emailActeur);
        verrouillerJour(rdv.getDateRdv());
        if (praticienId == null) {
            Utilisateur ancien = rdv.getPraticien();
            if (ancien != null) {
                rdv.setPraticien(null);
                notifications.notifier(ancien, "RDV_RETIRE", "Rendez-vous retiré", rdv, "Ce rendez-vous ne vous est plus attribué", acteur.getNom());
                audit.log("RDV_ATTRIBUTION_RETIREE", "RendezVous", id, rdv.getNomClient() + " — plus attribué à " + ancien.getNom());
            }
        } else {
            attribuer(rdv, praticienId, acteur);
        }
        return toResponseDTO(rendezVousRepository.save(rdv));
    }

    private static final List<Role> ROLES_PRATICIENS = List.of(Role.MEDECIN, Role.SAGE_FEMME, Role.INFIRMIERE);

    private Utilisateur acteur(String email) {
        return utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non authentifié."));
    }

    /** Un praticien n'agit que sur ses propres rendez-vous : c'est le super administrateur qui les répartit. */
    private void verifierQueLeRdvMEstAttribue(RendezVous rdv, Utilisateur acteur) {
        if (rdv.getPraticien() == null || !rdv.getPraticien().getId().equals(acteur.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Seul le super administrateur peut attribuer ce rendez-vous ; vous ne pouvez agir que sur vos propres rendez-vous.");
        }
    }

    /** Attribue le rendez-vous à une personne libre à ce créneau, et la prévient (ainsi que la personne précédente). */
    private void attribuer(RendezVous rdv, Long praticienId, Utilisateur acteur) {
        Utilisateur nouveau = resolvePractitioner(praticienId);
        Utilisateur ancien = rdv.getPraticien();
        if (ancien != null && ancien.getId().equals(nouveau.getId())) return;
        if (rendezVousRepository.praticienOccupe(nouveau.getId(), rdv.getId(), rdv.getDateRdv(), rdv.getHeureRdv())) {
            throw new CreneauIndisponibleException(nouveau.getNom() + " a déjà un rendez-vous à cet horaire. Choisissez une personne libre.");
        }
        rdv.setPraticien(nouveau);
        notifications.notifier(nouveau, "RDV_ATTRIBUE", "Nouveau rendez-vous attribué", rdv, "Un rendez-vous vous est confié", acteur.getNom());
        if (ancien != null) notifications.notifier(ancien, "RDV_RETIRE", "Rendez-vous retiré", rdv, "Ce rendez-vous ne vous est plus attribué", acteur.getNom());
        audit.log("RDV_ATTRIBUE", "RendezVous", rdv.getId(), rdv.getNomClient() + " → " + nouveau.getNom() + (ancien == null ? "" : " (avant : " + ancien.getNom() + ")"));
    }

    // ============ TERMINER UN RDV (consultation effectuée) ============
    @Transactional
    public RendezVousResponseDTO terminerRendezVous(Long id, String emailActeur) {
        RendezVous rdv = rendezVousRepository.findByIdAndSupprimeFalse(id)
                .orElseThrow(() -> new RendezVousIntrouvableException("Rendez-vous introuvable avec l'id : " + id));
        Utilisateur acteur = acteur(emailActeur);
        if (acteur.getRole() != Role.SUPER_ADMIN) verifierQueLeRdvMEstAttribue(rdv, acteur);
        if (rdv.getStatut() != StatutRDV.CONFIRME) {
            throw new StatutInvalideException("Seul un RDV confirmé peut être marqué comme terminé");
        }
        rdv.setStatut(StatutRDV.TERMINE);
        RendezVousResponseDTO saved = toResponseDTO(rendezVousRepository.save(rdv));
        audit.log("RDV_TERMINE", "RendezVous", id, rdv.getNomClient());
        return saved;
    }

    // ============ ANNULER UN RDV ============
    /**
     * @param token            jeton remis au client à la création (en-tête X-RDV-Access-Token)
     * @param utilisateurInterne true pour le personnel connecté, qui n'a pas besoin du jeton
     */
    @Transactional
    public RendezVousResponseDTO annulerRendezVous(Long id, String token, boolean utilisateurInterne) {
        RendezVous rdv = rendezVousRepository.findByIdAndSupprimeFalse(id)
                .orElseThrow(() -> new RendezVousIntrouvableException("Rendez-vous introuvable avec l'id : " + id));
        verifierAcces(rdv, token, utilisateurInterne);

        if (rdv.getStatut() == StatutRDV.ANNULE) return toResponseDTO(rdv);
        if (rdv.getStatut() == StatutRDV.TERMINE) {
            throw new StatutInvalideException("Impossible d'annuler un RDV terminé");
        }

        rdv.setStatut(StatutRDV.ANNULE);
        RendezVous updated = rendezVousRepository.save(rdv);
        audit.log("RDV_ANNULE", "RendezVous", id, rdv.getNomClient() + (utilisateurInterne ? "" : " (annulé par le patient)"));
        notifications.notifier(rdv.getPraticien(), "RDV_ANNULE", "Rendez-vous annulé", rdv, utilisateurInterne ? "Ce rendez-vous a été annulé par le cabinet" : "Le patient a annulé ce rendez-vous", utilisateurInterne ? "Le cabinet" : "Le patient");

        // Envoyer SMS au client (annulation)
        String message = "Votre RDV du " + updated.getDateRdv()
                + " à " + updated.getHeureRdv()
                + " a été ANNULÉ. Cabinet médical.";
        smsService.envoyerSms(updated.getTelephoneClient(), message);

        return toResponseDTO(updated);
    }

    // ============ CRÉNEAUX DISPONIBLES D'UNE JOURNÉE ============
    @Transactional(readOnly = true)
    public List<LocalTime> getCreneauxDisponibles(LocalDate date) {
        return getCreneauxDisponibles(date, null);
    }

    /** Créneaux encore libres : ni passés, ni complets, et — si un praticien est choisi — où ce praticien est disponible. */
    @Transactional(readOnly = true)
    public List<LocalTime> getCreneauxDisponibles(LocalDate date, Long praticienId) {
        LocalDateTime maintenant = LocalDateTime.now(CABINET_ZONE);
        if (date == null || date.isBefore(maintenant.toLocalDate())) return List.of();
        List<LocalTime> grille = planning.creneaux(date);
        if (grille.isEmpty()) return List.of();

        int capacite = planning.parametres().getCapaciteParCreneau();
        List<RendezVous> actifs = rendezVousRepository.findByDateRdvAndSupprimeFalse(date);
        Map<LocalTime, Long> occupation = new HashMap<>();
        Set<LocalTime> praticienPris = new HashSet<>();
        for (RendezVous rdv : actifs) {
            occupation.merge(rdv.getHeureRdv(), 1L, Long::sum);
            if (praticienId != null && rdv.getPraticien() != null && praticienId.equals(rdv.getPraticien().getId())) {
                praticienPris.add(rdv.getHeureRdv());
            }
        }

        List<LocalTime> disponibles = new ArrayList<>();
        for (LocalTime creneau : grille) {
            boolean passe = date.equals(maintenant.toLocalDate()) && !creneau.isAfter(maintenant.toLocalTime());
            if (!passe && occupation.getOrDefault(creneau, 0L) < capacite && !praticienPris.contains(creneau)) {
                disponibles.add(creneau);
            }
        }
        return disponibles;
    }

    @Transactional(readOnly = true)
    public RendezVous rendezVousPdfAutorise(Long id, String token, boolean utilisateurInterne) {
        RendezVous rdv = rendezVousRepository.findByIdAndSupprimeFalse(id)
                .orElseThrow(() -> new RendezVousIntrouvableException("Rendez-vous introuvable ou supprimé."));
        verifierAcces(rdv, token, utilisateurInterne);
        // Charge les relations dans la transaction : le PDF est généré après la fin de celle-ci.
        if (rdv.getService() != null) rdv.getService().getNom();
        if (rdv.getPraticien() != null) rdv.getPraticien().getNom();
        return rdv;
    }

    private void verifierAcces(RendezVous rdv, String token, boolean utilisateurInterne) {
        if (utilisateurInterne) return;
        String attendu = rdv.getClientAccessTokenHash();
        if (token == null || attendu == null || !MessageDigest.isEqual(
                attendu.getBytes(StandardCharsets.US_ASCII), hashAccessToken(token).getBytes(StandardCharsets.US_ASCII))) {
            throw new org.springframework.security.access.AccessDeniedException("Accès refusé à ce rendez-vous.");
        }
    }

    private void verrouillerJour(LocalDate date) {
        if (date != null) jdbc.queryForList("select pg_advisory_xact_lock(1001, ?)", (int) date.toEpochDay());
    }

    /** Un créneau est réservable s'il est dans la grille du planning de ce jour, et pas déjà écoulé. */
    private boolean isBookableSlot(LocalDate date, LocalTime time) {
        if (date == null || time == null) return false;
        LocalDateTime maintenant = LocalDateTime.now(CABINET_ZONE);
        if (date.isBefore(maintenant.toLocalDate())) return false;
        if (date.equals(maintenant.toLocalDate()) && !time.isAfter(maintenant.toLocalTime())) return false;
        return planning.creneaux(date).contains(time);
    }

    private String motifIndisponibilite(LocalDate date) {
        return planning.jourFerme(date)
                .map(j -> "Le cabinet est fermé ce jour-là" + (j.getMotif() == null ? "." : " (" + j.getMotif() + ")."))
                .orElse("Créneau horaire invalide, hors horaires d’ouverture ou déjà passé");
    }

    /** Capacité du créneau et disponibilité du praticien (excludeId = rendez-vous en cours de modification, 0 sinon). */
    private void verifierDisponibilite(LocalDate date, LocalTime heure, Long praticienId, Long excludeId) {
        int capacite = planning.parametres().getCapaciteParCreneau();
        long deja = rendezVousRepository.countOtherActiveAtSlot(excludeId, date, heure);
        if (deja >= capacite) throw new CreneauIndisponibleException("Ce créneau est déjà réservé");
        if (praticienId != null && rendezVousRepository.praticienOccupe(praticienId, excludeId, date, heure)) {
            throw new CreneauIndisponibleException("Ce praticien a déjà un rendez-vous à cet horaire");
        }
    }

    private void appliquerChamps(RendezVous rdv, RendezVousRequestDTO dto) {
        rdv.setNomClient(dto.getNomClient().trim());
        rdv.setAgeClient(dto.getAgeClient());
        String telephone = PhoneUtils.canonical(dto.getTelephoneClient());
        if (telephone == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le numéro de téléphone est invalide");
        rdv.setTelephoneClient(telephone);
        rdv.setMotif(dto.getMotif() == null || dto.getMotif().isBlank() ? null : dto.getMotif().trim());
        rdv.setDateRdv(dto.getDateRdv());
        rdv.setHeureRdv(dto.getHeureRdv());
        rdv.setService(resolveService(dto.getServiceId()));
        rdv.setPraticien(resolvePractitioner(dto.getPraticienId()));
    }

    private com.cabinet.cabinet.entity.Service resolveService(Long id) {
        if (id == null) return null;
        return serviceRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service introuvable."));
    }

    private Utilisateur resolvePractitioner(Long id) {
        if (id == null) return null;
        Utilisateur practitioner = utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Praticien introuvable."));
        if (!List.of(Role.MEDECIN, Role.SAGE_FEMME, Role.INFIRMIERE).contains(practitioner.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le compte sélectionné n’est pas un praticien.");
        }
        return practitioner;
    }

    private String createAccessToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String hashAccessToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 est indisponible.", impossible);
        }
    }

    // ============ CONVERSION ENTITÉ → DTO ============
    private RendezVousResponseDTO toResponseDTO(RendezVous rdv) {
        RendezVousResponseDTO dto = new RendezVousResponseDTO();
        dto.setId(rdv.getId());
        dto.setNomClient(rdv.getNomClient());
        dto.setAgeClient(rdv.getAgeClient());
        dto.setTelephoneClient(rdv.getTelephoneClient());
        dto.setMotif(rdv.getMotif());
        dto.setServiceId(rdv.getService() == null ? null : rdv.getService().getId());
        dto.setServiceNom(rdv.getService() == null ? null : rdv.getService().getNom());
        dto.setPraticienId(rdv.getPraticien() == null ? null : rdv.getPraticien().getId());
        dto.setPraticienNom(rdv.getPraticien() == null ? null : rdv.getPraticien().getNom());
        dto.setDateRdv(rdv.getDateRdv());
        dto.setHeureRdv(rdv.getHeureRdv());
        dto.setStatut(rdv.getStatut());
        dto.setDateCreation(rdv.getDateCreation());
        dto.setDeletedAt(rdv.getDeletedAt());
        return dto;
    }
}
