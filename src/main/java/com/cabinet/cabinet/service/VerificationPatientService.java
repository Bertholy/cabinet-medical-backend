package com.cabinet.cabinet.service;

import com.cabinet.cabinet.repository.RendezVousRepository;
import com.cabinet.cabinet.util.ClientHistorique;
import com.cabinet.cabinet.util.PhoneUtils;
import com.cabinet.cabinet.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * « Patient existant ou nouveau ? » pendant que la secrétaire tape un nom (réservé au personnel).
 * <ul>
 *   <li>EXISTANT : un client déjà venu a exactement ce nom (dans n'importe quel ordre, sans tenir compte des accents ni des majuscules),
 *       et le numéro, s'il est donné, est le sien.</li>
 *   <li>PROCHE : des clients ressemblent (le nom tapé n'est que le début d'un nom connu, même nom avec un autre numéro, ou numéro déjà utilisé par un autre nom).</li>
 *   <li>NOUVEAU : personne ne correspond.</li>
 *   <li>INCONNU : trop peu de lettres tapées pour juger.</li>
 * </ul>
 * Un « client » est reconnu comme dans ClientHistorique (même nom, même numéro) ; seuls les rendez-vous non annulés comptent comme des passages.
 */
@Service
@RequiredArgsConstructor
public class VerificationPatientService {
    private static final int LETTRES_MINIMUM = 3;
    private static final int MAX_RESULTATS = 5;
    private final RendezVousRepository rendezVous;

    /** Un client connu, tous ses numéros écrits de la même façon et ses passages additionnés. */
    private static final class Connu {
        String nom; String telephone; long passages; LocalDate dernier; String nomPlat; String cleTelephone; boolean parTelephone;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> verifier(String nomSaisi, String telephoneSaisi) {
        String nomPlat = TextUtils.fold(nomSaisi);
        String[] mots = Arrays.stream(nomPlat.split(" ")).filter(m -> !m.isBlank()).toArray(String[]::new);
        long lettres = nomPlat.chars().filter(Character::isLetter).count();
        String cleTelephone = telephoneSaisi == null || PhoneUtils.digits(telephoneSaisi).length() < 9 ? null : PhoneUtils.searchKey(telephoneSaisi);
        if (lettres < LETTRES_MINIMUM && cleTelephone == null) return reponse("INCONNU", List.of());

        Map<String, Connu> clients = new LinkedHashMap<>();
        if (lettres >= LETTRES_MINIMUM) {
            String plusLong = Arrays.stream(mots).max(Comparator.comparingInt(String::length)).orElse(nomPlat);
            for (Object[] ligne : rendezVous.clientsParMot(TextUtils.ACCENTS, TextUtils.PLAIN, TextUtils.escapeLike(plusLong))) ajouter(clients, ligne, false);
        }
        if (cleTelephone != null) for (Object[] ligne : rendezVous.clientsParTelephone(cleTelephone)) ajouter(clients, ligne, true);

        List<Connu> exacts = new ArrayList<>(), proches = new ArrayList<>();
        for (Connu c : clients.values()) {
            boolean memeNom = lettres >= LETTRES_MINIMUM && memesMots(mots, c.nomPlat);
            boolean memeTel = cleTelephone != null && cleTelephone.equals(c.cleTelephone);
            if (memeNom && (cleTelephone == null || memeTel)) exacts.add(c);
            else if (memeNom || (lettres >= LETTRES_MINIMUM && debutDeNom(mots, c.nomPlat)) || memeTel) proches.add(c);
        }
        Comparator<Connu> recents = Comparator.comparing((Connu c) -> c.dernier, Comparator.nullsFirst(Comparator.naturalOrder())).reversed();
        exacts.sort(recents); proches.sort(recents);
        if (!exacts.isEmpty()) return reponse("EXISTANT", exacts.stream().limit(MAX_RESULTATS).toList());
        if (!proches.isEmpty()) return reponse("PROCHE", proches.stream().limit(MAX_RESULTATS).toList());
        return reponse(lettres >= LETTRES_MINIMUM ? "NOUVEAU" : "INCONNU", List.of());
    }

    /** Un patient de la liste : tous ses passages réunis. */
    private static final class Fiche {
        String nom; String telephone; Integer age; String sexe; long passages; LocalDate premier; LocalDate dernier;
        String categorie() {
            return com.cabinet.cabinet.util.Civilite.categorie(age, sexe);
        }
    }

    private static final int LIMITE_LISTE = 500;

    /**
     * La liste des patients : un par client (même nom, même numéro), du dernier venu au plus ancien. « Nouveau » = un seul passage, « Ancien » = plusieurs.
     * « q » filtre par nom ou téléphone ; « statut » (NOUVEAU ou ANCIEN) ne garde que les nouveaux ou les anciens, mais les totaux
     * (nouveaux, anciens, femmes, hommes, enfants) portent sur toute la recherche, pour que les onglets montrent toujours leur compte.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> liste(String q, String statut) {
        Map<String, Fiche> fiches = new LinkedHashMap<>();
        for (Object[] p : rendezVous.findPassagesDetailles()) {
            Fiche f = fiches.computeIfAbsent(ClientHistorique.cle((String) p[0], (String) p[1]), cle -> new Fiche());
            LocalDate jour = p[4] instanceof java.sql.Date date ? date.toLocalDate() : (LocalDate) p[4];
            if (f.premier == null) f.premier = jour;
            // les passages arrivent du plus ancien au plus récent : l'écriture, l'âge et le sexe retenus sont ceux du dernier passage
            f.nom = (String) p[0]; f.telephone = (String) p[1]; f.age = (Integer) p[2]; f.sexe = (String) p[3]; f.dernier = jour; f.passages++;
        }
        String terme = TextUtils.fold(q);
        String chiffres = PhoneUtils.digits(q == null ? "" : q);
        List<Fiche> trouvees = new ArrayList<>();
        for (Fiche f : fiches.values()) {
            boolean ok = terme.isEmpty() || TextUtils.fold(f.nom).contains(terme) || (chiffres.length() >= 3 && PhoneUtils.digits(f.telephone).contains(chiffres));
            if (ok) trouvees.add(f);
        }
        trouvees.sort(Comparator.comparing((Fiche f) -> f.dernier).reversed().thenComparing(f -> TextUtils.fold(f.nom)));

        long nouveaux = trouvees.stream().filter(f -> f.passages == 1).count();
        Map<String, Long> parCategorie = new LinkedHashMap<>();
        for (String code : List.of("FEMME", "HOMME", "ADOLESCENT", "ENFANT", "NON_PRECISE")) parCategorie.put(code, 0L);
        trouvees.forEach(f -> parCategorie.merge(f.categorie(), 1L, Long::sum));

        boolean seulementNouveaux = "NOUVEAU".equalsIgnoreCase(statut), seulementAnciens = "ANCIEN".equalsIgnoreCase(statut);
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (Fiche f : trouvees) {
            boolean nouveau = f.passages == 1;
            if ((seulementNouveaux && !nouveau) || (seulementAnciens && nouveau)) continue;
            if (lignes.size() >= LIMITE_LISTE) break;
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("nom", f.nom); l.put("telephone", f.telephone); l.put("age", f.age); l.put("sexe", com.cabinet.cabinet.util.Civilite.normaliserSexe(f.sexe));
            l.put("categorie", f.categorie()); l.put("passages", f.passages); l.put("premierPassage", f.premier); l.put("dernierPassage", f.dernier); l.put("nouveau", nouveau);
            lignes.add(l);
        }
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("total", (long) trouvees.size());
        corps.put("nouveaux", nouveaux);
        corps.put("anciens", trouvees.size() - nouveaux);
        corps.put("parCategorie", parCategorie);
        corps.put("tronque", (seulementNouveaux ? nouveaux : seulementAnciens ? trouvees.size() - nouveaux : trouvees.size()) > lignes.size());
        corps.put("patients", lignes);
        return corps;
    }

    private void ajouter(Map<String, Connu> clients, Object[] ligne, boolean parTelephone) {
        String nom = (String) ligne[0], telephone = (String) ligne[1];
        Connu c = clients.computeIfAbsent(ClientHistorique.cle(nom, telephone), cle -> {
            Connu n = new Connu();
            n.nom = nom; n.telephone = telephone; n.nomPlat = TextUtils.fold(nom); n.cleTelephone = PhoneUtils.searchKey(telephone);
            return n;
        });
        // une ligne par écriture du nom et du numéro : on additionne les passages et on garde le plus récent
        c.passages += ((Number) ligne[2]).longValue();
        LocalDate jour = ligne[3] instanceof java.sql.Date date ? date.toLocalDate() : (LocalDate) ligne[3];
        if (c.dernier == null || (jour != null && jour.isAfter(c.dernier))) c.dernier = jour;
        c.parTelephone |= parTelephone;
    }

    /** Les mêmes mots, dans n'importe quel ordre : « Hanta Rasoa » = « Rasoa Hanta ». */
    private static boolean memesMots(String[] saisis, String nomConnu) {
        return new TreeSet<>(Arrays.asList(saisis)).equals(new TreeSet<>(Arrays.asList(nomConnu.split(" "))));
    }

    /** Chaque mot tapé est le début d'un mot du nom connu : « ras han » → « Rasoa Hanta ». */
    private static boolean debutDeNom(String[] saisis, String nomConnu) {
        String[] motsConnus = nomConnu.split(" ");
        for (String mot : saisis) if (Arrays.stream(motsConnus).noneMatch(m -> m.startsWith(mot))) return false;
        return saisis.length > 0;
    }

    private static Map<String, Object> reponse(String statut, List<Connu> clients) {
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (Connu c : clients) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("nom", c.nom); l.put("telephone", c.telephone); l.put("passages", c.passages); l.put("dernierPassage", c.dernier);
            lignes.add(l);
        }
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("statut", statut);
        corps.put("correspondances", lignes);
        return corps;
    }
}
