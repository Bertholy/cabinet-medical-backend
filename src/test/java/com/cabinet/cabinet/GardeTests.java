package com.cabinet.cabinet;

import com.cabinet.cabinet.enums.Role;
import com.cabinet.cabinet.repository.GardeRepository;
import com.cabinet.cabinet.repository.NotificationRepository;
import com.cabinet.cabinet.repository.TourGardeModeleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tour de garde : tout le personnel le voit ; l'administration et la secrétaire le gèrent (droit GARDE_GERER, réglable), pas les médecins ni les sages-femmes.
 * Une seule personne de garde par jour, à tour de rôle selon le nombre de personnes de l'équipe soignante.
 */
class GardeTests extends BaseApiTest {
    @Autowired GardeRepository gardes;
    @Autowired NotificationRepository notifications;
    @Autowired TourGardeModeleRepository modeles;

    private final LocalDate demain = LocalDate.now(ZoneId.of("Indian/Antananarivo")).plusDays(1);

    @BeforeEach
    @AfterEach
    void nettoyer() {
        notifications.deleteAll();
        gardes.deleteAll();
        modeles.deleteAll();
    }

    private String corps(Long utilisateurId, LocalDate date, String periode) {
        return "{\"date\":\"" + date + "\",\"periode\":\"" + periode + "\",\"utilisateurId\":" + utilisateurId + ",\"note\":\"Téléphone allumé\"}";
    }

