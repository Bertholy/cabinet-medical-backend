package com.cabinet.cabinet;

import com.cabinet.cabinet.repository.ConsultationPrenataleRepository;
import com.cabinet.cabinet.repository.EnfantRepository;
import com.cabinet.cabinet.repository.GrossesseRepository;
import com.cabinet.cabinet.repository.VaccinationEnfantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MaterniteTests extends BaseApiTest {
    private static final String TEL = "0341234567";
    private static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");

    @Autowired GrossesseRepository grossesses;
    @Autowired ConsultationPrenataleRepository consultations;
    @Autowired EnfantRepository enfants;
    @Autowired VaccinationEnfantRepository vaccinations;

    @BeforeEach
    void vider() {
        vaccinations.deleteAll();
        enfants.deleteAll();
        consultations.deleteAll();
        grossesses.deleteAll();
    }

    private LocalDate aujourdhui() { return LocalDate.now(ZONE); }

    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requete, String jeton, String corps) throws Exception {
        return mvc.perform(requete.header("Authorization", jeton).contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    private String ouvrirGrossesse(LocalDate ddr) throws Exception {
        return extraire(json(post("/api/dossiers/" + TEL + "/grossesses"), sageFemme, "{\"ddr\":\"" + ddr + "\",\"groupeSanguin\":\"O+\"}")
                .andExpect(status().isCreated()).andReturn(), "id");
    }

    @Test
    void grossesse_terme_calendrier_consultations_etAlertes() throws Exception {
        LocalDate ddr = aujourdhui().minusDays(140); // 20 SA
        json(post("/api/dossiers/" + TEL + "/grossesses"), sageFemme, "{\"ddr\":\"" + ddr + "\",\"groupeSanguin\":\"o+\",\"risques\":\"Césarienne précédente\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sa").value(20)).andExpect(jsonPath("$.saJours").value(0)).andExpect(jsonPath("$.trimestre").value(2))
                .andExpect(jsonPath("$.dpa").value(ddr.plusDays(280).toString())).andExpect(jsonPath("$.joursRestants").value(140))
                .andExpect(jsonPath("$.groupeSanguin").value("O+"))
                // à 20 SA : la 1re consultation (0–16 SA) est en retard, la 2e (24–28) est à venir
                .andExpect(jsonPath("$.calendrier[0].etat").value("EN_RETARD")).andExpect(jsonPath("$.calendrier[1].etat").value("A_VENIR"))
                .andExpect(jsonPath("$.prochaineEtape.code").value("CPN1"))
                .andExpect(jsonPath("$.rappels[*]", hasItem(containsString("1re consultation prénatale non faite"))))
                .andExpect(jsonPath("$.rappels[*]", hasItem(containsString("Césarienne précédente"))));
        String id = extraire(mvc.perform(get("/api/dossiers/" + TEL + "/maternite").header("Authorization", sageFemme)).andReturn(), "id");

        // une seule grossesse en cours à la fois
        json(post("/api/dossiers/" + TEL + "/grossesses"), sageFemme, "{\"ddr\":\"" + ddr + "\"}").andExpect(status().isConflict());

        // consultation à 12 SA, avec tension élevée et albuminurie : la 1re consultation est faite ; alertes affichées
        json(post("/api/dossiers/grossesses/" + id + "/consultations"), sageFemme,
                "{\"date\":\"" + ddr.plusWeeks(12) + "\",\"poidsKg\":58.5,\"tensionSystolique\":150,\"tensionDiastolique\":95,\"bcf\":150,\"albuminurie\":\"+\",\"oedemes\":true,\"actes\":[\"VAT\",\"fer\"],\"observations\":\"RAS\",\"prochainRdv\":\"" + aujourdhui().plusDays(10) + "\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.termeSa").value(12))
                .andExpect(jsonPath("$.actes", contains("FER", "VAT")))
                .andExpect(jsonPath("$.alertes[*]", hasItem(containsString("Tension élevée"))))
                .andExpect(jsonPath("$.alertes[*]", hasItem(containsString("Albuminurie"))))
                .andExpect(jsonPath("$.alertes[*]", hasItem(containsString("Œdèmes"))));
        mvc.perform(get("/api/dossiers/" + TEL + "/maternite").header("Authorization", sageFemme)).andExpect(status().isOk())
                .andExpect(jsonPath("$.grossesses[0].calendrier[0].etat").value("FAIT"))
                .andExpect(jsonPath("$.grossesses[0].prochaineEtape.code").value("CPN2"))
                .andExpect(jsonPath("$.grossesses[0].consultations.length()").value(1))
                .andExpect(jsonPath("$.grossesses[0].prochainRdv").value(aujourdhui().plusDays(10).toString()));

        // valeurs impossibles refusées
        String base = "{\"date\":\"" + aujourdhui() + "\",";
        json(post("/api/dossiers/grossesses/" + id + "/consultations"), sageFemme, base + "\"bcf\":300}").andExpect(status().isBadRequest());
        json(post("/api/dossiers/grossesses/" + id + "/consultations"), sageFemme, base + "\"albuminurie\":\"X\"}").andExpect(status().isBadRequest());
        json(post("/api/dossiers/grossesses/" + id + "/consultations"), sageFemme, base + "\"actes\":[\"MAGIE\"]}").andExpect(status().isBadRequest());
        json(post("/api/dossiers/grossesses/" + id + "/consultations"), sageFemme, "{\"date\":\"" + aujourdhui().plusDays(3) + "\"}").andExpect(status().isBadRequest());
        json(post("/api/dossiers/grossesses/" + id + "/consultations"), sageFemme, "{\"date\":\"" + ddr.minusDays(5) + "\"}").andExpect(status().isBadRequest());

        // fin de grossesse : on peut alors en ouvrir une nouvelle
        json(put("/api/dossiers/grossesses/" + id + "/terminer"), sageFemme, "{\"statut\":\"TERMINEE\",\"issue\":\"Accouchement normal, garçon\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("TERMINEE")).andExpect(jsonPath("$.issue").value(containsString("garçon")));
        json(put("/api/dossiers/grossesses/" + id + "/terminer"), sageFemme, "{\"statut\":\"BIZARRE\"}").andExpect(status().isBadRequest());
        json(post("/api/dossiers/" + TEL + "/grossesses"), sageFemme, "{\"dpa\":\"" + aujourdhui().plusDays(200) + "\"}").andExpect(status().isCreated());
    }

    @Test
    void ouvertureDeGrossesse_validations() throws Exception {
        json(post("/api/dossiers/" + TEL + "/grossesses"), sageFemme, "{}").andExpect(status().isBadRequest());
        json(post("/api/dossiers/" + TEL + "/grossesses"), sageFemme, "{\"ddr\":\"" + aujourdhui().plusDays(2) + "\"}").andExpect(status().isBadRequest());
        json(post("/api/dossiers/" + TEL + "/grossesses"), sageFemme, "{\"ddr\":\"" + aujourdhui().minusDays(400) + "\"}").andExpect(status().isBadRequest());
        json(post("/api/dossiers/" + TEL + "/grossesses"), sageFemme, "{\"ddr\":\"" + aujourdhui().minusDays(30) + "\",\"groupeSanguin\":\"Z9\"}").andExpect(status().isBadRequest());
        json(post("/api/dossiers/pas-un-numero/grossesses"), sageFemme, "{\"ddr\":\"" + aujourdhui().minusDays(30) + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void droitsParRole() throws Exception {
        String id = ouvrirGrossesse(aujourdhui().minusDays(70));
        mvc.perform(get("/api/dossiers/" + TEL + "/maternite").header("Authorization", secretaire)).andExpect(status().isForbidden());
        mvc.perform(get("/api/dossiers/maternite/rappels").header("Authorization", secretaire)).andExpect(status().isForbidden());
        mvc.perform(get("/api/dossiers/" + TEL + "/maternite")).andExpect(status().isUnauthorized());
        for (String jeton : new String[]{admin, medecin, sageFemme}) mvc.perform(get("/api/dossiers/" + TEL + "/maternite").header("Authorization", jeton)).andExpect(status().isOk());
        // la suppression est réservée au médecin et au super administrateur
        mvc.perform(delete("/api/dossiers/grossesses/" + id).header("Authorization", sageFemme)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/dossiers/grossesses/" + id).header("Authorization", medecin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/dossiers/" + TEL + "/maternite").header("Authorization", sageFemme)).andExpect(jsonPath("$.grossesses.length()").value(0));
    }

    @Test
    void carnetDeVaccination_echeances_retards_etAnnulation() throws Exception {
        LocalDate naissance = aujourdhui().minusDays(100);
        String enfantJson = json(post("/api/dossiers/" + TEL + "/enfants"), sageFemme, "{\"nom\":\"Bébé Rakoto\",\"sexe\":\"f\",\"dateNaissance\":\"" + naissance + "\",\"poidsNaissanceG\":3200}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.ageJours").value(100)).andExpect(jsonPath("$.sexe").value("F"))
                .andExpect(jsonPath("$.vaccins.length()").value(15))
                // à 100 jours : tout ce qui est dû jusqu'à 14 semaines est en retard ; la rougeole (9 mois) est à venir
                .andExpect(jsonPath("$.vaccins[?(@.code=='BCG')].statut", contains("EN_RETARD")))
                .andExpect(jsonPath("$.vaccins[?(@.code=='PENTA3')].statut", contains("EN_RETARD")))
                .andExpect(jsonPath("$.vaccins[?(@.code=='RR1')].statut", contains("A_VENIR")))
                .andReturn().getResponse().getContentAsString();
        String id = enfantJson.replaceAll(".*\"id\":([0-9]+).*", "$1");

        json(put("/api/dossiers/enfants/" + id + "/vaccins/bcg"), sageFemme, "{\"dateFaite\":\"" + naissance.plusDays(1) + "\",\"lot\":\"L123\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.vaccins[?(@.code=='BCG')].statut", contains("FAIT")))
                .andExpect(jsonPath("$.vaccins[?(@.code=='BCG')].lot", contains("L123")));
        // incohérences refusées
        json(put("/api/dossiers/enfants/" + id + "/vaccins/PENTA1"), sageFemme, "{\"dateFaite\":\"" + naissance.minusDays(3) + "\"}").andExpect(status().isBadRequest());
        json(put("/api/dossiers/enfants/" + id + "/vaccins/PENTA1"), sageFemme, "{\"dateFaite\":\"" + aujourdhui().plusDays(2) + "\"}").andExpect(status().isBadRequest());
        json(put("/api/dossiers/enfants/" + id + "/vaccins/INCONNU"), sageFemme, "{}").andExpect(status().isBadRequest());
        json(post("/api/dossiers/" + TEL + "/enfants"), sageFemme, "{\"nom\":\"\",\"dateNaissance\":\"" + naissance + "\"}").andExpect(status().isBadRequest());
        json(post("/api/dossiers/" + TEL + "/enfants"), sageFemme, "{\"nom\":\"Futur\",\"dateNaissance\":\"" + aujourdhui().plusDays(1) + "\"}").andExpect(status().isBadRequest());

        // les vaccins en retard remontent dans les rappels, pas celui qui est fait
        mvc.perform(get("/api/dossiers/maternite/rappels").header("Authorization", sageFemme)).andExpect(status().isOk())
                .andExpect(jsonPath("$.vaccins[?(@.code=='PENTA1')].enfantNom", contains("Bébé Rakoto")))
                .andExpect(jsonPath("$.vaccins[?(@.code=='BCG')]").isEmpty());

        // annulation d'un vaccin enregistré par erreur
        mvc.perform(put("/api/dossiers/enfants/" + id + "/vaccins/BCG/annuler").header("Authorization", sageFemme)).andExpect(status().isOk())
                .andExpect(jsonPath("$.vaccins[?(@.code=='BCG')].statut", contains("EN_RETARD")));
        // suppression de l'enfant : médecin seulement
        mvc.perform(delete("/api/dossiers/enfants/" + id).header("Authorization", sageFemme)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/dossiers/enfants/" + id).header("Authorization", medecin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/dossiers/" + TEL + "/maternite").header("Authorization", sageFemme)).andExpect(jsonPath("$.enfants.length()").value(0));
    }

    @Test
    void rappels_termeProcheEtTermeDepasse() throws Exception {
        ouvrirGrossesse(aujourdhui().minusDays(270)); // terme dans 10 jours
        json(post("/api/dossiers/0329876543/grossesses"), sageFemme, "{\"dpa\":\"" + aujourdhui().minusDays(5) + "\"}").andExpect(status().isCreated());
        mvc.perform(get("/api/dossiers/maternite/rappels").header("Authorization", medecin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.grossesses.length()").value(2))
                .andExpect(jsonPath("$.grossesses[*].rappels[*]", hasItem(containsString("Accouchement prévu dans 10 jour"))))
                .andExpect(jsonPath("$.grossesses[*].rappels[*]", hasItem(containsString("Terme dépassé de 5 jour"))));
    }
}
