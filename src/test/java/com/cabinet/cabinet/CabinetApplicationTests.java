package com.cabinet.cabinet;

import com.cabinet.cabinet.entity.RendezVous;
import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.enums.Role;
import com.cabinet.cabinet.repository.MaladieRepository;
import com.cabinet.cabinet.repository.RendezVousRepository;
import com.cabinet.cabinet.repository.ServiceRepository;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import com.cabinet.cabinet.security.CustomUserDetailsService;
import com.cabinet.cabinet.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration de l'API sur la base de test « casafami_test » (profil « test »).
 * Ils couvrent les anomalies corrigées : droits d'accès, codes HTTP, règles de créneaux et filtres du tableau de bord.
 */
@SpringBootTest
@ActiveProfiles("test")
class CabinetApplicationTests {

    private static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");

    @Autowired WebApplicationContext context;
    @Autowired RendezVousRepository rdvs;
    @Autowired UtilisateurRepository users;
    @Autowired ServiceRepository services;
    @Autowired MaladieRepository maladies;
    @Autowired JwtService jwtService;
    @Autowired CustomUserDetailsService userDetailsService;
    @Autowired PasswordEncoder encoder;

    MockMvc mvc;
    String adminToken, medecinToken, secretaireToken;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        rdvs.deleteAll();
        maladies.deleteAll();
        services.deleteAll();
        planningLundiVendredi();
        adminToken = tokenFor("test.admin@casafami.test", Role.SUPER_ADMIN);
        medecinToken = tokenFor("test.medecin@casafami.test", Role.MEDECIN);
        secretaireToken = tokenFor("test.secretaire@casafami.test", Role.SECRETAIRE);
    }

    /** Ces tests supposent un cabinet fermé le week-end : on le configure explicitement (le défaut du produit est 7 jours sur 7). */
    private void planningLundiVendredi() {
        StringBuilder jours = new StringBuilder();
        for (int j = 1; j <= 7; j++) {
            boolean ouvert = j <= 5;
            jours.append("{\"jourSemaine\":%d,\"ouvert\":%b,\"matinDebut\":%s,\"matinFin\":%s,\"apresMidiDebut\":%s,\"apresMidiFin\":%s}%s".formatted(j, ouvert,
                    ouvert ? "\"08:00\"" : "null", ouvert ? "\"11:00\"" : "null", ouvert ? "\"14:00\"" : "null", ouvert ? "\"17:00\"" : "null", j < 7 ? "," : ""));
        }
        try {
            mvc.perform(put("/api/admin/planning").header("Authorization", tokenFor("test.admin@casafami.test", Role.SUPER_ADMIN))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"dureeCreneauMinutes\":30,\"capaciteParCreneau\":1,\"horaires\":[" + jours + "]}")).andExpect(status().isOk());
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private String tokenFor(String email, Role role) {
        users.findByEmailIgnoreCase(email).orElseGet(() -> {
            Utilisateur u = new Utilisateur();
            u.setNom("Test " + role);
            u.setEmail(email);
            u.setMotDePasse(encoder.encode("motdepasse-test-1"));
            u.setRole(role);
            return users.save(u);
        });
        return "Bearer " + jwtService.genererToken(userDetailsService.loadUserByUsername(email));
    }

    /** Jour ouvré situé `offset + 1` jours ouvrés après aujourd'hui. */
    private LocalDate jourOuvre(int offset) {
        LocalDate d = LocalDate.now(ZONE);
        int restant = offset + 1;
        while (restant > 0) {
            d = d.plusDays(1);
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) restant--;
        }
        return d;
    }

    private String rdvJson(LocalDate date, String heure, String telephone) {
        return """
            {"nomClient":"Alice Rakoto","ageClient":28,"telephoneClient":"%s","motif":"Suivi",
             "dateRdv":"%s","heureRdv":"%s"}""".formatted(telephone, date, heure);
    }

    private MvcResult creer(LocalDate date, String heure) throws Exception {
        return mvc.perform(post("/api/rdv").contentType(MediaType.APPLICATION_JSON).content(rdvJson(date, heure, "034 12 345 67")))
                .andReturn();
    }

    private static String extraire(MvcResult result, String champ) throws Exception {
        Matcher m = Pattern.compile("\"" + champ + "\":\\s*\"?([^\",}]+)").matcher(result.getResponse().getContentAsString());
        assertTrue(m.find(), "champ absent : " + champ + " dans " + result.getResponse().getContentAsString());
        return m.group(1);
    }

    // ---------------------------------------------------------------- prise de rendez-vous

    @Test
    void priseDeRdvAcceptePlaceholderAvecEspacesEtNormaliseLeNumero() throws Exception {
        MvcResult r = creer(jourOuvre(1), "08:00:00");
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals("0341234567", extraire(r, "telephoneClient"));
        assertFalse(extraire(r, "clientAccessToken").isBlank());
    }

    @Test
    void ageZeroAccepte_telephoneInvalideEtWeekendRefuses() throws Exception {
        mvc.perform(post("/api/rdv").contentType(MediaType.APPLICATION_JSON)
                .content(rdvJson(jourOuvre(1), "09:00:00", "034 12 345 67").replace("\"ageClient\":28", "\"ageClient\":0")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/rdv").contentType(MediaType.APPLICATION_JSON).content(rdvJson(jourOuvre(1), "09:30:00", "abc")))
                .andExpect(status().isBadRequest());
        LocalDate samedi = LocalDate.now(ZONE);
        while (samedi.getDayOfWeek() != DayOfWeek.SATURDAY) samedi = samedi.plusDays(1);
        mvc.perform(post("/api/rdv").contentType(MediaType.APPLICATION_JSON).content(rdvJson(samedi, "09:30:00", "0341234567")))
                .andExpect(status().isConflict());
    }

    @Test
    void creneauDejaPrisRefuseMaisRedevientLibreApresAnnulation() throws Exception {
        LocalDate date = jourOuvre(2);
        MvcResult premier = creer(date, "10:00:00");
        assertEquals(201, premier.getResponse().getStatus());
        assertEquals(409, creer(date, "10:00:00").getResponse().getStatus());

        String id = extraire(premier, "id");
        String token = extraire(premier, "clientAccessToken");
        mvc.perform(put("/api/rdv/" + id + "/annuler").header("X-RDV-Access-Token", token)).andExpect(status().isOk());

        // le créneau d'un rendez-vous annulé est de nouveau proposé et réservable
        mvc.perform(get("/api/rdv/creneaux/" + date)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("10:00")));
        assertEquals(201, creer(date, "10:00:00").getResponse().getStatus());
    }

    @Test
    void creneauxDuJourNeProposentPasLesHeuresPassees() throws Exception {
        LocalDate aujourdhui = LocalDate.now(ZONE);
        MvcResult r = mvc.perform(get("/api/rdv/creneaux/" + aujourdhui)).andExpect(status().isOk()).andReturn();
        LocalTime maintenant = LocalDateTime.now(ZONE).toLocalTime();
        Matcher m = Pattern.compile("(\\d\\d:\\d\\d)").matcher(r.getResponse().getContentAsString());
        while (m.find()) {
            assertTrue(LocalTime.parse(m.group(1)).isAfter(maintenant.minusMinutes(1)), "créneau passé proposé : " + m.group(1));
        }
    }

    @Test
    void dateIllisibleDonne400Pas500() throws Exception {
        mvc.perform(get("/api/rdv/creneaux/pas-une-date")).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- annulation sécurisée

    @Test
    void annulationAnonymeSansJetonOuAvecMauvaisJetonRefusee() throws Exception {
        MvcResult r = creer(jourOuvre(3), "14:00:00");
        String id = extraire(r, "id");
        mvc.perform(put("/api/rdv/" + id + "/annuler")).andExpect(status().isForbidden());
        mvc.perform(put("/api/rdv/" + id + "/annuler").header("X-RDV-Access-Token", "faux")).andExpect(status().isForbidden());
        assertEquals("EN_ATTENTE", rdvs.findById(Long.parseLong(id)).orElseThrow().getStatut().name());
    }

    @Test
    void personnelPeutAnnulerSansJeton_etRdvInconnuDonne404() throws Exception {
        String id = extraire(creer(jourOuvre(3), "14:30:00"), "id");
        mvc.perform(put("/api/rdv/" + id + "/annuler").header("Authorization", secretaireToken)).andExpect(status().isOk());
        mvc.perform(put("/api/rdv/999999/annuler").header("Authorization", secretaireToken)).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- droits d'accès

    @Test
    void maladiesNeSontPlusPubliques() throws Exception {
        mvc.perform(get("/api/maladies")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/maladies").contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"X\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/maladies/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/maladies").header("Authorization", secretaireToken)).andExpect(status().isForbidden());
    }

    @Test
    void medecinCreeUneMaladieAvecSesTraitements() throws Exception {
        String body = """
            {"nom":"Anémie","description":"d","symptomes":"fatigue",
             "traitements":[{"medicament":"Fer","posologie":"1/jour"}]}""";
        mvc.perform(post("/api/maladies").header("Authorization", medecinToken).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/maladies").header("Authorization", medecinToken)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Fer")));
        mvc.perform(post("/api/maladies").header("Authorization", medecinToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void statutInvalideDonne400_etRoleInsuffisantDonne403() throws Exception {
        mvc.perform(get("/api/rdv/statut/NIMPORTEQUOI").header("Authorization", medecinToken)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/rdv/statut/CONFIRME").header("Authorization", secretaireToken)).andExpect(status().isForbidden());
    }

    @Test
    void jetonDUnCompteSupprimeDonne401Pas500() throws Exception {
        String bearer = tokenFor("test.ephemere@casafami.test", Role.SECRETAIRE);
        users.delete(users.findByEmailIgnoreCase("test.ephemere@casafami.test").orElseThrow());
        mvc.perform(get("/api/rdv").header("Authorization", bearer)).andExpect(status().isUnauthorized());
    }

    @Test
    void erreursDeGestionAdminGardentLeurCodeHttp() throws Exception {
        String doublon = """
            {"nom":"Dup","email":"TEST.ADMIN@casafami.test","motDePasse":"unmotdepasse-long","role":"SECRETAIRE"}""";
        mvc.perform(post("/api/admin/users").header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON).content(doublon))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/admin/users/999999/role").header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEDECIN\"}")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/admin/users/1/role").header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"PIRATE\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/users").header("Authorization", secretaireToken).contentType(MediaType.APPLICATION_JSON).content(doublon))
                .andExpect(status().isForbidden());
    }

    @Test
    void preflightCorsAutoriseLaMethodePatch() throws Exception {
        mvc.perform(options("/api/admin/users/1/role").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "PATCH").header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void connexionInsensibleALaCasseDeLEmail() throws Exception {
        tokenFor("test.casse@casafami.test", Role.SECRETAIRE);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"Test.CASSE@casafami.test\",\"motDePasse\":\"motdepasse-test-1\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test.casse@casafami.test\",\"motDePasse\":\"mauvais\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void imagesUploadsSontAccessiblesSansConnexion() throws Exception {
        // un 404 (fichier absent) prouve que la sécurité laisse passer ; un 401 prouverait l'ancien défaut
        mvc.perform(get("/uploads/services/inexistant.png")).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- tableau de bord

    @Test
    void cycleDeVieConfirmerPuisTerminer_etDroitsParRole() throws Exception {
        String id = extraire(creer(jourOuvre(1), "15:00:00"), "id");
        Long idMedecin = users.findByEmailIgnoreCase("test.medecin@casafami.test").orElseThrow().getId();
        mvc.perform(put("/api/rdv/" + id + "/terminer").header("Authorization", adminToken)).andExpect(status().isBadRequest()); // pas encore confirmé
        mvc.perform(put("/api/rdv/" + id + "/confirmer").header("Authorization", secretaireToken)).andExpect(status().isForbidden());
        // un médecin ne peut pas s'attribuer lui-même un rendez-vous : c'est le super administrateur qui décide
        mvc.perform(put("/api/rdv/" + id + "/confirmer").header("Authorization", medecinToken)).andExpect(status().isForbidden());
        mvc.perform(put("/api/rdv/" + id + "/confirmer").header("Authorization", adminToken)).andExpect(status().isBadRequest()); // il faut choisir la personne
        mvc.perform(put("/api/rdv/" + id + "/confirmer").header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"praticienId\":" + idMedecin + "}")).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("CONFIRME"))
                .andExpect(jsonPath("$.praticienId").value(idMedecin));
        mvc.perform(put("/api/rdv/" + id + "/terminer").header("Authorization", medecinToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("TERMINE"));
    }

    @Test
    void medecinVoitLesRdvSansPraticienDansSesStatistiques() throws Exception {
        LocalDate demain = jourOuvre(0);
        creer(demain, "16:00:00");
        LocalDate aujourdhui = LocalDate.now(ZONE);
        if (demain.getMonth() != aujourdhui.getMonth()) return; // le RDV sort de la fenêtre « ce mois »
        String medecin = mvc.perform(get("/api/rdv/dashboard?periode=ce_mois").header("Authorization", medecinToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String admin = mvc.perform(get("/api/rdv/dashboard?periode=ce_mois").header("Authorization", adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(medecin.contains("\"total\":1"), "le médecin doit voir le RDV non attribué : " + medecin);
        assertTrue(admin.contains("\"total\":1"), admin);
    }

    @Test
    void suppressionDUnServiceUtiliseParUnRdvReussit() throws Exception {
        com.cabinet.cabinet.entity.Service s = new com.cabinet.cabinet.entity.Service();
        s.setNom("Echo");
        s.setDescription("d");
        s = services.save(s);
        mvc.perform(post("/api/rdv").contentType(MediaType.APPLICATION_JSON).content(
                rdvJson(jourOuvre(4), "08:30:00", "0341234567").replace("\"motif\"", "\"serviceId\":" + s.getId() + ",\"motif\"")))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/services/" + s.getId()).header("Authorization", adminToken)).andExpect(status().isNoContent());
        assertEquals(1, rdvs.count());
    }

    @Test
    void themeSansCouleurDonne400() throws Exception {
        mvc.perform(put("/api/theme").header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"primaryColor\":\"#112233\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void restaurationRefuseeSiLeCreneauAEteRepris() throws Exception {
        LocalDate date = jourOuvre(5);
        String id = extraire(creer(date, "08:00:00"), "id");
        mvc.perform(delete("/api/rdv/" + id).header("Authorization", secretaireToken)).andExpect(status().isNoContent());
        assertEquals(201, creer(date, "08:00:00").getResponse().getStatus()); // le créneau est libéré par la corbeille
        mvc.perform(put("/api/rdv/" + id + "/restaurer").header("Authorization", secretaireToken)).andExpect(status().isConflict());
    }

    @Test
    void rdvPassePeutEtreCorrigeSansChangerLeCreneau() throws Exception {
        String id = extraire(creer(jourOuvre(1), "09:00:00"), "id");
        RendezVous rdv = rdvs.findById(Long.parseLong(id)).orElseThrow();
        rdv.setDateRdv(LocalDate.now(ZONE).minusDays(7)); // simule un rendez-vous déjà passé
        rdvs.save(rdv);
        String corrige = rdvJson(rdv.getDateRdv(), "09:00:00", "0349999999");
        mvc.perform(put("/api/rdv/" + id).header("Authorization", secretaireToken).contentType(MediaType.APPLICATION_JSON).content(corrige))
                .andExpect(status().isOk()).andExpect(jsonPath("$.telephoneClient").value("0349999999"));
    }
}
