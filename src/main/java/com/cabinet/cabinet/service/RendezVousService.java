package com.cabinet.cabinet.service;

import com.cabinet.cabinet.dto.RendezVousRequestDTO;
import com.cabinet.cabinet.dto.RendezVousResponseDTO;
import com.cabinet.cabinet.dto.SansRendezVousRequestDTO;
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
import com.cabinet.cabinet.util.Civilite;
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

@SuppressWarnings("null") // avertissements de l'éditeur sur des références de méthode : aucun risque réel
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
    private final com.cabinet.cabinet.security.PermissionService permissions;
    private final JdbcTemplate jdbc;

    /** Un même nom ne peut pas faire deux réservations en ligne à moins de N jours d'intervalle (0 = règle désactivée). */
    @org.springframework.beans.factory.annotation.Value("${app.reservation.delai-jours:7}")
    private int delaiEntreReservationsJours;

    /** Un patient ne peut plus annuler son rendez-vous dans les N heures qui précèdent (0 = règle désactivée). */
    @org.springframework.beans.factory.annotation.Value("${app.reservation.annulation-min-heures:2}")
    private int delaiAnnulationHeures;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ZoneId CABINET_ZONE = ZoneId.of("Indian/Antananarivo");

    // ============ NUMÉRO DU MÉDECIN (notification de chaque nouveau rendez-vous) ============
    @org.springframework.beans.factory.annotation.Value("${app.notification.numero-medecin:0343498888}")
    private String numeroMedecin;

    // ============ CRÉER UN RDV ============
    /**
     * Règle de la réservation en ligne : un nom OU un numéro de téléphone qui a déjà une réservation active (non annulée) faite il y a moins de 7 jours
     * ne peut pas en faire une seconde avant la fin de ce délai ; annuler la première la libère. Les noms sont comparés sans tenir compte des majuscules,
     * des accents, de l'ordre des mots ni des espaces en trop (« rakoto  Jean » = « Jean Rakoto »), les numéros quel que soit leur format
     * (« 034 12 345 67 » = « 0341234567 » = « 34 12 345 67 »). Le personnel n'est pas concerné (voir le contrôleur).
     */
    @Transactional(readOnly = true)
    public void verifierDelaiEntreReservations(String nomClient, String telephoneClient) {
        if (delaiEntreReservationsJours <= 0) return;
        LocalDateTime maintenant = LocalDateTime.now(CABINET_ZONE);
        String cleNom = nomClient == null ? "" : cleDeNom(nomClient);
        String cleTelephone = PhoneUtils.searchKey(telephoneClient);
        if (cleNom.isEmpty() && cleTelephone.isEmpty()) return;
        for (RendezVous existant : rendezVousRepository.findReservationsActivesDepuis(maintenant.minusDays(delaiEntreReservationsJours))) {
            boolean memeNom = !cleNom.isEmpty() && cleNom.equals(cleDeNom(existant.getNomClient()));
            boolean memeTelephone = !cleTelephone.isEmpty() && cleTelephone.equals(PhoneUtils.searchKey(existant.getTelephoneClient()));
            if (!memeNom && !memeTelephone) continue;
            LocalDateTime disponibleLe = existant.getDateCreation().plusDays(delaiEntreReservationsJours);
            String rdvExistant = "(rendez-vous du " + existant.getDateRdv().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " à "
                    + existant.getHeureRdv().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + ")";
            String fin = " Une nouvelle réservation sera possible à partir du "
                    + disponibleLe.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm")) + ", ou dès que la première est annulée.";
            throw new com.cabinet.cabinet.exception.ReservationRecenteException(
                    memeNom ? "Une réservation existe déjà au nom de « " + existant.getNomClient().trim() + " » " + rdvExistant + "." + fin
                            : "Une réservation existe déjà avec ce numéro de téléphone " + rdvExistant + "." + fin,
                    disponibleLe, existant.getDateRdv(), existant.getHeureRdv(), memeNom ? "NOM" : "TELEPHONE");
        }
    }

    /** Nom ramené à une forme comparable : minuscules, sans accents ni ponctuation, mots triés. */
    static String cleDeNom(String nom) {
        String sansAccents = java.text.Normalizer.normalize(nom == null ? "" : nom, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String[] mots = sansAccents.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").trim().split("\\s+");
        java.util.Arrays.sort(mots);
        return String.join(" ", mots).trim();
    }

    /** Réservation faite par un patient (site public) : les super administrateurs sont prévenus pour attribuer le rendez-vous. */
    @Transactional
    public RendezVousResponseDTO creerRendezVous(RendezVousRequestDTO dto) {
        return creerRendezVous(dto, "Le patient", false);
    }

    /**
     * @param origine        qui a créé le rendez-vous (« Le patient », « Le cabinet »), affiché dans la notification
     * @param parSuperAdmin  true si le super administrateur l'a lui-même saisi : il n'a pas besoin d'être prévenu
     */
    @Transactional
    public RendezVousResponseDTO creerRendezVous(RendezVousRequestDTO dto, String origine, boolean parSuperAdmin) {
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
        rdv.setCleSuivi(genererCleSuivi());
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
        // Chaque super administrateur reçoit une notification : un clic ouvre ce rendez-vous sur le tableau de bord, où l'on choisit la personne.
        if (!parSuperAdmin) {
            prevenirLaReception(saved, "RDV_NOUVEAU", "Nouvelle demande de rendez-vous", saved.getPraticien() == null ? "Nouvelle demande à attribuer" : "Nouvelle demande", origine, null);
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
        return avecHistorique(rendezVousRepository.findBySupprimeFalse()
                .stream()
                .map(this::toResponseDTO)
                .toList());
    }

    /** Ajoute à chaque rendez-vous : « nouveau » ou « ancien » client, rang du passage et nombre total de passages (même nom et même numéro, voir ClientHistorique). */
    private List<RendezVousResponseDTO> avecHistorique(List<RendezVousResponseDTO> liste) {
        com.cabinet.cabinet.util.ClientHistorique.Index index = new com.cabinet.cabinet.util.ClientHistorique.Index();
        for (Object[] p : rendezVousRepository.findPassages()) index.ajouter((String) p[0], (String) p[1], (LocalDate) p[2], (LocalTime) p[3], (Long) p[4]);
        for (RendezVousResponseDTO dto : liste) {
            int avant = index.passagesAvant(dto.getNomClient(), dto.getTelephoneClient(), dto.getDateRdv(), dto.getHeureRdv(), dto.getId());
            dto.setNouveauClient(avant == 0);
            dto.setPassage(avant + 1);
            dto.setPassagesTotal(Math.max(index.passages(dto.getNomClient(), dto.getTelephoneClient()), avant));
        }
        return liste;
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
        return avecHistorique(result.stream().map(this::toResponseDTO).toList());
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
        return modifierRendezVous(id, dto, peutAttribuer, "Le cabinet");
    }

    /** @param origine qui modifie (affiché dans la notification envoyée aux super administrateurs quand ce n'est pas l'un d'eux) */
    @Transactional
    public RendezVousResponseDTO modifierRendezVous(Long id, RendezVousRequestDTO dto, boolean peutAttribuer, String origine) {
        return modifierRendezVous(id, dto, peutAttribuer, origine, peutAttribuer);
    }

    /**
     * « parSuperAdmin » : la modification vient d'un super administrateur (il a déjà vu la sienne). Distinct de « peutAttribuer » : une secrétaire
     * peut avoir le droit d'attribuer sans être super administrateur, et le super administrateur doit alors être prévenu de ce qu'elle modifie.
     */
    @Transactional
    public RendezVousResponseDTO modifierRendezVous(Long id, RendezVousRequestDTO dto, boolean peutAttribuer, String origine, boolean parSuperAdmin) {
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
        if (praticienChange || (creneauChange && rdv.getStatut() == StatutRDV.EN_ATTENTE)) rdv.setPersonnelConfirmeLe(null);
        try {
            RendezVousResponseDTO saved = toResponseDTO(rendezVousRepository.saveAndFlush(rdv));
            audit.log("RDV_MODIFIE", "RendezVous", id, rdv.getNomClient() + " — " + rdv.getDateRdv() + " " + rdv.getHeureRdv());
            if (praticienChange) {
                notifications.notifier(rdv.getPraticien(), "RDV_ATTRIBUE", "Nouveau rendez-vous attribué", rdv, "Un rendez-vous vous est confié", "Le super administrateur");
                notifications.notifier(ancienPraticienEntite, "RDV_RETIRE", "Rendez-vous retiré", rdv, "Ce rendez-vous ne vous est plus attribué", "Le super administrateur");
            } else if (creneauChange) {
                notifications.notifier(rdv.getPraticien(), "RDV_DEPLACE", "Rendez-vous déplacé", rdv, "Ce rendez-vous (prévu le " + ancienneDate.substring(0, 16) + ") a été déplacé", "Le cabinet");
            }
            // le super administrateur est prévenu des modifications faites par les autres (il a déjà vu les siennes)
            if (!parSuperAdmin) {
                prevenirLaReception(rdv, "RDV_MODIFIE", "Rendez-vous modifié", creneauChange ? "Rendez-vous déplacé (il était prévu le " + ancienneDate.substring(0, 16) + ")" : "Rendez-vous modifié", origine, null);
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
        Utilisateur acteur = utilisateurRepository.findByEmail(emailUtilisateur)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non authentifié."));
        rdv.setSupprime(true);
        rdv.setDeletedAt(LocalDateTime.now(CABINET_ZONE));
        rdv.setDeletedBy(acteur);
        rendezVousRepository.save(rdv);
        if (acteur.getRole() != Role.SUPER_ADMIN) {
            prevenirLaReception(rdv, "RDV_CORBEILLE", "Rendez-vous mis à la corbeille", "Rendez-vous mis à la corbeille (récupérable depuis la corbeille)", NotificationService.origine(acteur), "/corbeille");
        }
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

    // ============ CLÉ DE SUIVI : la seule façon pour un patient de retrouver son rendez-vous ============
    private static final String ALPHABET_CLE = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // sans I, L, O, 0, 1 : pas de confusion à la lecture
    private static final int MAX_CLES_FAUSSES = 10;
    private static final java.time.Duration FENETRE_CLES_FAUSSES = java.time.Duration.ofMinutes(15);
    private final Map<String, java.util.Deque<java.time.Instant>> clesFaussesParAdresse = new java.util.concurrent.ConcurrentHashMap<>();

    /** Nouvelle clé « CF-XXXX-XXXX » (8 caractères tirés au hasard : impossible à deviner) qu'aucun autre rendez-vous n'utilise. */
    private String genererCleSuivi() {
        String cle;
        do {
            StringBuilder lettres = new StringBuilder(8);
            for (int i = 0; i < 8; i++) lettres.append(ALPHABET_CLE.charAt(SECURE_RANDOM.nextInt(ALPHABET_CLE.length())));
            cle = "CF-" + lettres.substring(0, 4) + "-" + lettres.substring(4);
        } while (rendezVousRepository.existsByCleSuivi(cle));
        return cle;
    }

    /** Forme canonique d'une clé saisie (majuscules, avec ou sans tirets, espaces ou préfixe « CF ») ; null si elle ne peut pas être une clé. */
    static String normaliserCle(String saisie) {
        String s = saisie == null ? "" : saisie.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (s.length() == 10 && s.startsWith("CF")) s = s.substring(2);
        if (s.length() != 8) return null;
        for (char c : s.toCharArray()) if (ALPHABET_CLE.indexOf(c) < 0) return null;
        return "CF-" + s.substring(0, 4) + "-" + s.substring(4);
    }

    /** Donne une clé aux rendez-vous créés avant son introduction (le personnel peut la lire dans le tableau de bord). */
    @Transactional
    public int attribuerClesManquantes() {
        List<RendezVous> sansCle = rendezVousRepository.findByCleSuiviIsNull();
        for (RendezVous rdv : sansCle) {
            rdv.setCleSuivi(genererCleSuivi());
            rendezVousRepository.saveAndFlush(rdv);
        }
        return sansCle.size();
    }

    /**
     * Page publique « Mes RDV » : le patient saisit sa clé, jamais son nom ni son numéro (qui permettraient de fouiller dans les rendez-vous des autres).
     * Les essais ratés sont comptés par adresse : au-delà de 10 en 15 minutes, la recherche est suspendue.
     * Le motif (information de santé) n'est pas renvoyé.
     */
    @Transactional(readOnly = true)
    public RendezVousResponseDTO retrouverParCle(String saisie, String adresse) {
        String origine = adresse == null ? "?" : adresse;
        java.util.Deque<java.time.Instant> echecs = clesFaussesParAdresse.computeIfAbsent(origine, k -> new java.util.ArrayDeque<>());
        synchronized (echecs) {
            java.time.Instant limite = java.time.Instant.now().minus(FENETRE_CLES_FAUSSES);
            while (!echecs.isEmpty() && echecs.peekFirst().isBefore(limite)) echecs.pollFirst();
            if (echecs.size() >= MAX_CLES_FAUSSES) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Trop d'essais avec une clé incorrecte. Réessayez dans quelques minutes.");
            }
        }
        String cle = normaliserCle(saisie);
        RendezVous trouve = cle == null ? null : rendezVousRepository.findActifParCleSuivi(cle).orElse(null);
        if (trouve == null) {
            synchronized (echecs) { echecs.addLast(java.time.Instant.now()); }
            if (clesFaussesParAdresse.size() > 5000) clesFaussesParAdresse.clear(); // garde-fou mémoire
            throw new RendezVousIntrouvableException("Aucun rendez-vous ne correspond à cette clé. Vérifiez-la (elle ressemble à CF-7K3M-9QX2).");
        }
        return toClientLookupDTO(trouve);
    }

    // ============ RECHERCHE DU PERSONNEL (téléphone OU nom) ============
    /**
     * Réservée au personnel connecté : les patients passent par leur clé de suivi (retrouverParCle).
     * Numéro de téléphone (tout format) ou nom : un ou plusieurs mots, dans n'importe quel ordre, avec ou sans accents ni majuscules
     * (« hery », « rakoto hery », « Héry Rakoto »). Chaque mot doit correspondre au début d'un mot du nom enregistré.
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
        dto.setPersonnelConfirme(false);
        dto.setPersonnelConfirmeLe(null);
        if (rdv.getStatut() != StatutRDV.CONFIRME && rdv.getStatut() != StatutRDV.TERMINE) {
            dto.setPraticienId(null);
            dto.setPraticienNom(null);
        }
        return dto;
    }

    // ============ RDV PAR STATUT (pour le médecin) ============
    @Transactional(readOnly = true)
    public List<RendezVousResponseDTO> getRendezVousParStatut(StatutRDV statut) {
        return avecHistorique(rendezVousRepository.findByStatut(statut)
                .stream()
                .map(this::toResponseDTO)
                .toList());
    }

    // ============ « MES PATIENTS » : les rendez-vous confiés à la personne connectée ============
    /** Rendez-vous attribués à la personne connectée (des 6 derniers mois à l'avenir) ; elle voit aussi ceux qu'elle n'a pas encore acceptés. */
    @Transactional(readOnly = true)
    public List<RendezVousResponseDTO> getMesPatients(String emailActeur) {
        Utilisateur acteur = acteur(emailActeur);
        return avecHistorique(rendezVousRepository.findAttribuesA(acteur.getId(), LocalDate.now(CABINET_ZONE).minusMonths(6)).stream().map(this::toResponseDTO).toList());
    }

    /** Un rendez-vous précis (page ouverte depuis une notification). Un praticien ne voit que les siens ou ceux qui ne sont pas encore attribués. */
    @Transactional(readOnly = true)
    public RendezVousResponseDTO getRendezVousPourPersonnel(Long id, String emailActeur) {
        RendezVous rdv = rendezVousRepository.findByIdAndSupprimeFalse(id)
                .orElseThrow(() -> new RendezVousIntrouvableException("Rendez-vous introuvable avec l'id : " + id));
        Utilisateur acteur = acteur(emailActeur);
        boolean praticien = ROLES_PRATICIENS.contains(acteur.getRole());
        if (praticien && rdv.getPraticien() != null && !rdv.getPraticien().getId().equals(acteur.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Ce rendez-vous est confié à une autre personne.");
        }
        return avecHistorique(List.of(toResponseDTO(rdv))).get(0);   // « nouveau » ou « ancien » patient, affiché sur la demande à traiter
    }

    // ============ CONFIRMER UN RDV : deux confirmations ============
    /**
     * Étape 1 — le super administrateur attribue le rendez-vous à une personne (voir attribuerRendezVous) ; elle est notifiée.
     * Étape 2 — cette personne l'accepte (accepterRendezVous) ; le super administrateur est notifié.
     * Étape 3 — le super administrateur confirme le rendez-vous (cette méthode) : le patient reçoit son SMS.
     */
    @Transactional
    public RendezVousResponseDTO confirmerRendezVous(Long id, String emailActeur) {
        RendezVous rdv = rendezVousRepository.findByIdAndSupprimeFalse(id)
                .orElseThrow(() -> new RendezVousIntrouvableException("Rendez-vous introuvable avec l'id : " + id));
        if (rdv.getStatut() != StatutRDV.EN_ATTENTE) {
            throw new StatutInvalideException("Seul un rendez-vous en cours peut être confirmé");
        }
        Utilisateur acteur = acteur(emailActeur);
        if (!permissions.de(acteur).contains(com.cabinet.cabinet.security.Permissions.RDV_CONFIRMER)) {
            throw new org.springframework.security.access.AccessDeniedException("Vous n'avez pas le droit de confirmer définitivement un rendez-vous.");
        }
        // le personnel soignant ne confirme que ses propres rendez-vous (sauf s'il a aussi le droit d'attribuer) ; la personne attribuée qui confirme accepte du même coup
        if (ROLES_PRATICIENS.contains(acteur.getRole()) && !permissions.de(acteur).contains(com.cabinet.cabinet.security.Permissions.RDV_ATTRIBUER)) verifierQueLeRdvMEstAttribue(rdv, acteur);
        if (rdv.getPraticien() != null && rdv.getPersonnelConfirmeLe() == null && rdv.getPraticien().getId().equals(acteur.getId())) {
            rdv.setPersonnelConfirmeLe(LocalDateTime.now(CABINET_ZONE));
            audit.log("RDV_ACCEPTE_PAR_PERSONNEL", "RendezVous", id, rdv.getNomClient() + " — accepté par " + acteur.getNom() + " en le confirmant");
        }
        verrouillerJour(rdv.getDateRdv());
        if (rdv.getPraticien() == null) {
            if (!utilisateurRepository.findByRoleIn(ROLES_PRATICIENS).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attribuez d'abord ce rendez-vous à une personne du personnel.");
            }
        } else if (rdv.getPersonnelConfirmeLe() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, rdv.getPraticien().getNom()
                    + " n'a pas encore accepté ce rendez-vous. Attendez sa confirmation ou choisissez une autre personne.");
        }

        rdv.setStatut(StatutRDV.CONFIRME);
        RendezVous updated = rendezVousRepository.save(rdv);
        audit.log("RDV_CONFIRME", "RendezVous", id, rdv.getNomClient() + " — " + rdv.getDateRdv() + " " + rdv.getHeureRdv()
                + (rdv.getPraticien() == null ? "" : " — avec " + rdv.getPraticien().getNom()));
        // la personne attribuée est prévenue que le cabinet a confirmé, sauf si c'est elle qui vient de le confirmer
        if (rdv.getPraticien() != null && !rdv.getPraticien().getId().equals(acteur.getId())) {
            notifications.notifier(rdv.getPraticien(), "RDV_CONFIRME", "Rendez-vous confirmé", rdv, "Le cabinet a confirmé ce rendez-vous", acteur.getNom());
        }

        // Envoyer SMS au client (confirmation)
        String message = "Votre RDV du " + updated.getDateRdv()
                + " à " + updated.getHeureRdv()
                + " est CONFIRMÉ" + (rdv.getPraticien() == null ? "" : " avec " + rdv.getPraticien().getNom()) + ". Cabinet médical.";
        smsService.envoyerSms(updated.getTelephoneClient(), message);

        return toResponseDTO(updated);
    }

    /**
     * La personne à qui le rendez-vous a été attribué l'accepte : la réception est prévenue pour le confirmer au patient (super administrateurs et personnes
     * qui ont le droit de confirmer sans être du personnel soignant, la secrétaire par défaut). Si elle le confirme elle-même, elle l'accepte du même coup
     * et personne n'est prévenu (voir confirmerRendezVous).
     */
    @Transactional
    public RendezVousResponseDTO accepterRendezVous(Long id, String emailActeur) {
        RendezVous rdv = rendezVousRepository.findByIdAndSupprimeFalse(id)
                .orElseThrow(() -> new RendezVousIntrouvableException("Rendez-vous introuvable avec l'id : " + id));
        Utilisateur acteur = acteur(emailActeur);
        verifierQueLeRdvMEstAttribue(rdv, acteur);
        if (rdv.getStatut() != StatutRDV.EN_ATTENTE && rdv.getStatut() != StatutRDV.CONFIRME) {
            throw new StatutInvalideException("Ce rendez-vous ne peut plus être accepté");
        }
        if (rdv.getPersonnelConfirmeLe() != null) return toResponseDTO(rdv);
        rdv.setPersonnelConfirmeLe(LocalDateTime.now(CABINET_ZONE));
        RendezVousResponseDTO saved = toResponseDTO(rendezVousRepository.save(rdv));
        audit.log("RDV_ACCEPTE_PAR_PERSONNEL", "RendezVous", id, rdv.getNomClient() + " — accepté par " + acteur.getNom());
        for (Utilisateur destinataire : receptionQuiConfirme(acteur)) {
            String lien = destinataire.getRole() == Role.SECRETAIRE ? "/espace/secretariat?rdv=" + rdv.getId() : null;
            notifications.notifier(destinataire, "RDV_ACCEPTE", "Attribution acceptée", rdv, acteur.getNom() + " a accepté ce rendez-vous : à confirmer au patient", acteur.getNom(), lien);
        }
        return saved;
    }

    /**
     * La personne à qui le rendez-vous a été attribué le décline : il n'est plus attribué à personne (son créneau est libéré pour elle), et la réception
     * (super administrateurs et personnes qui ont le droit d'attribuer sans être du personnel soignant, la secrétaire par défaut) est prévenue pour le confier à quelqu'un d'autre.
     */
    @Transactional
    public RendezVousResponseDTO declinerRendezVous(Long id, String emailActeur, String motif) {
        RendezVous rdv = rendezVousRepository.findByIdAndSupprimeFalse(id)
                .orElseThrow(() -> new RendezVousIntrouvableException("Rendez-vous introuvable avec l'id : " + id));
        Utilisateur acteur = acteur(emailActeur);
        verifierQueLeRdvMEstAttribue(rdv, acteur);
        if (rdv.getStatut() != StatutRDV.EN_ATTENTE) {
            throw new StatutInvalideException("Seul un rendez-vous en cours peut être décliné : celui-ci est " + rdv.getStatut().name().toLowerCase());
        }
        String raison = motif == null || motif.isBlank() ? null : motif.trim();
        rdv.setPraticien(null);
        rdv.setPersonnelConfirmeLe(null);
        rdv.setDeclinePar(acteur.getNom());
        rdv.setDeclineLe(LocalDateTime.now(CABINET_ZONE));
        rdv.setDeclineMotif(raison);
        RendezVousResponseDTO saved = toResponseDTO(rendezVousRepository.save(rdv));
        audit.log("RDV_DECLINE_PAR_PERSONNEL", "RendezVous", id, rdv.getNomClient() + " — décliné par " + acteur.getNom() + (raison == null ? "" : " (" + raison + ")"));
        for (Utilisateur destinataire : receptionQuiAttribue(acteur)) {
            String lien = destinataire.getRole() == Role.SECRETAIRE ? "/espace/secretariat?rdv=" + rdv.getId() : null;
            notifications.notifier(destinataire, "RDV_DECLINE", "Rendez-vous décliné", rdv,
                    acteur.getNom() + " a décliné ce rendez-vous" + (raison == null ? "" : " (" + raison + ")") + " : à confier à une autre personne", acteur.getNom(), lien);
        }
        return saved;
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
                rdv.setPersonnelConfirmeLe(null);
                notifications.notifier(ancien, "RDV_RETIRE", "Rendez-vous retiré", rdv, "Ce rendez-vous ne vous est plus attribué", acteur.getNom());
                audit.log("RDV_ATTRIBUTION_RETIREE", "RendezVous", id, rdv.getNomClient() + " — plus attribué à " + ancien.getNom());
            }
        } else {
            attribuer(rdv, praticienId, acteur);
        }
        return toResponseDTO(rendezVousRepository.save(rdv));
    }

    /**
     * Chaque super administrateur reçoit la notification, ainsi que chaque personne qui a le droit « Être prévenu des nouveaux rendez-vous » (la secrétaire par défaut) :
     * elle suit ainsi tout ce qui arrive sur les rendez-vous, quelle que soit la personne qui agit, et peut attribuer une demande à une personne libre.
     * La personne qui agit n'est pas prévenue de ce qu'elle vient de faire elle-même.
     */
    private void prevenirLaReception(RendezVous rdv, String type, String titre, String phrase, String origine, String lien) {
        String acteur = emailActeurCourant();
        for (Utilisateur destinataire : destinatairesDesRendezVous(acteur)) {
            // la secrétaire ouvre la demande sur son tableau de bord (c'est là qu'elle l'attribue) ; les autres suivent le lien habituel
            String lienPropre = lien != null ? lien : (destinataire.getRole() == Role.SECRETAIRE ? "/espace/secretariat?rdv=" + rdv.getId() : null);
            notifications.notifier(destinataire, type, titre, rdv, phrase, origine, lienPropre);
        }
    }

    /** Ceux qui confirment les rendez-vous pour le cabinet : les super administrateurs, et les personnes qui ont le droit de confirmer sans être du personnel soignant (la secrétaire). L'acteur n'en fait pas partie. */
    private List<Utilisateur> receptionQuiConfirme(Utilisateur acteur) {
        java.util.Map<Long, Utilisateur> destinataires = new java.util.LinkedHashMap<>();
        for (Utilisateur admin : utilisateurRepository.findByRoleIn(List.of(Role.SUPER_ADMIN))) destinataires.put(admin.getId(), admin);
        for (Utilisateur u : utilisateurRepository.findByRoleIn(com.cabinet.cabinet.security.PermissionService.ROLES_REGLABLES)) {
            if (u.getId().equals(acteur.getId()) || ROLES_PRATICIENS.contains(u.getRole())) continue;
            if (permissions.de(u).contains(com.cabinet.cabinet.security.Permissions.RDV_CONFIRMER)) destinataires.put(u.getId(), u);
        }
        return new java.util.ArrayList<>(destinataires.values());
    }

    /** Ceux qui confient les rendez-vous au personnel : les super administrateurs, et les personnes qui ont le droit d'attribuer sans être du personnel soignant (la secrétaire). L'acteur n'en fait pas partie. */
    private List<Utilisateur> receptionQuiAttribue(Utilisateur acteur) {
        java.util.Map<Long, Utilisateur> destinataires = new java.util.LinkedHashMap<>();
        for (Utilisateur admin : utilisateurRepository.findByRoleIn(List.of(Role.SUPER_ADMIN))) destinataires.put(admin.getId(), admin);
        for (Utilisateur u : utilisateurRepository.findByRoleIn(com.cabinet.cabinet.security.PermissionService.ROLES_REGLABLES)) {
            if (u.getId().equals(acteur.getId()) || ROLES_PRATICIENS.contains(u.getRole())) continue;
            if (permissions.de(u).contains(com.cabinet.cabinet.security.Permissions.RDV_ATTRIBUER)) destinataires.put(u.getId(), u);
        }
        return new java.util.ArrayList<>(destinataires.values());
    }

    private List<Utilisateur> destinatairesDesRendezVous(String emailActeur) {
        java.util.Map<Long, Utilisateur> destinataires = new java.util.LinkedHashMap<>();
        for (Utilisateur admin : utilisateurRepository.findByRoleIn(List.of(Role.SUPER_ADMIN))) destinataires.put(admin.getId(), admin);
        for (Utilisateur u : utilisateurRepository.findByRoleIn(com.cabinet.cabinet.security.PermissionService.ROLES_REGLABLES)) {
            if (emailActeur != null && emailActeur.equalsIgnoreCase(u.getEmail())) continue;
            if (permissions.de(u).contains(com.cabinet.cabinet.security.Permissions.RDV_NOTIFIER)) destinataires.put(u.getId(), u);
        }
        return new java.util.ArrayList<>(destinataires.values());
    }

    /** Adresse e-mail de la personne connectée qui effectue l'action en cours (null pour un visiteur du site public). */
    private static String emailActeurCourant() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) return null;
        return auth.getName();
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
        rdv.setPersonnelConfirmeLe(null);
        rdv.setDeclinePar(null); rdv.setDeclineLe(null); rdv.setDeclineMotif(null);   // le refus précédent est réglé
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

    /** Le personnel connecté doit avoir le droit « Annuler un rendez-vous » ; le personnel soignant n'annule que ses propres rendez-vous (sauf s'il peut aussi attribuer). */
    private void verifierDroitAnnulation(RendezVous rdv) {
        String email = emailActeurCourant();
        Utilisateur acteur = email == null ? null : utilisateurRepository.findByEmail(email).orElse(null);
        if (acteur == null) return;
        java.util.Set<String> droits = permissions.de(acteur);
        if (!droits.contains(com.cabinet.cabinet.security.Permissions.RDV_ANNULER)) {
            throw new org.springframework.security.access.AccessDeniedException("Vous n'avez pas le droit d'annuler un rendez-vous.");
        }
        if (ROLES_PRATICIENS.contains(acteur.getRole()) && !droits.contains(com.cabinet.cabinet.security.Permissions.RDV_ATTRIBUER)
                && (rdv.getPraticien() == null || !rdv.getPraticien().getId().equals(acteur.getId()))) {
            throw new org.springframework.security.access.AccessDeniedException("Vous ne pouvez annuler que vos propres rendez-vous.");
        }
    }

    // ============ ANNULER UN RDV ============
    /**
     * @param token            jeton remis au client à la création (en-tête X-RDV-Access-Token)
     * @param utilisateurInterne true pour le personnel connecté, qui n'a pas besoin du jeton
     */
    @Transactional
    public RendezVousResponseDTO annulerRendezVous(Long id, String token, boolean utilisateurInterne) {
        return annulerRendezVous(id, token, utilisateurInterne, utilisateurInterne ? "Le cabinet" : "Le patient", false);
    }

    /** @param origine qui annule ; @param parSuperAdmin true si c'est un super administrateur (il n'a pas besoin d'être prévenu) */
    @Transactional
    public RendezVousResponseDTO annulerRendezVous(Long id, String token, boolean utilisateurInterne, String origine, boolean parSuperAdmin) {
        RendezVous rdv = rendezVousRepository.findByIdAndSupprimeFalse(id)
                .orElseThrow(() -> new RendezVousIntrouvableException("Rendez-vous introuvable avec l'id : " + id));
        verifierAcces(rdv, token, utilisateurInterne);
        if (utilisateurInterne) verifierDroitAnnulation(rdv);

        if (rdv.getStatut() == StatutRDV.ANNULE) return toResponseDTO(rdv);
        if (rdv.getStatut() == StatutRDV.TERMINE) {
            throw new StatutInvalideException("Impossible d'annuler un RDV terminé");
        }
        // Le patient ne peut plus annuler lui-même dans les dernières heures : le créneau ne serait plus rempli. Le personnel n'est pas concerné.
        if (!utilisateurInterne && delaiAnnulationHeures > 0
                && !LocalDateTime.now(CABINET_ZONE).isBefore(rdv.getDateRdv().atTime(rdv.getHeureRdv()).minusHours(delaiAnnulationHeures))) {
            throw new StatutInvalideException("Annulation impossible à moins de " + delaiAnnulationHeures
                    + " heures du rendez-vous. Contactez le cabinet pour l'annuler.");
        }

        rdv.setStatut(StatutRDV.ANNULE);
        RendezVous updated = rendezVousRepository.save(rdv);
        audit.log("RDV_ANNULE", "RendezVous", id, rdv.getNomClient() + (utilisateurInterne ? "" : " (annulé par le patient)"));
        notifications.notifier(rdv.getPraticien(), "RDV_ANNULE", "Rendez-vous annulé", rdv, utilisateurInterne ? "Ce rendez-vous a été annulé par le cabinet" : "Le patient a annulé ce rendez-vous", utilisateurInterne ? "Le cabinet" : "Le patient");
        if (!parSuperAdmin) {
            prevenirLaReception(rdv, "RDV_ANNULE", "Rendez-vous annulé", utilisateurInterne ? "Rendez-vous annulé par l'équipe" : "Le patient a annulé son rendez-vous", origine, null);
        }

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
        // Le patient prouve qu'il est bien le titulaire avec la clé de suivi (ou, pour un ancien rendez-vous, avec le jeton remis à la création).
        String cle = normaliserCle(token);
        if (cle != null && rdv.getCleSuivi() != null && MessageDigest.isEqual(
                rdv.getCleSuivi().getBytes(StandardCharsets.US_ASCII), cle.getBytes(StandardCharsets.US_ASCII))) return;
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
        if (dto.getSexe() != null) rdv.setSexe(Civilite.normaliserSexe(dto.getSexe()));   // absent = inchangé ; vide = non précisé
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

    // ============ PATIENT SANS RENDEZ-VOUS (consultation immédiate) ============

    /** Un rendez-vous occupe son praticien du début du créneau jusqu'à la fin de sa durée (en minutes depuis minuit : aucun passage de minuit à gérer). */
    private boolean occupePraticienMaintenant(RendezVous r, Long praticienId, int minuteMaintenant, int dureeMinutes) {
        if (r.getPraticien() == null || !r.getPraticien().getId().equals(praticienId)) return false;
        if (r.getStatut() != StatutRDV.EN_ATTENTE && r.getStatut() != StatutRDV.CONFIRME) return false;
        int debut = r.getHeureRdv().getHour() * 60 + r.getHeureRdv().getMinute();
        return debut <= minuteMaintenant && minuteMaintenant < debut + dureeMinutes;
    }

    /**
     * Qui est libre en ce moment ? Pour chaque personne du personnel : libre ou non, jusqu'à quand elle est prise, et l'heure de son prochain rendez-vous.
     * Sert à la secrétaire quand un patient se présente sans rendez-vous.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> praticiensLibresMaintenant() {
        LocalDateTime maintenant = LocalDateTime.now(CABINET_ZONE);
        LocalDate jour = maintenant.toLocalDate();
        int minuteMaintenant = maintenant.getHour() * 60 + maintenant.getMinute();
        int duree = planning.parametres().getDureeCreneauMinutes();
        List<RendezVous> actifs = rendezVousRepository.findByDateRdvAndSupprimeFalse(jour);

        List<Map<String, Object>> personnel = new ArrayList<>();
        for (Utilisateur u : utilisateurRepository.findByRoleIn(ROLES_PRATICIENS).stream()
                .sorted(java.util.Comparator.comparing(Utilisateur::getNom, String.CASE_INSENSITIVE_ORDER)).toList()) {
            RendezVous enCours = actifs.stream().filter(r -> occupePraticienMaintenant(r, u.getId(), minuteMaintenant, duree)).findFirst().orElse(null);
            LocalTime prochain = actifs.stream()
                    .filter(r -> r.getPraticien() != null && r.getPraticien().getId().equals(u.getId()))
                    .filter(r -> r.getStatut() == StatutRDV.EN_ATTENTE || r.getStatut() == StatutRDV.CONFIRME)
                    .map(RendezVous::getHeureRdv)
                    .filter(h -> h.getHour() * 60 + h.getMinute() > minuteMaintenant)
                    .min(java.util.Comparator.naturalOrder()).orElse(null);
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("id", u.getId());
            ligne.put("nom", u.getNom());
            ligne.put("role", u.getRole());
            ligne.put("photoUrl", u.getPhotoUrl());
            ligne.put("libre", enCours == null);
            ligne.put("occupeJusqua", enCours == null ? null : enCours.getHeureRdv().plusMinutes(duree).toString().substring(0, 5));
            ligne.put("prochainRdv", prochain == null ? null : prochain.toString().substring(0, 5));
            personnel.add(ligne);
        }

        List<LocalTime> creneaux = planning.creneaux(jour);
        boolean ouvert = !creneaux.isEmpty()
                && minuteMaintenant >= creneaux.get(0).getHour() * 60 + creneaux.get(0).getMinute()
                && minuteMaintenant < creneaux.get(creneaux.size() - 1).getHour() * 60 + creneaux.get(creneaux.size() - 1).getMinute() + duree;

        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("heure", String.format("%02d:%02d", maintenant.getHour(), maintenant.getMinute()));
        corps.put("ouvert", ouvert);
        corps.put("dureeCreneauMinutes", duree);
        corps.put("praticiens", personnel);
        return corps;
    }

    /**
     * Reçoit tout de suite un patient venu sans rendez-vous : le rendez-vous est créé pour maintenant, avec une personne libre, déjà accepté et confirmé
     * (le patient est devant le comptoir : pas de double confirmation ni de SMS). La personne et les super administrateurs sont prévenus.
     */
    @Transactional
    public RendezVousResponseDTO recevoirSansRendezVous(SansRendezVousRequestDTO dto, String emailActeur) {
        Utilisateur acteur = acteur(emailActeur);
        LocalDateTime maintenant = LocalDateTime.now(CABINET_ZONE);
        LocalDate jour = maintenant.toLocalDate();
        LocalTime heure = maintenant.toLocalTime().withSecond(0).withNano(0);
        verrouillerJour(jour);

        Utilisateur praticien = resolvePractitioner(dto.getPraticienId());
        int minuteMaintenant = heure.getHour() * 60 + heure.getMinute();
        int duree = planning.parametres().getDureeCreneauMinutes();
        if (rendezVousRepository.findByDateRdvAndSupprimeFalse(jour).stream().anyMatch(r -> occupePraticienMaintenant(r, praticien.getId(), minuteMaintenant, duree))) {
            throw new CreneauIndisponibleException(praticien.getNom() + " n'est pas libre en ce moment. Choisissez une autre personne.");
        }

        RendezVous rdv = new RendezVous();
        rdv.setNomClient(dto.getNomClient().trim());
        rdv.setAgeClient(dto.getAgeClient());
        rdv.setSexe(Civilite.normaliserSexe(dto.getSexe()));
        // Un enfant n'a pas de téléphone : le contact est celui de son parent ou responsable (nom et numéro obligatoires).
        // Pour un adulte : son numéro ; en urgence, à défaut, celui de la personne qui l'accompagne.
        boolean enfant = dto.getAgeClient() != null && dto.getAgeClient() < Civilite.AGE_ADULTE;
        if (enfant && (dto.getAccompagnantNom() == null || dto.getAccompagnantNom().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indiquez le nom du parent ou du responsable de l'enfant.");
        }
        String numeroSaisi = enfant ? dto.getAccompagnantTelephone()
                : dto.getTelephoneClient() != null && !dto.getTelephoneClient().isBlank() ? dto.getTelephoneClient()
                : (dto.isUrgence() ? dto.getAccompagnantTelephone() : null);
        if (numeroSaisi == null || numeroSaisi.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, enfant
                ? "Un enfant n'a pas de téléphone : indiquez celui de son parent ou de son responsable."
                : dto.isUrgence() ? "Indiquez le téléphone du patient ou celui de la personne qui l'accompagne." : "Le téléphone est obligatoire");
        String telephone = PhoneUtils.canonical(numeroSaisi);
        if (telephone == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le numéro de téléphone est invalide");
        rdv.setTelephoneClient(telephone);
        rdv.setUrgence(dto.isUrgence());
        rdv.setAccompagnantNom(dto.getAccompagnantNom() == null || dto.getAccompagnantNom().isBlank() ? null : dto.getAccompagnantNom().trim());
        String telAccompagnant = dto.getAccompagnantTelephone() == null || dto.getAccompagnantTelephone().isBlank() ? null : PhoneUtils.canonical(dto.getAccompagnantTelephone());
        if (dto.getAccompagnantTelephone() != null && !dto.getAccompagnantTelephone().isBlank() && telAccompagnant == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le numéro de l'accompagnant est invalide");
        }
        rdv.setAccompagnantTelephone(telAccompagnant);
        rdv.setAccompagnantLien(dto.getAccompagnantLien() == null || dto.getAccompagnantLien().isBlank() ? null : dto.getAccompagnantLien().trim());
        rdv.setAccompagnantSexe(Civilite.normaliserSexe(dto.getAccompagnantSexe()));
        rdv.setMotif(dto.getMotif() == null || dto.getMotif().isBlank() ? null : dto.getMotif().trim());
        rdv.setDateRdv(jour);
        rdv.setHeureRdv(heure);
        // un ou plusieurs services : le premier est le service principal, les autres sont gardés par leur nom
        java.util.List<Long> idsServices = new java.util.ArrayList<>();
        if (dto.getServiceIds() != null) dto.getServiceIds().stream().filter(java.util.Objects::nonNull).distinct().forEach(idsServices::add);
        else if (dto.getServiceId() != null) idsServices.add(dto.getServiceId());
        if (!idsServices.isEmpty()) {
            rdv.setService(resolveService(idsServices.get(0)));
            String autres = idsServices.stream().skip(1).map(this::resolveService).map(com.cabinet.cabinet.entity.Service::getNom).collect(java.util.stream.Collectors.joining("|"));
            rdv.setAutresServices(autres.isEmpty() ? null : (autres.length() > 500 ? autres.substring(0, 500) : autres));
        } else {
            rdv.setService(resolveService(dto.getServiceId()));
        }
        rdv.setPraticien(praticien);
        rdv.setSansRendezVous(true);
        rdv.setStatut(StatutRDV.CONFIRME);
        rdv.setPersonnelConfirmeLe(maintenant);
        rdv.setRappelEnvoye(true);   // le patient est déjà là : aucun rappel à lui envoyer
        rdv.setClientAccessTokenHash(hashAccessToken(createAccessToken()));
        rdv.setCleSuivi(genererCleSuivi());

        RendezVous saved;
        try {
            saved = rendezVousRepository.saveAndFlush(rdv);
        } catch (DataIntegrityViolationException ex) {
            throw new CreneauIndisponibleException("Cette personne vient d'être prise. Choisissez-en une autre.");
        }
        audit.log("RDV_SANS_RDV", "RendezVous", saved.getId(), saved.getNomClient() + " — reçu sans rendez-vous" + (dto.isUrgence() ? " EN URGENCE" : "") + " par " + praticien.getNom() + " (saisi par " + acteur.getNom() + ")"
                + (saved.getAccompagnantNom() == null ? "" : " — accompagné par " + saved.getAccompagnantNom()));
        notifications.notifier(praticien, "RDV_ATTRIBUE", dto.isUrgence() ? "URGENCE — patient sans rendez-vous" : "Patient sans rendez-vous", saved,
                dto.isUrgence() ? "Urgence : un patient vous attend, consultation immédiate" : "Un patient vous attend : consultation immédiate", acteur.getNom());
        if (acteur.getRole() != Role.SUPER_ADMIN) {
            prevenirLaReception(saved, "RDV_CONFIRME", dto.isUrgence() ? "URGENCE reçue sans rendez-vous" : "Patient reçu sans rendez-vous", "Reçu par " + praticien.getNom(), acteur.getNom(), null);
        }
        return toResponseDTO(saved);
    }

    // ============ CONVERSION ENTITÉ → DTO ============
    private RendezVousResponseDTO toResponseDTO(RendezVous rdv) {
        RendezVousResponseDTO dto = new RendezVousResponseDTO();
        dto.setId(rdv.getId());
        dto.setNomClient(rdv.getNomClient());
        dto.setAgeClient(rdv.getAgeClient());
        dto.setSexe(rdv.getSexe());
        dto.setCivilite(Civilite.de(rdv.getAgeClient(), rdv.getSexe()));
        dto.setTelephoneClient(rdv.getTelephoneClient());
        dto.setMotif(rdv.getMotif());
        dto.setServiceId(rdv.getService() == null ? null : rdv.getService().getId());
        dto.setServiceNom(rdv.getService() == null ? null : rdv.getService().getNom());
        java.util.List<String> servicesNoms = new java.util.ArrayList<>();
        if (rdv.getService() != null) servicesNoms.add(rdv.getService().getNom());
        if (rdv.getAutresServices() != null && !rdv.getAutresServices().isBlank()) {
            for (String nom : rdv.getAutresServices().split("\\|")) if (!nom.isBlank()) servicesNoms.add(nom.trim());
        }
        dto.setServicesNoms(servicesNoms);
        dto.setPraticienId(rdv.getPraticien() == null ? null : rdv.getPraticien().getId());
        dto.setPraticienNom(rdv.getPraticien() == null ? null : rdv.getPraticien().getNom());
        dto.setCleSuivi(rdv.getCleSuivi());
        if (rdv.getPraticien() == null && rdv.getDeclineLe() != null) {
            dto.setDeclinePar(rdv.getDeclinePar());
            dto.setDeclineLe(rdv.getDeclineLe());
            dto.setDeclineMotif(rdv.getDeclineMotif());
        }
        dto.setPersonnelConfirme(rdv.getPersonnelConfirmeLe() != null);
        dto.setPersonnelConfirmeLe(rdv.getPersonnelConfirmeLe());
        dto.setDateRdv(rdv.getDateRdv());
        dto.setHeureRdv(rdv.getHeureRdv());
        dto.setStatut(rdv.getStatut());
        dto.setDateCreation(rdv.getDateCreation());
        dto.setDeletedAt(rdv.getDeletedAt());
        dto.setSansRendezVous(Boolean.TRUE.equals(rdv.getSansRendezVous()));
        dto.setUrgence(Boolean.TRUE.equals(rdv.getUrgence()));
        dto.setAccompagnantNom(rdv.getAccompagnantNom());
        dto.setAccompagnantTelephone(rdv.getAccompagnantTelephone());
        dto.setAccompagnantLien(rdv.getAccompagnantLien());
        dto.setAccompagnantSexe(rdv.getAccompagnantSexe());
        dto.setAccompagnantCivilite(rdv.getAccompagnantNom() == null ? null : Civilite.de(Civilite.AGE_ADULTE, rdv.getAccompagnantSexe()));
        return dto;
    }
}
