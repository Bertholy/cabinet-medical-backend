package com.cabinet.cabinet.service;

import com.cabinet.cabinet.dto.RendezVousResponseDTO;
import com.cabinet.cabinet.entity.ConsultationPrenatale;
import com.cabinet.cabinet.entity.DossierPatient;
import com.cabinet.cabinet.entity.Enfant;
import com.cabinet.cabinet.entity.Grossesse;
import com.cabinet.cabinet.entity.VaccinationEnfant;
import com.cabinet.cabinet.repository.ConsultationPrenataleRepository;
import com.cabinet.cabinet.repository.DossierPatientRepository;
import com.cabinet.cabinet.repository.EnfantRepository;
import com.cabinet.cabinet.repository.GrossesseRepository;
import com.cabinet.cabinet.repository.VaccinationEnfantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Suivi de la maternité : grossesse (terme, consultations prénatales, alertes), enfant (carnet de vaccination) et rappels.
 * Les calendriers ci-dessous sont des REPÈRES (modèle OMS de 4 consultations prénatales, calendrier vaccinal de l'enfant) :
 * ils aident à ne rien oublier, mais ne remplacent pas le jugement du médecin ou de la sage-femme.
 */
@SuppressWarnings("null") // avertissements de l'éditeur sur des références de méthode : aucun risque réel
@Service
@RequiredArgsConstructor
public class MaterniteService {
    private static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");

    /** Fenêtre recommandée pour une consultation prénatale (en semaines d'aménorrhée). */
    public record Fenetre(String code, String libelle, int debutSa, int finSa) { }

    public record Vaccin(String code, String libelle, String groupe, int jours) { }

    public static final List<Fenetre> CALENDRIER_CPN = List.of(
            new Fenetre("CPN1", "1re consultation prénatale", 0, 16),
            new Fenetre("CPN2", "2e consultation prénatale", 24, 28),
            new Fenetre("CPN3", "3e consultation prénatale", 30, 32),
            new Fenetre("CPN4", "4e consultation prénatale", 36, 38));

    public static final List<Vaccin> CALENDRIER_VACCINAL = List.of(
            new Vaccin("BCG", "BCG (tuberculose)", "À la naissance", 0),
            new Vaccin("VPO0", "Polio oral — dose de naissance", "À la naissance", 0),
            new Vaccin("PENTA1", "Pentavalent 1 (diphtérie, tétanos, coqueluche, hépatite B, Hib)", "6 semaines", 42),
            new Vaccin("VPO1", "Polio oral 1", "6 semaines", 42),
            new Vaccin("PCV1", "Pneumocoque 1", "6 semaines", 42),
            new Vaccin("ROTA1", "Rotavirus 1", "6 semaines", 42),
            new Vaccin("PENTA2", "Pentavalent 2", "10 semaines", 70),
            new Vaccin("VPO2", "Polio oral 2", "10 semaines", 70),
            new Vaccin("PCV2", "Pneumocoque 2", "10 semaines", 70),
            new Vaccin("ROTA2", "Rotavirus 2", "10 semaines", 70),
            new Vaccin("PENTA3", "Pentavalent 3", "14 semaines", 98),
            new Vaccin("VPO3", "Polio oral 3", "14 semaines", 98),
            new Vaccin("PCV3", "Pneumocoque 3", "14 semaines", 98),
            new Vaccin("VPI", "Polio injectable (VPI)", "14 semaines", 98),
            new Vaccin("RR1", "Rougeole-Rubéole", "9 mois", 270));

    /** Actes que l'on peut cocher lors d'une consultation prénatale. */
    public static final Set<String> ACTES = Set.of("VAT", "SP", "FER", "MILDA", "DEPISTAGE", "ECHO", "PALU");
    private static final Set<String> ALBUMINURIES = Set.of("NEG", "+", "++", "+++");

    private final GrossesseRepository grossesses;
    private final ConsultationPrenataleRepository consultations;
    private final EnfantRepository enfants;
    private final VaccinationEnfantRepository vaccinations;
    private final DossierPatientRepository dossiers;
    private final RendezVousService rendezVousService;
    private final AuditService audit;

    public static LocalDate aujourdhui() { return LocalDate.now(ZONE); }

    // ======================================================================== lecture

    @Transactional(readOnly = true)
    public Map<String, Object> vue(String telephone) {
        LocalDate jour = aujourdhui();
        List<Map<String, Object>> liste = grossesses.findByTelephoneOrderByCreeLeDescIdDesc(telephone).stream()
                .map(g -> grossesseVue(g, jour)).toList();
        List<Map<String, Object>> petits = enfants.findByMereTelephoneOrderByDateNaissanceDescIdDesc(telephone).stream()
                .map(e -> enfantVue(e, jour)).toList();
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("grossesses", liste);
        reponse.put("enfants", petits);
        reponse.put("calendrierCpn", CALENDRIER_CPN);
        return reponse;
    }

    /** Ce qui demande une action : consultations prénatales à faire ou en retard, terme proche, vaccins à faire ou en retard. */
    @Transactional(readOnly = true)
    public Map<String, Object> rappels() {
        LocalDate jour = aujourdhui();
        List<Map<String, Object>> grossessesASuivre = new ArrayList<>();
        for (Grossesse g : grossesses.findByStatut(Grossesse.EN_COURS)) {
            Map<String, Object> vue = grossesseVue(g, jour);
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("grossesseId", g.getId());
            ligne.put("telephone", g.getTelephone());
            ligne.put("nom", nomDe(g.getTelephone()));
            ligne.put("sa", vue.get("sa"));
            ligne.put("saJours", vue.get("saJours"));
            ligne.put("dpa", g.getDpa());
            ligne.put("joursRestants", vue.get("joursRestants"));
            ligne.put("trimestre", vue.get("trimestre"));
            ligne.put("prochaineEtape", vue.get("prochaineEtape"));
            ligne.put("prochainRdv", vue.get("prochainRdv"));
            ligne.put("rappels", vue.get("rappels"));
            ligne.put("risques", g.getRisques());
            grossessesASuivre.add(ligne);
        }
        grossessesASuivre.sort(Comparator.comparing((Map<String, Object> m) -> ((List<?>) m.get("rappels")).isEmpty()).thenComparing(m -> (LocalDate) m.get("dpa")));

        List<Map<String, Object>> vaccinsASuivre = new ArrayList<>();
        for (Enfant e : enfants.findByDateNaissanceAfter(jour.minusYears(2))) {
            for (Map<String, Object> v : vaccinsDe(e, jour)) {
                String statut = (String) v.get("statut");
                if (!"EN_RETARD".equals(statut) && !"A_FAIRE".equals(statut)) continue;
                Map<String, Object> ligne = new LinkedHashMap<>();
                ligne.put("enfantId", e.getId());
                ligne.put("enfantNom", e.getNom());
                ligne.put("telephone", e.getMereTelephone());
                ligne.put("mere", nomDe(e.getMereTelephone()));
                ligne.put("code", v.get("code"));
                ligne.put("vaccin", v.get("libelle"));
                ligne.put("echeance", v.get("echeance"));
                ligne.put("statut", statut);
                vaccinsASuivre.add(ligne);
            }
        }
        vaccinsASuivre.sort(Comparator.comparing(m -> (LocalDate) m.get("echeance")));
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("grossesses", grossessesASuivre);
        reponse.put("vaccins", vaccinsASuivre);
        return reponse;
    }

    // ======================================================================== grossesse

    @Transactional
    public Map<String, Object> creerGrossesse(String telephone, LocalDate ddr, LocalDate dpa, String groupe, String risques, String notes, String auteur) {
        if (grossesses.findFirstByTelephoneAndStatut(telephone, Grossesse.EN_COURS).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une grossesse est déjà en cours pour cette patiente. Terminez-la avant d'en ouvrir une nouvelle.");
        }
        LocalDate jour = aujourdhui();
        if (ddr == null && dpa == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indiquez la date des dernières règles ou la date prévue d'accouchement.");
        if (ddr != null && (ddr.isAfter(jour) || ddr.isBefore(jour.minusDays(320)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date des dernières règles est incohérente (elle doit se situer dans les 10 derniers mois).");
        }
        LocalDate terme = dpa != null ? dpa : java.util.Objects.requireNonNull(ddr).plusDays(280);
        if (terme.isBefore(jour.minusDays(60)) || terme.isAfter(jour.plusDays(310))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date prévue d'accouchement est incohérente.");
        }
        Grossesse g = new Grossesse();
        g.setTelephone(telephone);
        g.setDdr(ddr);
        g.setDpa(terme);
        appliquerInfos(g, groupe, risques, notes);
        g.setCreeLe(LocalDateTime.now(ZONE));
        g.setCreePar(auteur);
        grossesses.save(g);
        audit.log("GROSSESSE_CREEE", "Grossesse", g.getId(), "Patiente " + telephone + " — terme prévu le " + terme);
        return grossesseVue(g, jour);
    }

    @Transactional
    public Map<String, Object> modifierGrossesse(Long id, LocalDate ddr, LocalDate dpa, String groupe, String risques, String notes) {
        Grossesse g = grossesse(id);
        LocalDate jour = aujourdhui();
        if (ddr != null && (ddr.isAfter(jour) || ddr.isBefore(jour.minusDays(400)))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date des dernières règles est incohérente.");
        if (ddr != null) { g.setDdr(ddr); if (dpa == null) g.setDpa(ddr.plusDays(280)); }
        if (dpa != null) g.setDpa(dpa);
        appliquerInfos(g, groupe, risques, notes);
        grossesses.save(g);
        audit.log("GROSSESSE_MODIFIEE", "Grossesse", id, "Patiente " + g.getTelephone());
        return grossesseVue(g, jour);
    }

    @Transactional
    public Map<String, Object> terminerGrossesse(Long id, String statut, LocalDate dateFin, String issue) {
        Grossesse g = grossesse(id);
        if (!Grossesse.TERMINEE.equals(statut) && !Grossesse.INTERROMPUE.equals(statut)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut invalide.");
        LocalDate jour = aujourdhui();
        LocalDate fin = dateFin != null ? dateFin : jour;
        if (fin.isAfter(jour) || fin.isBefore(g.debutCalcul())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de fin est incohérente.");
        g.setStatut(statut);
        g.setDateFin(fin);
        g.setIssue(vide(issue));
        grossesses.save(g);
        audit.log("GROSSESSE_TERMINEE", "Grossesse", id, "Patiente " + g.getTelephone() + " — " + statut);
        return grossesseVue(g, jour);
    }

    @Transactional
    public void supprimerGrossesse(Long id) {
        Grossesse g = grossesse(id);
        consultations.supprimerDeLaGrossesse(id);
        grossesses.delete(g);
        audit.log("GROSSESSE_SUPPRIMEE", "Grossesse", id, "Patiente " + g.getTelephone());
    }

    // ======================================================================== consultations prénatales

    public record MesuresCpn(LocalDate date, BigDecimal poidsKg, Integer tensionSystolique, Integer tensionDiastolique, Integer hauteurUterineCm,
                             Integer bcf, boolean oedemes, String albuminurie, String presentation, List<String> actes, String observations, LocalDate prochainRdv) { }

    @Transactional
    public Map<String, Object> ajouterConsultation(Long grossesseId, MesuresCpn m, String auteur) {
        Grossesse g = grossesse(grossesseId);
        LocalDate jour = aujourdhui();
        LocalDate date = m.date() != null ? m.date() : jour;
        if (date.isAfter(jour)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de la consultation ne peut pas être dans le futur.");
        if (date.isBefore(g.debutCalcul())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de la consultation précède le début de la grossesse.");
        borne(m.poidsKg() == null ? null : m.poidsKg().intValue(), 20, 200, "Le poids");
        borne(m.tensionSystolique(), 50, 260, "La tension systolique");
        borne(m.tensionDiastolique(), 30, 160, "La tension diastolique");
        borne(m.hauteurUterineCm(), 0, 60, "La hauteur utérine");
        borne(m.bcf(), 60, 220, "Les bruits du cœur fœtal");
        String albu = m.albuminurie() == null || m.albuminurie().isBlank() ? null : m.albuminurie().trim().toUpperCase();
        if (albu != null && !ALBUMINURIES.contains(albu)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Albuminurie invalide (NEG, +, ++ ou +++).");
        Set<String> actes = new TreeSet<>();
        if (m.actes() != null) for (String a : m.actes()) {
            String code = a == null ? "" : a.trim().toUpperCase();
            if (!ACTES.contains(code)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Acte inconnu : " + a);
            actes.add(code);
        }
        if (m.prochainRdv() != null && m.prochainRdv().isBefore(date)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le prochain rendez-vous doit être après la consultation.");

        ConsultationPrenatale c = new ConsultationPrenatale();
        c.setGrossesse(g);
        c.setDateConsultation(date);
        c.setTermeSa((int) (ChronoUnit.DAYS.between(g.debutCalcul(), date) / 7));
        c.setPoidsKg(m.poidsKg());
        c.setTensionSystolique(m.tensionSystolique());
        c.setTensionDiastolique(m.tensionDiastolique());
        c.setHauteurUterineCm(m.hauteurUterineCm());
        c.setBcf(m.bcf());
        c.setOedemes(m.oedemes());
        c.setAlbuminurie(albu);
        c.setPresentation(vide(m.presentation()));
        c.setActes(String.join(",", actes));
        c.setObservations(vide(m.observations()));
        c.setProchainRdv(m.prochainRdv());
        c.setAuteurNom(auteur);
        c.setCreeLe(LocalDateTime.now(ZONE));
        consultations.save(c);
        audit.log("CPN_AJOUTEE", "Grossesse", grossesseId, "Patiente " + g.getTelephone() + " — " + c.getTermeSa() + " SA");
        return consultationVue(c);
    }

    @Transactional
    public void supprimerConsultation(Long id) {
        ConsultationPrenatale c = consultations.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation introuvable."));
        consultations.delete(c);
        audit.log("CPN_SUPPRIMEE", "Grossesse", c.getGrossesse().getId(), "Consultation du " + c.getDateConsultation());
    }

    // ======================================================================== enfants et vaccins

    @Transactional
    public Map<String, Object> creerEnfant(String telephoneMere, String nom, String sexe, LocalDate naissance, Integer poidsG, Long grossesseId, String notes) {
        LocalDate jour = aujourdhui();
        if (nom == null || nom.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom de l'enfant est obligatoire.");
        if (naissance == null || naissance.isAfter(jour) || naissance.isBefore(jour.minusYears(10))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de naissance est incohérente.");
        String s = sexe == null || sexe.isBlank() ? null : sexe.trim().toUpperCase();
        if (s != null && !s.equals("F") && !s.equals("M")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sexe invalide (F ou M).");
        borne(poidsG, 300, 7000, "Le poids de naissance");
        if (grossesseId != null && !grossesse(grossesseId).getTelephone().equals(telephoneMere)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette grossesse n'appartient pas à cette patiente.");
        Enfant e = new Enfant();
        e.setMereTelephone(telephoneMere);
        e.setNom(nom.trim());
        e.setSexe(s);
        e.setDateNaissance(naissance);
        e.setPoidsNaissanceG(poidsG);
        e.setGrossesseId(grossesseId);
        e.setNotes(vide(notes));
        e.setCreeLe(LocalDateTime.now(ZONE));
        enfants.save(e);
        audit.log("ENFANT_AJOUTE", "Enfant", e.getId(), "Mère " + telephoneMere);
        return enfantVue(e, jour);
    }

    @Transactional
    public void supprimerEnfant(Long id) {
        Enfant e = enfant(id);
        vaccinations.supprimerDeLEnfant(id);
        enfants.delete(e);
        audit.log("ENFANT_SUPPRIME", "Enfant", id, "Mère " + e.getMereTelephone());
    }

    @Transactional
    public Map<String, Object> enregistrerVaccin(Long enfantId, String code, LocalDate dateFaite, String lot, String auteur) {
        Enfant e = enfant(enfantId);
        String c = code == null ? "" : code.trim().toUpperCase();
        if (CALENDRIER_VACCINAL.stream().noneMatch(v -> v.code().equals(c))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vaccin inconnu : " + code);
        LocalDate jour = aujourdhui();
        LocalDate date = dateFaite != null ? dateFaite : jour;
        if (date.isAfter(jour) || date.isBefore(e.getDateNaissance())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date du vaccin est incohérente (après la naissance, pas dans le futur).");
        VaccinationEnfant v = vaccinations.findByEnfantIdAndCode(enfantId, c).orElseGet(VaccinationEnfant::new);
        v.setEnfant(e);
        v.setCode(c);
        v.setDateFaite(date);
        v.setLot(vide(lot));
        v.setAuteurNom(auteur);
        vaccinations.save(v);
        audit.log("VACCIN_ENREGISTRE", "Enfant", enfantId, c + " le " + date);
        return enfantVue(e, jour);
    }

    @Transactional
    public Map<String, Object> annulerVaccin(Long enfantId, String code) {
        Enfant e = enfant(enfantId);
        String c = code == null ? "" : code.trim().toUpperCase();
        vaccinations.findByEnfantIdAndCode(enfantId, c).ifPresent(v -> {
            vaccinations.delete(v);
            audit.log("VACCIN_ANNULE", "Enfant", enfantId, c);
        });
        return enfantVue(e, aujourdhui());
    }

    // ======================================================================== calculs et vues

    private Map<String, Object> grossesseVue(Grossesse g, LocalDate jour) {
        boolean enCours = Grossesse.EN_COURS.equals(g.getStatut());
        LocalDate reference = enCours ? jour : (g.getDateFin() != null ? g.getDateFin() : jour);
        long saJours = Math.max(0, ChronoUnit.DAYS.between(g.debutCalcul(), reference));
        int sa = (int) (saJours / 7);
        List<ConsultationPrenatale> cpn = consultations.findByGrossesseIdOrderByDateConsultationAscIdAsc(g.getId());
        List<Map<String, Object>> calendrier = calendrierCpn(g, cpn, sa, enCours);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("telephone", g.getTelephone());
        m.put("ddr", g.getDdr());
        m.put("dpa", g.getDpa());
        m.put("groupeSanguin", g.getGroupeSanguin());
        m.put("risques", g.getRisques());
        m.put("notes", g.getNotes());
        m.put("statut", g.getStatut());
        m.put("dateFin", g.getDateFin());
        m.put("issue", g.getIssue());
        m.put("creeLe", g.getCreeLe());
        m.put("creePar", g.getCreePar());
        m.put("sa", sa);
        m.put("saJours", saJours % 7);
        m.put("trimestre", sa < 14 ? 1 : (sa < 28 ? 2 : 3));
        m.put("joursRestants", enCours ? ChronoUnit.DAYS.between(jour, g.getDpa()) : null);
        m.put("calendrier", calendrier);
        m.put("consultations", cpn.stream().map(this::consultationVue).toList());
        if (enCours) {
            m.put("prochaineEtape", prochaineEtape(calendrier));
            m.put("prochainRdv", cpn.stream().map(ConsultationPrenatale::getProchainRdv).filter(d -> d != null && !d.isBefore(jour)).min(Comparator.naturalOrder()).orElse(null));
            m.put("rappels", rappelsDe(g, sa, calendrier, jour));
        }
        return m;
    }

    private List<Map<String, Object>> calendrierCpn(Grossesse g, List<ConsultationPrenatale> cpn, int saActuel, boolean enCours) {
        List<Map<String, Object>> etapes = new ArrayList<>();
        for (Fenetre f : CALENDRIER_CPN) {
            Optional<ConsultationPrenatale> faite = cpn.stream().filter(c -> c.getTermeSa() >= f.debutSa() && c.getTermeSa() <= f.finSa()).findFirst();
            String etat;
            if (faite.isPresent()) etat = "FAIT";
            else if (saActuel > f.finSa()) etat = enCours ? "EN_RETARD" : "MANQUE";
            else if (saActuel >= f.debutSa()) etat = "A_FAIRE";
            else etat = "A_VENIR";
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("code", f.code());
            e.put("libelle", f.libelle());
            e.put("debutSa", f.debutSa());
            e.put("finSa", f.finSa());
            e.put("debut", g.debutCalcul().plusWeeks(f.debutSa()));
            e.put("fin", g.debutCalcul().plusWeeks(f.finSa() + 1L).minusDays(1));
            e.put("etat", etat);
            e.put("consultationId", faite.map(ConsultationPrenatale::getId).orElse(null));
            etapes.add(e);
        }
        return etapes;
    }

    private Map<String, Object> prochaineEtape(List<Map<String, Object>> calendrier) {
        for (String etat : List.of("EN_RETARD", "A_FAIRE", "A_VENIR")) {
            for (Map<String, Object> e : calendrier) if (etat.equals(e.get("etat"))) return e;
        }
        return null;
    }

    private List<String> rappelsDe(Grossesse g, int sa, List<Map<String, Object>> calendrier, LocalDate jour) {
        List<String> r = new ArrayList<>();
        long restants = ChronoUnit.DAYS.between(jour, g.getDpa());
        if (restants < 0) r.add("Terme dépassé de " + (-restants) + " jour(s)");
        else if (restants <= 14) r.add("Accouchement prévu dans " + restants + " jour(s)");
        else if (restants <= 30) r.add("Terme dans moins d'un mois");
        for (Map<String, Object> e : calendrier) {
            if ("EN_RETARD".equals(e.get("etat"))) r.add(e.get("libelle") + " non faite (prévue entre " + e.get("debutSa") + " et " + e.get("finSa") + " SA)");
            else if ("A_FAIRE".equals(e.get("etat"))) r.add(e.get("libelle") + " à faire maintenant (jusqu'à " + e.get("finSa") + " SA)");
        }
        if (g.getRisques() != null && !g.getRisques().isBlank()) r.add("Grossesse à surveiller : " + g.getRisques());
        return r;
    }

    private Map<String, Object> consultationVue(ConsultationPrenatale c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("date", c.getDateConsultation());
        m.put("termeSa", c.getTermeSa());
        m.put("poidsKg", c.getPoidsKg());
        m.put("tensionSystolique", c.getTensionSystolique());
        m.put("tensionDiastolique", c.getTensionDiastolique());
        m.put("hauteurUterineCm", c.getHauteurUterineCm());
        m.put("bcf", c.getBcf());
        m.put("oedemes", c.isOedemes());
        m.put("albuminurie", c.getAlbuminurie());
        m.put("presentation", c.getPresentation());
        m.put("actes", c.getActes() == null || c.getActes().isBlank() ? List.of() : List.of(c.getActes().split(",")));
        m.put("observations", c.getObservations());
        m.put("prochainRdv", c.getProchainRdv());
        m.put("auteur", c.getAuteurNom());
        m.put("alertes", alertes(c));
        return m;
    }

    /** Valeurs hors normes à signaler (aide à la décision : ne remplace pas l'avis du soignant). */
    static List<String> alertes(ConsultationPrenatale c) {
        List<String> a = new ArrayList<>();
        Integer sys = c.getTensionSystolique(), dia = c.getTensionDiastolique();
        if ((sys != null && sys >= 140) || (dia != null && dia >= 90)) a.add("Tension élevée (≥ 140/90) : risque de pré-éclampsie, à surveiller");
        if ((sys != null && sys < 90) || (dia != null && dia < 50)) a.add("Tension basse");
        if (c.getBcf() != null && (c.getBcf() < 110 || c.getBcf() > 160)) a.add("Bruits du cœur fœtal hors de 110–160 bpm");
        if (c.getAlbuminurie() != null && !c.getAlbuminurie().equals("NEG")) a.add("Albuminurie positive (" + c.getAlbuminurie() + ")");
        if (c.isOedemes() && ((sys != null && sys >= 140) || (dia != null && dia >= 90))) a.add("Œdèmes avec tension élevée : évaluer sans attendre");
        return a;
    }

    private Map<String, Object> enfantVue(Enfant e, LocalDate jour) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("nom", e.getNom());
        m.put("sexe", e.getSexe());
        m.put("dateNaissance", e.getDateNaissance());
        m.put("poidsNaissanceG", e.getPoidsNaissanceG());
        m.put("grossesseId", e.getGrossesseId());
        m.put("notes", e.getNotes());
        m.put("ageJours", ChronoUnit.DAYS.between(e.getDateNaissance(), jour));
        m.put("vaccins", vaccinsDe(e, jour));
        return m;
    }

    private List<Map<String, Object>> vaccinsDe(Enfant e, LocalDate jour) {
        Map<String, VaccinationEnfant> faits = vaccinations.findByEnfantId(e.getId()).stream().collect(Collectors.toMap(VaccinationEnfant::getCode, v -> v, (a, b) -> a));
        List<Map<String, Object>> liste = new ArrayList<>();
        for (Vaccin v : CALENDRIER_VACCINAL) {
            LocalDate echeance = e.getDateNaissance().plusDays(v.jours());
            VaccinationEnfant fait = faits.get(v.code());
            String statut = fait != null ? "FAIT" : (echeance.isBefore(jour) ? "EN_RETARD" : (!echeance.isAfter(jour.plusDays(14)) ? "A_FAIRE" : "A_VENIR"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", v.code());
            m.put("libelle", v.libelle());
            m.put("groupe", v.groupe());
            m.put("echeance", echeance);
            m.put("statut", statut);
            m.put("dateFaite", fait == null ? null : fait.getDateFaite());
            m.put("lot", fait == null ? null : fait.getLot());
            m.put("auteur", fait == null ? null : fait.getAuteurNom());
            liste.add(m);
        }
        return liste;
    }

    // ======================================================================== outils

    private String nomDe(String telephone) {
        Optional<DossierPatient> dossier = dossiers.findByTelephone(telephone);
        if (dossier.isPresent() && dossier.get().getNom() != null) return dossier.get().getNom();
        return rendezVousService.getRendezVousPatient(telephone).stream().findFirst().map(RendezVousResponseDTO::getNomClient).orElse(null);
    }

    private Grossesse grossesse(Long id) {
        return grossesses.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grossesse introuvable."));
    }

    private Enfant enfant(Long id) {
        return enfants.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enfant introuvable."));
    }

    private static void appliquerInfos(Grossesse g, String groupe, String risques, String notes) {
        String gs = groupe == null || groupe.isBlank() ? null : groupe.trim().toUpperCase();
        if (gs != null && !Set.of("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-").contains(gs)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Groupe sanguin invalide (ex. O+, A-).");
        g.setGroupeSanguin(gs);
        g.setRisques(vide(risques));
        g.setNotes(vide(notes));
    }

    private static void borne(Integer valeur, int min, int max, String nom) {
        if (valeur != null && (valeur < min || valeur > max)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, nom + " doit être compris entre " + min + " et " + max + ".");
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }
}
