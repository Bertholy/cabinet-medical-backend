package com.cabinet.cabinet.service;

import com.cabinet.cabinet.dto.GardeDTO;
import com.cabinet.cabinet.dto.GardeRequestDTO;
import com.cabinet.cabinet.dto.RestaurationGardeRequestDTO;
import com.cabinet.cabinet.dto.TourGardeRequestDTO;
import com.cabinet.cabinet.entity.Garde;
import com.cabinet.cabinet.entity.TourGardeModele;
import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.enums.Role;
import com.cabinet.cabinet.repository.GardeRepository;
import com.cabinet.cabinet.repository.TourGardeModeleRepository;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Tour de garde du personnel soignant (le cabinet n'a pas de médecin : toute l'équipe est « sage-femme » pour la garde).
 * Une seule personne est de garde chaque jour, jour et nuit, à tour de rôle selon le nombre de personnes de l'équipe ;
 * l'administration peut modifier une garde, générer un autre tour, ou rétablir le tour automatique.
 */
@Service
@RequiredArgsConstructor
public class GardeService {
    private static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");
    private static final long JOURS_MAX = 93;
    private static final DateTimeFormatter JOUR_LONG = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);
    /** Les comptes qui peuvent être de garde : le personnel soignant (pas la secrétaire ni le super administrateur). */
    private static final Set<Role> ROLES_DE_GARDE = EnumSet.of(Role.SAGE_FEMME, Role.MEDECIN, Role.INFIRMIERE);
    private static final String LIBELLE_ROLE = "Sage-femme";
    /** Période d'un tour : une personne de garde le jour et la nuit (24 h), ou seulement le jour, ou seulement la nuit. */
    public static final String JOUR_ET_NUIT = "JOUR_NUIT";

    private final GardeRepository gardes;
    private final TourGardeModeleRepository modeles;
    private final UtilisateurRepository utilisateurs;
    private final NotificationService notifications;
    private final AuditService audit;

    public static LocalDate aujourdhui() { return LocalDate.now(ZONE); }

    @Transactional(readOnly = true)
    public List<GardeDTO> lister(LocalDate du, LocalDate au) {
        if (au.isBefore(du)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de fin précède la date de début");
        if (ChronoUnit.DAYS.between(du, au) > JOURS_MAX) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choisissez une période de " + JOURS_MAX + " jours au plus");
        return gardes.findByDateBetweenOrderByDateAscPeriodeAscIdAsc(du, au).stream().map(this::versDto).toList();
    }

    /** Les périodes (« JOUR », « NUIT ») pendant lesquelles cette personne est de garde ce jour-là ; vide si elle ne l'est pas. */
    @Transactional(readOnly = true)
    public List<String> mesPeriodes(String email, LocalDate jour) {
        if (email == null) return List.of();
        return gardes.findByDateBetweenOrderByDateAscPeriodeAscIdAsc(jour, jour).stream()
                .filter(g -> email.equalsIgnoreCase(g.getUtilisateur().getEmail()))
                .map(Garde::getPeriode).toList();
    }

    /** L'équipe qui peut être de garde, dans un ordre stable (celui de la création des comptes). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> personnel() {
        return equipe().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("nom", u.getNom());
            m.put("role", u.getRole().name());
            m.put("roleLibelle", LIBELLE_ROLE);
            return m;
        }).toList();
    }

    private List<Utilisateur> equipe() {
        return utilisateurs.findByRoleIn(ROLES_DE_GARDE).stream().sorted(Comparator.comparing(Utilisateur::getId)).toList();
    }

    @Transactional
    public GardeDTO ajouter(GardeRequestDTO dto, String acteur) {
        Utilisateur personne = personne(dto.utilisateurId());
        if (gardes.existsByDateAndPeriodeAndUtilisateurId(dto.date(), dto.periode(), personne.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, personne.getNom() + " est déjà de garde ce jour-là (" + libellePeriode(dto.periode()) + ").");
        }
        Garde garde = nouvelleGarde(dto.date(), dto.periode(), personne);
        garde.setNote(dto.note() == null || dto.note().isBlank() ? null : dto.note().trim());
        garde = gardes.save(garde);
        audit.log("GARDE_AJOUTEE", "Garde", garde.getId(), personne.getNom() + " de garde le " + dto.date() + " (" + libellePeriode(dto.periode()) + ")");
        prevenir(personne, acteur, "Vous êtes de garde le " + JOUR_LONG.format(dto.date()) + " (" + libellePeriode(dto.periode()) + ").");
        return versDto(garde);
    }

    @Transactional
    public void supprimer(Long id) {
        Garde garde = gardes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Garde introuvable"));
        audit.log("GARDE_RETIREE", "Garde", id, garde.getUtilisateur().getNom() + " n'est plus de garde le " + garde.getDate() + " (" + libellePeriode(garde.getPeriode()) + ")");
        gardes.delete(garde);
    }

    /** Tour à tour : le jour n, la personne n modulo le nombre de personnes choisies. Retourne le nombre de jours couverts et de gardes remplacées. */
    @Transactional
    public Map<String, Object> genererTour(TourGardeRequestDTO dto, String acteur) {
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(dto.utilisateurIds()));
        List<Utilisateur> choisies = ids.stream().map(this::personne).toList();
        Map<String, Object> resultat = appliquer(dto.debut(), dto.nombreJours(), dto.periode(), choisies, dto.debut(), "GARDE_TOUR", Boolean.TRUE.equals(dto.remplacer()), acteur, "Tour de garde");
        // ce tour devient le tour de référence que suit la « génération auto »
        TourGardeModele modele = modeles.findById(TourGardeModele.ID_UNIQUE).orElseGet(TourGardeModele::new);
        modele.setId(TourGardeModele.ID_UNIQUE);
        modele.setOrdre(String.join(",", choisies.stream().map(u -> String.valueOf(u.getId())).toList()));
        modele.setDebut(dto.debut());
        modele.setPeriode(dto.periode());
        modeles.save(modele);
        return resultat;
    }

    /** Remise à zéro : efface toutes les gardes et le tour de référence, comme à l'installation. Retourne le nombre de gardes effacées. */
    @Transactional
    public Map<String, Object> reinitialiser() {
        long supprimees = gardes.count();
        gardes.deleteAll();
        gardes.flush();
        boolean tourEfface = modeles.existsById(TourGardeModele.ID_UNIQUE);
        modeles.deleteAll();
        audit.log("GARDE_REINITIALISE", "Garde", null, "Tour de garde remis à zéro : " + supprimees + " garde(s) effacée(s)" + (tourEfface ? " et tour de référence supprimé" : ""));
        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("supprimees", supprimees);
        resultat.put("tourEfface", tourEfface);
        return resultat;
    }

    /** Le tour de référence proposé par l'administration (ordre des personnes, date de départ, période), ou null s'il n'y en a pas encore. */
    @Transactional(readOnly = true)
    public Map<String, Object> modele() {
        Optional<TourGardeModele> m = modeles.findById(TourGardeModele.ID_UNIQUE);
        if (m.isEmpty()) return null;
        List<Utilisateur> equipe = equipeDuModele(m.get());
        if (equipe.isEmpty()) return null;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("debut", m.get().getDebut());
        r.put("periode", m.get().getPeriode());
        r.put("ids", equipe.stream().map(Utilisateur::getId).toList());
        r.put("noms", equipe.stream().map(Utilisateur::getNom).toList());
        return r;
    }

    /** Les personnes du tour de référence, dans l'ordre, sans celles qui n'existent plus ou ne sont plus de l'équipe soignante. */
    private List<Utilisateur> equipeDuModele(TourGardeModele m) {
        List<Utilisateur> equipe = new ArrayList<>();
        for (String morceau : m.getOrdre().split(",")) {
            if (morceau.isBlank()) continue;
            utilisateurs.findById(Long.valueOf(morceau.trim())).filter(u -> ROLES_DE_GARDE.contains(u.getRole())).ifPresent(equipe::add);
        }
        return equipe;
    }

    /**
     * Génération automatique : suit le tour de référence proposé par l'administration (ordre des personnes et date de départ), en le prolongeant
     * jour après jour. Sans tour de référence, une seule personne est de garde par jour (jour et nuit), à tour de rôle sur toute l'équipe, selon la date.
     * Relancer donne toujours le même résultat ; les gardes déjà enregistrées sur ces jours sont remplacées.
     */
    @Transactional
    public Map<String, Object> genererAutomatiquement(RestaurationGardeRequestDTO dto, String acteur) {
        Optional<TourGardeModele> modele = modeles.findById(TourGardeModele.ID_UNIQUE);
        List<Utilisateur> equipeModele = modele.map(this::equipeDuModele).orElse(List.of());
        Map<String, Object> resultat;
        if (!equipeModele.isEmpty()) {
            resultat = appliquer(dto.debut(), dto.nombreJours(), modele.get().getPeriode(), equipeModele, modele.get().getDebut(), "GARDE_TOUR_AUTO", true, acteur, "Génération automatique (tour de l'administration)");
            resultat.put("source", "tour");
        } else {
            List<Utilisateur> equipe = equipe();
            if (equipe.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune sage-femme n'est enregistrée : créez d'abord les comptes de l'équipe.");
            resultat = appliquer(dto.debut(), dto.nombreJours(), JOUR_ET_NUIT, equipe, null, "GARDE_TOUR_AUTO", true, acteur, "Génération automatique (rotation de l'équipe)");
            resultat.put("source", "equipe");
        }
        return resultat;
    }

    /**
     * Remplit les jours demandés. « ancre » : le jour où la première personne de la liste est de garde (les suivantes prennent les jours d'après, en boucle) ;
     * null = selon la date seule (toujours la même personne pour un même jour et un même nombre de personnes).
     */
    private Map<String, Object> appliquer(LocalDate debut, int nombreJours, String periode, List<Utilisateur> equipe, LocalDate ancre, String action, boolean remplacer, String acteur, String titre) {
        LocalDate fin = debut.plusDays(nombreJours - 1L);
        List<String> periodes = JOUR_ET_NUIT.equals(periode) ? List.of("JOUR", "NUIT") : List.of(periode);
        int remplacees = 0;
        if (remplacer) {
            for (String p : periodes) {
                List<Garde> anciennes = gardes.findByDateBetweenAndPeriode(debut, fin, p);
                remplacees += anciennes.size();
                gardes.deleteAll(anciennes);
            }
            gardes.flush();
        }
        Map<Long, Integer> joursParPersonne = new LinkedHashMap<>();
        int jours = 0;
        for (int i = 0; i < nombreJours; i++) {
            LocalDate jour = debut.plusDays(i);
            long decalage = ancre == null ? jour.toEpochDay() : ChronoUnit.DAYS.between(ancre, jour);
            int rang = (int) Math.floorMod(decalage, (long) equipe.size());
            Utilisateur personne = equipe.get(rang);
            boolean cree = false;
            for (String p : periodes) {
                if (gardes.existsByDateAndPeriodeAndUtilisateurId(jour, p, personne.getId())) continue;
                gardes.save(nouvelleGarde(jour, p, personne));
                cree = true;
            }
            if (cree) { jours++; joursParPersonne.merge(personne.getId(), 1, Integer::sum); }
        }
        audit.log(action, "Garde", null, titre + " (" + libellePeriode(periode) + ") du " + debut + " au " + fin + " : " + jours + " jour(s) pour " + equipe.size() + " personne(s)");
        for (Utilisateur personne : equipe) {
            int n = joursParPersonne.getOrDefault(personne.getId(), 0);
            if (n > 0) prevenir(personne, acteur, "Vous êtes de garde " + n + " jour" + (n > 1 ? "s" : "") + " (" + libellePeriode(periode) + ") du " + JOUR_LONG.format(debut) + " au " + JOUR_LONG.format(fin) + ".");
        }
        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("jours", jours);
        resultat.put("remplacees", remplacees);
        resultat.put("du", debut);
        resultat.put("au", fin);
        return resultat;
    }

    private static Garde nouvelleGarde(LocalDate jour, String periode, Utilisateur personne) {
        Garde garde = new Garde();
        garde.setDate(jour);
        garde.setPeriode(periode);
        garde.setUtilisateur(personne);
        return garde;
    }

    private void prevenir(Utilisateur personne, String acteur, String message) {
        if (acteur != null && acteur.equalsIgnoreCase(personne.getEmail())) return;
        notifications.informer(personne, "GARDE", "Tour de garde", message, acteur, "/garde");
    }

    private Utilisateur personne(Long id) {
        Utilisateur u = utilisateurs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette personne n'existe pas"));
        if (!ROLES_DE_GARDE.contains(u.getRole())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, u.getNom() + " ne fait pas partie de l'équipe de garde.");
        return u;
    }

    private static String libellePeriode(String periode) {
        return JOUR_ET_NUIT.equals(periode) ? "jour et nuit" : "NUIT".equals(periode) ? "nuit" : "jour";
    }

    private GardeDTO versDto(Garde g) {
        Utilisateur u = g.getUtilisateur();
        return new GardeDTO(g.getId(), g.getDate(), g.getPeriode(), u.getId(), u.getNom(), u.getRole().name(), LIBELLE_ROLE, u.getTelephone(), g.getNote());
    }
}
