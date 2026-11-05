package com.cabinet.cabinet;

import com.cabinet.cabinet.entity.RendezVous;
import com.cabinet.cabinet.enums.StatutRDV;
import com.cabinet.cabinet.repository.NotificationRepository;
import com.cabinet.cabinet.repository.RendezVousRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La secrétaire voit si un client est nouveau ou ancien (nombre de passages), et combien de femmes, d'hommes et d'enfants sont venus. */
class ClientsNouveauxAnciensTests extends BaseApiTest {
    @Autowired RendezVousRepository rdvs;
    @Autowired NotificationRepository notifications;

    private final LocalDate aujourdhui = LocalDate.now(ZoneId.of("Indian/Antananarivo"));

    @BeforeEach
    @AfterEach
    void nettoyer() {
        notifications.deleteAll();
        rdvs.deleteAll();
    }

    private void rdv(String nom, int age, String sexe, String telephone, LocalDate date, String heure, StatutRDV statut) {
        RendezVous r = new RendezVous();
        r.setNomClient(nom); r.setAgeClient(age); r.setSexe(sexe); r.setTelephoneClient(telephone);
        r.setDateRdv(date); r.setHeureRdv(LocalTime.parse(heure)); r.setStatut(statut);
        rdvs.save(r);
    }

    private void donnees() {
        rdv("Rasoa Hanta", 30, "FEMME", "034 12 345 67", aujourdhui.minusDays(10), "09:00", StatutRDV.TERMINE);
        rdv("rasoa  hanta", 30, "FEMME", "0341234567", aujourdhui, "09:00", StatutRDV.EN_ATTENTE);       // même personne : écriture et numéro différents
        rdv("Rakoto Jean", 40, "HOMME", "034 99 999 99", aujourdhui.minusDays(3), "10:00", StatutRDV.ANNULE);   // annulé : n'est pas un passage
        rdv("Rakoto Jean", 40, "HOMME", "034 99 999 99", aujourdhui, "10:00", StatutRDV.CONFIRME);
        rdv("Bema", 5, "FEMME", "034 12 345 67", aujourdhui, "11:00", StatutRDV.EN_ATTENTE);                // l'enfant partage le numéro de sa mère : autre personne
    }