    /** Les identifiants de l'équipe de garde, dans l'ordre où le serveur les fait tourner. */
    private List<Long> equipe() throws Exception {
        String json = mvc.perform(get("/api/gardes/personnel").header("Authorization", admin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(json);
        List<Long> ids = new ArrayList<>();
        while (m.find()) ids.add(Long.valueOf(m.group(1)));
        return ids;
    }

    @Test
    void l_administration_met_une_personne_de_garde_et_la_prevenue() throws Exception {
        Long medecinId = idDe("test.medecin@casafami.test");
        mvc.perform(post("/api/gardes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps(medecinId, demain, "NUIT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.utilisateurId").value(medecinId))
                .andExpect(jsonPath("$.periode").value("NUIT"))
                .andExpect(jsonPath("$.roleLibelle").value("Sage-femme"))
                .andExpect(jsonPath("$.note").value("Téléphone allumé"));
        mvc.perform(get("/api/notifications").header("Authorization", medecin))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Tour de garde")));
        // la même personne ne peut pas être mise deux fois de garde au même moment
        mvc.perform(post("/api/gardes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps(medecinId, demain, "NUIT")))
                .andExpect(status().isConflict());
        // mais elle peut l'être aussi le jour
        mvc.perform(post("/api/gardes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps(medecinId, demain, "JOUR")))
                .andExpect(status().isCreated());
    }

    @Test
    void tout_le_personnel_voit_le_tour_de_garde_mais_ne_peut_pas_le_modifier() throws Exception {
        Long sfId = idDe("test.sf@casafami.test");
        mvc.perform(post("/api/gardes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps(sfId, demain, "JOUR")))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/gardes").header("Authorization", secretaire).param("du", demain.toString()).param("au", demain.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].utilisateurId").value(sfId));
        mvc.perform(get("/api/gardes/aujourdhui").header("Authorization", medecin)).andExpect(status().isOk());
        // un médecin (sans GARDE_GERER) ne peut ni ajouter, ni retirer, ni générer un tour, ni rétablir le tour automatique, ni lister l'équipe
        mvc.perform(post("/api/gardes").header("Authorization", medecin).contentType(MediaType.APPLICATION_JSON).content(corps(sfId, demain.plusDays(1), "JOUR")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/gardes/1").header("Authorization", medecin)).andExpect(status().isForbidden());
        mvc.perform(post("/api/gardes/tour").header("Authorization", medecin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"debut\":\"" + demain + "\",\"nombreJours\":3,\"periode\":\"JOUR\",\"utilisateurIds\":[" + sfId + "]}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/gardes/automatique").header("Authorization", medecin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"debut\":\"" + demain + "\",\"nombreJours\":7}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/gardes/personnel").header("Authorization", medecin)).andExpect(status().isForbidden());
        // la secrétaire, elle, gère le tour de garde (droit d'origine)
        mvc.perform(get("/api/gardes/personnel").header("Authorization", secretaire)).andExpect(status().isOk());
        mvc.perform(post("/api/gardes").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(corps(sfId, demain.plusDays(1), "JOUR"))).andExpect(status().isCreated());
        // sans connexion : refusé
        mvc.perform(get("/api/gardes")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/gardes/personnel").header("Authorization", admin)).andExpect(status().isOk());
    }

    @Test
    void seule_l_equipe_soignante_peut_etre_de_garde() throws Exception {
        // la secrétaire et le super administrateur ne font pas partie de l'équipe de garde
        Long secId = idDe("test.secretaire@casafami.test");
        Long adminId = idDe("test.admin@casafami.test");
        mvc.perform(post("/api/gardes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps(secId, demain, "JOUR"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/gardes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps(adminId, demain, "JOUR"))).andExpect(status().isBadRequest());
        List<Long> equipe = equipe();
        org.junit.jupiter.api.Assertions.assertFalse(equipe.contains(secId));
        org.junit.jupiter.api.Assertions.assertFalse(equipe.contains(adminId));
        org.junit.jupiter.api.Assertions.assertTrue(equipe.contains(idDe("test.sf@casafami.test")));
        // toute l'équipe est présentée comme « sage-femme », même un compte enregistré comme médecin
        mvc.perform(get("/api/gardes/personnel").header("Authorization", admin)).andExpect(content().string(containsString("\"roleLibelle\":\"Sage-femme\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Médecin"))));
    }

    @Test
    void un_tour_de_garde_fait_tourner_les_personnes_jour_apres_jour() throws Exception {
        tokenFor("test.infirmiere@casafami.test", Role.INFIRMIERE);
        Long medecinId = idDe("test.medecin@casafami.test");
        Long sfId = idDe("test.sf@casafami.test");
        Long infId = idDe("test.infirmiere@casafami.test");
        String tour = "{\"debut\":\"" + demain + "\",\"nombreJours\":5,\"periode\":\"JOUR\",\"utilisateurIds\":[" + medecinId + "," + sfId + "," + infId + "],\"remplacer\":false}";
        mvc.perform(post("/api/gardes/tour").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(tour))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jours").value(5)).andExpect(jsonPath("$.remplacees").value(0));
        mvc.perform(get("/api/gardes").header("Authorization", medecin).param("du", demain.toString()).param("au", demain.plusDays(4).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].utilisateurId").value(medecinId))
                .andExpect(jsonPath("$[1].utilisateurId").value(sfId))
                .andExpect(jsonPath("$[2].utilisateurId").value(infId))
                .andExpect(jsonPath("$[3].utilisateurId").value(medecinId))
                .andExpect(jsonPath("$[4].utilisateurId").value(sfId));
        // relancer le même tour sans « remplacer » n'ajoute rien de plus ; avec « remplacer », il refait les 5 jours
        mvc.perform(post("/api/gardes/tour").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(tour))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jours").value(0));
        mvc.perform(post("/api/gardes/tour").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(tour.replace("\"remplacer\":false", "\"remplacer\":true")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jours").value(5)).andExpect(jsonPath("$.remplacees").value(5));
    }

    @Test
    void un_tour_jour_et_nuit_met_la_meme_personne_de_garde_toute_la_journee() throws Exception {
        Long sfId = idDe("test.sf@casafami.test");
        mvc.perform(post("/api/gardes/tour").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"debut\":\"" + demain + "\",\"nombreJours\":2,\"periode\":\"JOUR_NUIT\",\"utilisateurIds\":[" + sfId + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jours").value(2));
        mvc.perform(get("/api/gardes").header("Authorization", medecin).param("du", demain.toString()).param("au", demain.toString()))
                .andExpect(jsonPath("$", hasSize(2))).andExpect(jsonPath("$[0].periode").value("JOUR")).andExpect(jsonPath("$[1].periode").value("NUIT"))
                .andExpect(jsonPath("$[0].utilisateurId").value(sfId)).andExpect(jsonPath("$[1].utilisateurId").value(sfId));
    }

    @Test
    void le_tour_automatique_met_une_personne_par_jour_selon_le_nombre_de_personnes_et_se_retablit() throws Exception {
        tokenFor("test.infirmiere@casafami.test", Role.INFIRMIERE);
        List<Long> equipe = equipe();
        int n = equipe.size();
        org.junit.jupiter.api.Assertions.assertTrue(n >= 3, "l'équipe de test compte au moins 3 personnes");
        String corps = "{\"debut\":\"" + demain + "\",\"nombreJours\":" + (2 * n) + "}";
        mvc.perform(post("/api/gardes/automatique").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jours").value(2 * n));
        // une seule personne de garde par jour (jour et nuit), et chacun revient tous les n jours
        for (int i = 0; i < 2 * n; i++) {
            LocalDate jour = demain.plusDays(i);
            Long attendu = equipe.get((int) Math.floorMod(jour.toEpochDay(), (long) n));
            mvc.perform(get("/api/gardes").header("Authorization", secretaire).param("du", jour.toString()).param("au", jour.toString()))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].utilisateurId").value(attendu)).andExpect(jsonPath("$[1].utilisateurId").value(attendu));
        }
        // quelqu'un change une garde à la main, puis « restaurer » rétablit exactement le tour automatique
        Long attenduDemain = equipe.get((int) Math.floorMod(demain.toEpochDay(), (long) n));
        Long autre = equipe.stream().filter(id -> !id.equals(attenduDemain)).findFirst().orElseThrow();
        String liste = mvc.perform(get("/api/gardes").header("Authorization", admin).param("du", demain.toString()).param("au", demain.toString())).andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(liste);
        org.junit.jupiter.api.Assertions.assertTrue(m.find());
        mvc.perform(delete("/api/gardes/" + m.group(1)).header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(post("/api/gardes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps(autre, demain, "JOUR"))).andExpect(status().isCreated());
        mvc.perform(post("/api/gardes/automatique").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jours").value(2 * n)).andExpect(jsonPath("$.remplacees").value(org.hamcrest.Matchers.greaterThan(0)));
        mvc.perform(get("/api/gardes").header("Authorization", admin).param("du", demain.toString()).param("au", demain.toString()))
                .andExpect(jsonPath("$", hasSize(2))).andExpect(jsonPath("$[0].utilisateurId").value(attenduDemain)).andExpect(jsonPath("$[1].utilisateurId").value(attenduDemain));
    }

    @Test
    void la_generation_auto_suit_le_tour_propose_par_l_administration() throws Exception {
        tokenFor("test.infirmiere@casafami.test", Role.INFIRMIERE);
        Long sfId = idDe("test.sf@casafami.test");
        Long medecinId = idDe("test.medecin@casafami.test");
        Long infId = idDe("test.infirmiere@casafami.test");
        List<Long> ordre = List.of(sfId, medecinId, infId);
        // pas encore de tour de référence ; et seule l'administration le consulte
        mvc.perform(get("/api/gardes/modele").header("Authorization", admin)).andExpect(status().isOk()).andExpect(content().string(""));
        mvc.perform(get("/api/gardes/modele").header("Authorization", medecin)).andExpect(status().isForbidden());
        // l'administration propose un tour : sage-femme, médecin, infirmière, à partir de demain
        mvc.perform(post("/api/gardes/tour").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"debut\":\"" + demain + "\",\"nombreJours\":3,\"periode\":\"JOUR_NUIT\",\"utilisateurIds\":[" + sfId + "," + medecinId + "," + infId + "]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/gardes/modele").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.debut").value(demain.toString())).andExpect(jsonPath("$.periode").value("JOUR_NUIT"))
                .andExpect(jsonPath("$.ids[0]").value(sfId)).andExpect(jsonPath("$.ids[1]").value(medecinId)).andExpect(jsonPath("$.ids[2]").value(infId));
        // la génération auto prolonge ce tour, bien plus tard : le jour n (depuis le départ) est confié à la personne n modulo 3
        LocalDate debutAuto = demain.plusDays(10);
        mvc.perform(post("/api/gardes/automatique").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"debut\":\"" + debutAuto + "\",\"nombreJours\":7}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("tour")).andExpect(jsonPath("$.jours").value(7));
        for (int k = 0; k < 7; k++) {
            LocalDate jour = debutAuto.plusDays(k);
            Long attendu = ordre.get((10 + k) % 3);
            mvc.perform(get("/api/gardes").header("Authorization", secretaire).param("du", jour.toString()).param("au", jour.toString()))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].utilisateurId").value(attendu)).andExpect(jsonPath("$[1].utilisateurId").value(attendu));
        }
        // une garde changée à la main est rétablie par la génération auto
        mvc.perform(post("/api/gardes/automatique").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"debut\":\"" + debutAuto + "\",\"nombreJours\":7}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.remplacees").value(14));
    }

    @Test
    void la_remise_a_zero_efface_toutes_les_gardes_et_le_tour_de_reference() throws Exception {
        Long sfId = idDe("test.sf@casafami.test");
        mvc.perform(post("/api/gardes/tour").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"debut\":\"" + demain + "\",\"nombreJours\":4,\"periode\":\"JOUR_NUIT\",\"utilisateurIds\":[" + sfId + "]}")).andExpect(status().isOk());
        // un médecin ne peut pas remettre à zéro
        mvc.perform(delete("/api/gardes").header("Authorization", medecin)).andExpect(status().isForbidden());
        org.junit.jupiter.api.Assertions.assertEquals(8, gardes.count());
        mvc.perform(delete("/api/gardes").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.supprimees").value(8)).andExpect(jsonPath("$.tourEfface").value(true));
        org.junit.jupiter.api.Assertions.assertEquals(0, gardes.count());
        org.junit.jupiter.api.Assertions.assertEquals(0, modeles.count());
        mvc.perform(get("/api/gardes").header("Authorization", secretaire).param("du", demain.toString()).param("au", demain.plusDays(10).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/gardes/modele").header("Authorization", admin)).andExpect(status().isOk()).andExpect(content().string(""));
        // recommencer à zéro est sans danger
        mvc.perform(delete("/api/gardes").header("Authorization", admin)).andExpect(status().isOk()).andExpect(jsonPath("$.supprimees").value(0)).andExpect(jsonPath("$.tourEfface").value(false));
    }

    @Test
    void la_personne_de_garde_aujourd_hui_le_sait() throws Exception {
        Long sfId = idDe("test.sf@casafami.test");
        LocalDate aujourdhui = LocalDate.now(ZoneId.of("Indian/Antananarivo"));
        mvc.perform(post("/api/gardes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps(sfId, aujourdhui, "JOUR"))).andExpect(status().isCreated());
        mvc.perform(post("/api/gardes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps(sfId, aujourdhui, "NUIT"))).andExpect(status().isCreated());
        // la sage-femme de garde est avertie qu'elle l'est (jour et nuit) ; les autres comptes non
        mvc.perform(get("/api/gardes/aujourdhui").header("Authorization", sageFemme))
                .andExpect(status().isOk()).andExpect(jsonPath("$.moi", hasSize(2))).andExpect(jsonPath("$.moi[0]").value("JOUR")).andExpect(jsonPath("$.moi[1]").value("NUIT"))
                .andExpect(jsonPath("$.gardes", hasSize(2)));
        mvc.perform(get("/api/gardes/aujourdhui").header("Authorization", medecin)).andExpect(status().isOk()).andExpect(jsonPath("$.moi", hasSize(0)));
        mvc.perform(get("/api/gardes/aujourdhui").header("Authorization", secretaire)).andExpect(status().isOk()).andExpect(jsonPath("$.moi", hasSize(0)));
    }

    @Test
    void une_garde_se_retire_et_les_donnees_invalides_sont_refusees() throws Exception {
        Long medecinId = idDe("test.medecin@casafami.test");
        MvcResult cree = mvc.perform(post("/api/gardes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps(medecinId, demain, "JOUR")))
                .andExpect(status().isCreated()).andReturn();
        String id = extraire(cree, "id");
        mvc.perform(delete("/api/gardes/" + id).header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/gardes/" + id).header("Authorization", admin)).andExpect(status().isNotFound());
        // période inconnue, personne inconnue, période de dates à l'envers, tour trop long
        mvc.perform(post("/api/gardes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps(medecinId, demain, "SOIR"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/gardes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps(999999L, demain, "JOUR"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/gardes").header("Authorization", admin).param("du", demain.toString()).param("au", demain.minusDays(2).toString())).andExpect(status().isBadRequest());
        mvc.perform(post("/api/gardes/tour").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"debut\":\"" + demain + "\",\"nombreJours\":200,\"periode\":\"JOUR\",\"utilisateurIds\":[" + medecinId + "]}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/gardes/tour").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"debut\":\"" + demain + "\",\"nombreJours\":3,\"periode\":\"JOUR\",\"utilisateurIds\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/gardes/automatique").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"debut\":\"" + demain + "\",\"nombreJours\":0}")).andExpect(status().isBadRequest());
    }
}