    @Test
    void chaque_rendez_vous_dit_si_le_client_est_nouveau_ou_ancien() throws Exception {
        donnees();
        mvc.perform(get("/api/rdv/dashboard").param("periode", "aujourd_hui").header("Authorization", secretaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rendezVous[?(@.nomClient == 'rasoa  hanta')].nouveauClient").value(false))
                .andExpect(jsonPath("$.rendezVous[?(@.nomClient == 'rasoa  hanta')].passage").value(2))
                .andExpect(jsonPath("$.rendezVous[?(@.nomClient == 'rasoa  hanta')].passagesTotal").value(2))
                .andExpect(jsonPath("$.rendezVous[?(@.nomClient == 'Rakoto Jean')].nouveauClient").value(true))
                .andExpect(jsonPath("$.rendezVous[?(@.nomClient == 'Rakoto Jean')].passagesTotal").value(1))
                .andExpect(jsonPath("$.rendezVous[?(@.nomClient == 'Bema')].nouveauClient").value(true));
    }

    @Test
    void la_frequentation_compte_nouveaux_anciens_femmes_hommes_et_enfants() throws Exception {
        donnees();
        mvc.perform(get("/api/stats/synthese").param("debut", aujourdhui.toString()).param("fin", aujourdhui.toString()).header("Authorization", secretaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frequentation.passages").value(3))
                .andExpect(jsonPath("$.frequentation.patients").value(3))
                .andExpect(jsonPath("$.frequentation.nouveaux").value(2))
                .andExpect(jsonPath("$.frequentation.anciens").value(1))
                .andExpect(jsonPath("$.frequentation.parCategorie[?(@.code == 'FEMME')].patients").value(1))
                .andExpect(jsonPath("$.frequentation.parCategorie[?(@.code == 'FEMME')].anciens").value(1))
                .andExpect(jsonPath("$.frequentation.parCategorie[?(@.code == 'HOMME')].nouveaux").value(1))
                .andExpect(jsonPath("$.frequentation.parCategorie[?(@.code == 'ENFANT')].patients").value(1))
                .andExpect(jsonPath("$.frequentation.parCategorie[?(@.code == 'NON_PRECISE')].patients").value(0));
        // sur tout le mois précédent + aujourd'hui, la première venue de Rasoa (il y a 10 jours) la rend « nouvelle » de cette période
        mvc.perform(get("/api/stats/synthese").param("debut", aujourdhui.minusDays(10).toString()).param("fin", aujourdhui.toString()).header("Authorization", secretaire))
                .andExpect(jsonPath("$.frequentation.passages").value(4))
                .andExpect(jsonPath("$.frequentation.nouveaux").value(3))
                .andExpect(jsonPath("$.frequentation.anciens").value(0));
    }

    // ---------------------------------------------------------------- « patient existant ou nouveau ? » pendant la saisie du nom

    private org.springframework.test.web.servlet.ResultActions verifier(String nom, String telephone) throws Exception {
        var requete = get("/api/patients/verification").header("Authorization", secretaire);
        if (nom != null) requete = requete.param("nom", nom);
        if (telephone != null) requete = requete.param("telephone", telephone);
        return mvc.perform(requete).andExpect(status().isOk());
    }

    @Test
    void un_nom_deja_venu_est_un_patient_existant_dans_n_importe_quel_ordre() throws Exception {
        donnees();
        verifier("Hanta RASOA", null).andExpect(jsonPath("$.statut").value("EXISTANT"))
                .andExpect(jsonPath("$.correspondances.length()").value(1)).andExpect(jsonPath("$.correspondances[0].passages").value(2));
        verifier("Rakoto Jean", "034 99 999 99").andExpect(jsonPath("$.statut").value("EXISTANT"));
    }

    @Test
    void un_debut_de_nom_ou_un_autre_numero_ressemble_sans_etre_certain() throws Exception {
        donnees();
        verifier("Rasoa", null).andExpect(jsonPath("$.statut").value("PROCHE")).andExpect(jsonPath("$.correspondances[0].nom").value("rasoa  hanta"));
        verifier("ras han", null).andExpect(jsonPath("$.statut").value("PROCHE"));
        // même nom, mais le numéro est celui de quelqu'un d'autre
        verifier("Rasoa Hanta", "034 99 999 99").andExpect(jsonPath("$.statut").value("PROCHE"));
        // numéro seul : déjà utilisé
        verifier(null, "034 99 999 99").andExpect(jsonPath("$.statut").value("PROCHE")).andExpect(jsonPath("$.correspondances[0].nom").value("Rakoto Jean"));
    }

    @Test
    void personne_ne_correspond_nouveau_patient_et_trop_court_pour_juger() throws Exception {
        donnees();
        verifier("Zafy Tsiory", null).andExpect(jsonPath("$.statut").value("NOUVEAU")).andExpect(jsonPath("$.correspondances.length()").value(0));
        verifier("Ra", null).andExpect(jsonPath("$.statut").value("INCONNU"));
        // un rendez-vous annulé ne fait pas un patient existant
        rdv("Anule Seulement", 30, "HOMME", "034 55 555 55", aujourdhui.minusDays(2), "08:00", StatutRDV.ANNULE);
        verifier("Anule Seulement", null).andExpect(jsonPath("$.statut").value("NOUVEAU"));
    }

    @Test
    void la_verification_est_reservee_au_personnel() throws Exception {
        mvc.perform(get("/api/patients/verification").param("nom", "Rasoa Hanta")).andExpect(status().is4xxClientError());
    }

    // ---------------------------------------------------------------- la liste des patients (menu latéral)

    @Test
    void la_liste_des_patients_dit_nouveau_ou_ancien_et_compte_femmes_hommes_enfants() throws Exception {
        donnees();
        mvc.perform(get("/api/patients/liste").header("Authorization", secretaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.nouveaux").value(2)).andExpect(jsonPath("$.anciens").value(1))
                .andExpect(jsonPath("$.parCategorie.FEMME").value(1)).andExpect(jsonPath("$.parCategorie.HOMME").value(1)).andExpect(jsonPath("$.parCategorie.ENFANT").value(1))
                // Rasoa est venue deux fois (écrit de deux façons) : un seul patient, ancien, avec ses deux passages
                .andExpect(jsonPath("$.patients[?(@.nom == 'rasoa  hanta')].passages").value(2))
                .andExpect(jsonPath("$.patients[?(@.nom == 'rasoa  hanta')].nouveau").value(false))
                .andExpect(jsonPath("$.patients[?(@.nom == 'Rakoto Jean')].nouveau").value(true))
                .andExpect(jsonPath("$.patients[?(@.nom == 'Bema')].categorie").value("ENFANT"));
    }

    @Test
    void un_adolescent_de_12_a_17_ans_a_sa_propre_categorie() throws Exception {
        rdv("Tiana", 15, "HOMME", "034 77 777 77", aujourdhui, "08:00", StatutRDV.EN_ATTENTE);
        rdv("Lova", 11, "FEMME", "034 88 888 88", aujourdhui, "08:30", StatutRDV.EN_ATTENTE);
        rdv("Hery", 18, "HOMME", "034 66 666 66", aujourdhui, "09:00", StatutRDV.EN_ATTENTE);
        mvc.perform(get("/api/patients/liste").header("Authorization", secretaire))
                .andExpect(jsonPath("$.parCategorie.ADOLESCENT").value(1)).andExpect(jsonPath("$.parCategorie.ENFANT").value(1)).andExpect(jsonPath("$.parCategorie.HOMME").value(1))
                .andExpect(jsonPath("$.patients[?(@.nom == 'Tiana')].categorie").value("ADOLESCENT"));
        mvc.perform(get("/api/stats/synthese").param("debut", aujourdhui.toString()).param("fin", aujourdhui.toString()).header("Authorization", secretaire))
                .andExpect(jsonPath("$.frequentation.parCategorie[?(@.code == 'ADOLESCENT')].patients").value(1));
    }

    @Test
    void la_demande_a_traiter_dit_nouveau_ou_ancien() throws Exception {
        donnees();
        Long idAncien = rdvs.findAll().stream().filter(r -> "rasoa  hanta".equals(r.getNomClient())).findFirst().orElseThrow().getId();
        Long idNouveau = rdvs.findAll().stream().filter(r -> "Rakoto Jean".equals(r.getNomClient()) && r.getStatut() == StatutRDV.CONFIRME).findFirst().orElseThrow().getId();
        // un rendez-vous précis (page ouverte depuis une notification) porte lui aussi le voyant
        mvc.perform(get("/api/rdv/" + idAncien).header("Authorization", secretaire))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nouveauClient").value(false)).andExpect(jsonPath("$.passage").value(2)).andExpect(jsonPath("$.passagesTotal").value(2));
        mvc.perform(get("/api/rdv/" + idNouveau).header("Authorization", secretaire))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nouveauClient").value(true)).andExpect(jsonPath("$.passage").value(1));
    }

    @Test
    void la_liste_se_filtre_par_statut_et_par_recherche() throws Exception {
        donnees();
        mvc.perform(get("/api/patients/liste").param("statut", "NOUVEAU").header("Authorization", secretaire))
                .andExpect(jsonPath("$.patients.length()").value(2)).andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.anciens").value(1));
        mvc.perform(get("/api/patients/liste").param("statut", "ANCIEN").header("Authorization", secretaire)).andExpect(jsonPath("$.patients.length()").value(1));
        mvc.perform(get("/api/patients/liste").param("q", "RAKOTO").header("Authorization", secretaire))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.patients[0].nom").value("Rakoto Jean"));
        mvc.perform(get("/api/patients/liste").param("q", "034 99").header("Authorization", secretaire)).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/patients/liste")).andExpect(status().is4xxClientError());
    }
}
