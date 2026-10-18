package com.cabinet.cabinet;

import com.cabinet.cabinet.entity.RendezVous;
import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.enums.Role;
import com.cabinet.cabinet.enums.StatutRDV;
import com.cabinet.cabinet.repository.*;
import com.cabinet.cabinet.security.CustomUserDetailsService;
import com.cabinet.cabinet.security.JwtService;
import com.cabinet.cabinet.service.RappelService;
import com.cabinet.cabinet.util.PhoneUtils;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cabinet.cabinet.config.DonneesReparateur;
import com.cabinet.cabinet.service.PlanningService;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests des fonctions ajoutées : téléphone et recherche, planning, dossier patient, comptes, éditeur du site,
 * statistiques, exports, rappels et journal d'audit (base de test « casafami_test »).
 */
@SpringBootTest
@ActiveProfiles("test")
class FonctionnalitesTests {
    private static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");

    @Autowired WebApplicationContext context;
    @Autowired RendezVousRepository rdvs;
    @Autowired UtilisateurRepository users;
    @Autowired ServiceRepository services;
    @Autowired MaladieRepository maladies;
    @Autowired ConsultationRepository consultations;
    @Autowired DossierPatientRepository dossiers;
    @Autowired SiteContentRepository siteContent;
    @Autowired JourFermeRepository joursFermes;
    @Autowired JwtService jwtService;
    @Autowired CustomUserDetailsService userDetailsService;
    @Autowired PasswordEncoder encoder;
    @Autowired RappelService rappelService;
    @Autowired PlanningService planningService;
    @Autowired HoraireJourRepository horaireJours;
    @Autowired ParametresCabinetRepository parametresCabinet;
    @Autowired JdbcTemplate jdbc;
    @Autowired DonneesReparateur reparateur;
    @Autowired com.cabinet.cabinet.service.RendezVousService rendezVousService;
    @Autowired NotificationRepository notificationRepository;

    MockMvc mvc;
    String admin, medecin, sageFemme, secretaire;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        notificationRepository.deleteAll();
        rdvs.deleteAll();
        maladies.deleteAll();
        services.deleteAll();
        consultations.deleteAll();
        dossiers.deleteAll();
        siteContent.deleteAll();
        joursFermes.deleteAll();
        admin = tokenFor("test.admin@casafami.test", Role.SUPER_ADMIN);
        medecin = tokenFor("test.medecin@casafami.test", Role.MEDECIN);
        sageFemme = tokenFor("test.sf@casafami.test", Role.SAGE_FEMME);
        secretaire = tokenFor("test.secretaire@casafami.test", Role.SECRETAIRE);
        planningParDefaut();
    }

    private void planningParDefaut() throws Exception {
        mvc.perform(put("/api/admin/planning").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(planningJson(30, 1, "08:00", "11:00", "14:00", "17:00")))
                .andExpect(status().isOk());
    }

    private String planningJson(int duree, int capacite, String matinDebut, String matinFin, String apresDebut, String apresFin) {
        StringBuilder jours = new StringBuilder();
        for (int j = 1; j <= 7; j++) {
            boolean ouvert = j <= 5;
            jours.append("{\"jourSemaine\":%d,\"ouvert\":%b,\"matinDebut\":%s,\"matinFin\":%s,\"apresMidiDebut\":%s,\"apresMidiFin\":%s}%s".formatted(j, ouvert,
                    ouvert ? "\"" + matinDebut + "\"" : "null", ouvert ? "\"" + matinFin + "\"" : "null",
                    ouvert ? "\"" + apresDebut + "\"" : "null", ouvert ? "\"" + apresFin + "\"" : "null", j < 7 ? "," : ""));
        }
        return "{\"dureeCreneauMinutes\":%d,\"capaciteParCreneau\":%d,\"horaires\":[%s]}".formatted(duree, capacite, jours);
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

    private LocalDate jourOuvre(int offset) {
        LocalDate d = LocalDate.now(ZONE);
        int restant = offset + 1;
        while (restant > 0) {
            d = d.plusDays(1);
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) restant--;
        }
        return d;
    }

    private String rdvJson(LocalDate date, String heure, String nom, String telephone, Long praticienId) {
        return """
            {"nomClient":"%s","ageClient":28,"telephoneClient":"%s","motif":"Suivi","dateRdv":"%s","heureRdv":"%s"%s}"""
                .formatted(nom, telephone, date, heure, praticienId == null ? "" : ",\"praticienId\":" + praticienId);
    }

    private MvcResult creer(LocalDate date, String heure, String nom, String telephone) throws Exception {
        return mvc.perform(post("/api/rdv").contentType(MediaType.APPLICATION_JSON).content(rdvJson(date, heure, nom, telephone, null))).andReturn();
    }

    private static String extraire(MvcResult result, String champ) throws Exception {
        Matcher m = Pattern.compile("\"" + champ + "\":\\s*\"?([^\",}]+)").matcher(result.getResponse().getContentAsString());
        assertTrue(m.find(), "champ absent : " + champ + " dans " + result.getResponse().getContentAsString());
        return m.group(1);
    }

    // ---------------------------------------------------------------- téléphone

    @Test
    void numerosDeTousFormatsDonnentLaMemeFormeCanonique() {
        for (String saisie : new String[]{"034 12 345 67", "0341234567", "34 12 345 67", "261 34 12 345 67", "261341234567", "00261341234567", "034.12.345.67"}) {
            assertEquals("0341234567", PhoneUtils.canonical(saisie), saisie);
            assertEquals("261341234567", PhoneUtils.searchKey(saisie), saisie);
        }
        assertEquals("+33612345678", PhoneUtils.canonical("+33 6 12 34 56 78"));
        assertNull(PhoneUtils.canonical("12345"));
        assertEquals("+" + "26" + "1341234567", PhoneUtils.toInternational("0341234567"));
    }

    @Test
    void rechercheDuPersonnelParNumeroSansIndicatifEtParNom() throws Exception {
        creer(jourOuvre(1), "08:00:00", "Héry Rakoto", "261 34 12 345 67");
        // enregistré sous la forme locale
        assertEquals("0341234567", rdvs.findAll().get(0).getTelephoneClient());
        for (String q : new String[]{"034 12 345 67", "34 12 345 67", "261341234567", "0341234567"}) {
            mvc.perform(get("/api/rdv/recherche").param("q", q).header("Authorization", secretaire)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }
        // nom complet, sans tenir compte de la casse ni des accents
        mvc.perform(get("/api/rdv/recherche").param("q", "hery  RAKOTO").header("Authorization", secretaire)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        // un ou plusieurs mots, dans n'importe quel ordre, début de mot suffisant
        for (String q : new String[]{"rakoto", "hery", "rak her", "Rakoto Héry", "HERY"}) {
            mvc.perform(get("/api/rdv/recherche").param("q", q).header("Authorization", secretaire)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        }
        mvc.perform(get("/api/rdv/recherche").param("q", "razafy").header("Authorization", secretaire)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/rdv/recherche").param("q", "ab").header("Authorization", secretaire)).andExpect(status().isBadRequest());
    }

    @Test
    void unVisiteurNePeutPlusChercherParNomNiParNumero() throws Exception {
        creer(jourOuvre(1), "08:00:00", "Héry Rakoto", "0341234567");
        for (String url : new String[]{"/api/rdv/recherche?q=rakoto", "/api/rdv/recherche?q=0341234567", "/api/rdv/telephone/0341234567"}) {
            mvc.perform(get(url)).andExpect(status().is4xxClientError()).andExpect(content().string(not(containsString("Rakoto"))));
        }
    }

    @Test
    void lePatientRetrouveSonRdvAvecSaCleDeSuiviEtSeulementAvecElle() throws Exception {
        MvcResult cree = creer(jourOuvre(1), "08:00:00", "Héry Rakoto", "0341234567");
        String cle = extraire(cree, "cleSuivi");
        assertTrue(cle.matches("CF-[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}"), cle);
        assertEquals(cle, rdvs.findAll().get(0).getCleSuivi());
        // la clé se tape avec ou sans tirets, en minuscules : le motif (information de santé) n'est jamais renvoyé
        for (String saisie : new String[]{cle, cle.toLowerCase(), cle.replace("-", ""), cle.substring(3)}) {
            mvc.perform(get("/api/rdv/suivi/" + saisie)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.nomClient").value("Héry Rakoto")).andExpect(jsonPath("$.cleSuivi").value(cle))
                    .andExpect(jsonPath("$.motif").doesNotExist());
        }
        // adresse à part : les clés fausses comptent dans la limite de sa propre adresse (voir le test suivant)
        org.springframework.test.web.servlet.request.RequestPostProcessor ailleurs = r -> { r.setRemoteAddr("10.20.30.41"); return r; };
        mvc.perform(get("/api/rdv/suivi/CF-AAAA-BBBB").with(ailleurs)).andExpect(status().isNotFound());
        mvc.perform(get("/api/rdv/suivi/n-importe-quoi").with(ailleurs)).andExpect(status().isNotFound());
        // la clé donne aussi le droit de télécharger le reçu et d'annuler, sans session
        String id = extraire(cree, "id");
        mvc.perform(get("/api/rdv/" + id + "/pdf").header("X-RDV-Access-Token", cle)).andExpect(status().isOk());
        mvc.perform(get("/api/rdv/" + id + "/pdf").header("X-RDV-Access-Token", "CF-AAAA-BBBB")).andExpect(status().isForbidden());
        mvc.perform(put("/api/rdv/" + id + "/annuler").header("X-RDV-Access-Token", "CF-AAAA-BBBB")).andExpect(status().isForbidden());
        mvc.perform(put("/api/rdv/" + id + "/annuler").header("X-RDV-Access-Token", cle.toLowerCase())).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ANNULE"));
        // chaque rendez-vous a sa propre clé
        MvcResult autre = creer(jourOuvre(5), "09:00:00", "Zo Andry", "0329988776");
        assertNotEquals(cle, extraire(autre, "cleSuivi"));
    }

    private RendezVous rdvDans(java.time.Duration delai, String cle) {
        java.time.LocalDateTime debut = java.time.LocalDateTime.now(ZONE).plus(delai);
        RendezVous r = new RendezVous();
        r.setNomClient("Patient " + cle);
        r.setAgeClient(30);
        r.setTelephoneClient("0341234567");
        r.setDateRdv(debut.toLocalDate());
        r.setHeureRdv(debut.toLocalTime().withSecond(0).withNano(0));
        r.setStatut(StatutRDV.CONFIRME);
        r.setCleSuivi(cle);
        return rdvs.save(r);
    }

    @Test
    void lePatientNePeutPlusAnnulerAMoinsDeDeuxHeuresMaisLePersonnelSi() throws Exception {
        RendezVous proche = rdvDans(java.time.Duration.ofMinutes(100), "CF-ZZZZ-3333");
        RendezVous lointain = rdvDans(java.time.Duration.ofMinutes(150), "CF-ZZZZ-4444");
        mvc.perform(put("/api/rdv/" + proche.getId() + "/annuler").header("X-RDV-Access-Token", "CF-ZZZZ-3333"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("moins de 2 heures")));
        assertEquals(StatutRDV.CONFIRME, rdvs.findById(proche.getId()).orElseThrow().getStatut());
        // le personnel n'est pas concerné, et au-delà de 2 heures le patient peut annuler
        mvc.perform(put("/api/rdv/" + proche.getId() + "/annuler").header("Authorization", secretaire)).andExpect(status().isOk());
        mvc.perform(put("/api/rdv/" + lointain.getId() + "/annuler").header("X-RDV-Access-Token", "CF-ZZZZ-4444"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("ANNULE"));
    }

    @Test
    void lesEssaisDeClesIncorrectesSontLimitesParAdresse() throws Exception {
        String cle = extraire(creer(jourOuvre(1), "08:00:00", "Héry Rakoto", "0341234567"), "cleSuivi");
        org.springframework.test.web.servlet.request.RequestPostProcessor depuis = r -> { r.setRemoteAddr("10.20.30.40"); return r; };
        for (int i = 0; i < 10; i++) mvc.perform(get("/api/rdv/suivi/CF-ZZZZ-" + (2222 + i)).with(depuis)).andExpect(status().isNotFound());
        // après dix clés fausses, même la bonne clé est refusée un moment pour cette adresse...
        mvc.perform(get("/api/rdv/suivi/" + cle).with(depuis)).andExpect(status().isTooManyRequests());
        // ... mais pas pour les autres visiteurs
        mvc.perform(get("/api/rdv/suivi/" + cle)).andExpect(status().isOk());
    }

    @Test
    void lesRdvDejaEnregistresRecoiventUneCleAuDemarrage() throws Exception {
        jdbc.update("insert into rendez_vous (nom_client, age_client, telephone_client, date_rdv, heure_rdv, statut, date_creation, supprime) values ('Sans Cle', 30, '0341111111', current_date + 40, '09:00', 'EN_ATTENTE', now(), false)");
        assertTrue(rendezVousService.attribuerClesManquantes() >= 1);
        String cle = jdbc.queryForObject("select cle_suivi from rendez_vous where nom_client = 'Sans Cle'", String.class);
        assertNotNull(cle);
        mvc.perform(get("/api/rdv/suivi/" + cle)).andExpect(status().isOk()).andExpect(jsonPath("$.nomClient").value("Sans Cle"));
        assertEquals(0, rendezVousService.attribuerClesManquantes(), "une seconde passe ne change plus rien");
    }

    @Test
    void rechercheRapideDuPersonnelParNomAccentueOuNumeroFormate() throws Exception {
        creer(jourOuvre(1), "08:00:00", "Héry Rakoto", "0341234567");
        creer(jourOuvre(1), "09:00:00", "Zo Andry", "0329988776");
        mvc.perform(get("/api/patients/recherche").param("q", "hery").header("Authorization", secretaire))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].telephone").value("0341234567"));
        mvc.perform(get("/api/patients/recherche").param("q", "034 12").header("Authorization", secretaire))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/patients/recherche").param("q", "261 32 99").header("Authorization", secretaire))
                .andExpect(jsonPath("$[0].nom").value("Zo Andry"));
        mvc.perform(get("/api/patients/recherche").param("q", "a%").header("Authorization", secretaire)).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- planning

    @Test
    void capaciteDeuxAutoriseDeuxRdvSurLeMemeCreneau() throws Exception {
        mvc.perform(put("/api/admin/planning").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(planningJson(30, 2, "08:00", "11:00", "14:00", "17:00"))).andExpect(status().isOk());
        LocalDate date = jourOuvre(2);
        assertEquals(201, creer(date, "09:00:00", "Patiente Un", "0341111111").getResponse().getStatus());
        assertEquals(201, creer(date, "09:00:00", "Patiente Deux", "0342222222").getResponse().getStatus());
        assertEquals(409, creer(date, "09:00:00", "Patiente Trois", "0343333333").getResponse().getStatus());
        mvc.perform(get("/api/rdv/creneaux/" + date)).andExpect(content().string(not(containsString("09:00"))));
    }

    @Test
    void unPraticienNePeutPasEtreReserveDeuxFoisAuMemeCreneau() throws Exception {
        mvc.perform(put("/api/admin/planning").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(planningJson(30, 2, "08:00", "11:00", "14:00", "17:00"))).andExpect(status().isOk());
        Long idMedecin = users.findByEmailIgnoreCase("test.medecin@casafami.test").orElseThrow().getId();
        LocalDate date = jourOuvre(2);
        mvc.perform(post("/api/rdv").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(rdvJson(date, "10:00:00", "Un", "0341111111", idMedecin))).andExpect(status().isCreated());
        mvc.perform(post("/api/rdv").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(rdvJson(date, "10:00:00", "Deux", "0342222222", idMedecin)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(containsString("praticien")));
        // sans praticien : le créneau reste réservable (capacité 2), et il est encore proposé aux patients sans préférence
        mvc.perform(get("/api/rdv/creneaux").param("date", date.toString())).andExpect(content().string(containsString("10:00")));
        mvc.perform(get("/api/rdv/creneaux").param("date", date.toString()).param("praticienId", String.valueOf(idMedecin)))
                .andExpect(content().string(not(containsString("10:00"))));
    }

    @Test
    void jourFermeEtHorairesPersonnalises() throws Exception {
        LocalDate date = jourOuvre(3);
        mvc.perform(post("/api/admin/jours-fermes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + date + "\",\"motif\":\"Formation\"}")).andExpect(status().isCreated());
        mvc.perform(get("/api/rdv/creneaux/" + date)).andExpect(content().json("[]"));
        mvc.perform(get("/api/planning/jour/" + date)).andExpect(jsonPath("$.ferme").value(true)).andExpect(jsonPath("$.motif").value("Formation"));
        mvc.perform(post("/api/rdv").contentType(MediaType.APPLICATION_JSON).content(rdvJson(date, "09:00:00", "Fermé", "0341111111", null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(containsString("fermé")));
        mvc.perform(post("/api/admin/jours-fermes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + date + "\"}")).andExpect(status().isConflict());

        // créneaux de 60 min de 9h à 12h (3 créneaux) le matin, rien l'après-midi → 3 créneaux
        mvc.perform(put("/api/admin/planning").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(planningJson(60, 1, "09:00", "12:00", "14:00", "14:00".replace("14:00", "15:00")))).andExpect(status().isOk());
        LocalDate autre = jourOuvre(5);
        mvc.perform(get("/api/rdv/creneaux/" + autre)).andExpect(jsonPath("$.length()").value(4)); // 9h,10h,11h + 14h
    }

    @Test
    void planningPublicEnLecture_etModificationReserveeAuSuperAdmin() throws Exception {
        mvc.perform(get("/api/planning")).andExpect(status().isOk()).andExpect(jsonPath("$.horaires.length()").value(7));
        String corps = planningJson(30, 1, "08:00", "11:00", "14:00", "17:00");
        mvc.perform(put("/api/admin/planning").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/planning").contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/planning").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(planningJson(5, 1, "08:00", "11:00", "14:00", "17:00"))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/planning").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(planningJson(30, 1, "11:00", "08:00", "14:00", "17:00"))).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- dossier patient

    @Test
    void dossierPatientReserveAuPersonnelSoignant() throws Exception {
        String id = extraire(creer(jourOuvre(1), "08:30:00", "Mialy Rabe", "0345550000"), "id");
        mvc.perform(get("/api/dossiers/0345550000").header("Authorization", secretaire)).andExpect(status().isForbidden());
        mvc.perform(get("/api/dossiers/0345550000")).andExpect(status().isUnauthorized());

        mvc.perform(put("/api/dossiers/0345550000").header("Authorization", medecin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Mialy Rabe\",\"allergies\":\"Pénicilline\",\"antecedents\":\"Césarienne 2022\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allergies").value("Pénicilline")).andExpect(jsonPath("$.rendezVous.length()").value(1));

        mvc.perform(post("/api/dossiers/0345550000/consultations").header("Authorization", medecin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rendezVousId\":" + id + ",\"notes\":\"RAS\",\"diagnostic\":\"Grossesse normale\",\"ordonnance\":\"Fer 1 cp/j\"}"))
                .andExpect(status().isCreated());
        // second envoi pour le même rendez-vous : mise à jour, pas de doublon
        mvc.perform(post("/api/dossiers/0345550000/consultations").header("Authorization", sageFemme).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rendezVousId\":" + id + ",\"notes\":\"RAS, TA normale\"}")).andExpect(status().isOk());
        assertEquals(1, consultations.count());
        // un rendez-vous d'un autre patient est refusé
        creer(jourOuvre(1), "09:00:00", "Autre Patiente", "0326666666");
        Long autreId = rdvs.findAll().stream().filter(r -> r.getNomClient().startsWith("Autre")).findFirst().orElseThrow().getId();
        mvc.perform(post("/api/dossiers/0345550000/consultations").header("Authorization", medecin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rendezVousId\":" + autreId + ",\"notes\":\"x\"}")).andExpect(status().isBadRequest());
        // lecture, puis suppression réservée au médecin
        mvc.perform(get("/api/dossiers/0345550000").header("Authorization", sageFemme)).andExpect(jsonPath("$.consultations.length()").value(1))
                .andExpect(jsonPath("$.consultations[0].notes").value("RAS, TA normale"));
        Long cid = consultations.findAll().get(0).getId();
        mvc.perform(delete("/api/dossiers/consultations/" + cid).header("Authorization", sageFemme)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/dossiers/consultations/" + cid).header("Authorization", medecin)).andExpect(status().isNoContent());
    }

    @Test
    void consultationSurvitALaSuppressionDefinitiveDuRendezVous() throws Exception {
        String id = extraire(creer(jourOuvre(1), "08:30:00", "Mialy Rabe", "0345550000"), "id");
        mvc.perform(post("/api/dossiers/0345550000/consultations").header("Authorization", medecin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rendezVousId\":" + id + ",\"notes\":\"Important\"}")).andExpect(status().isCreated());
        mvc.perform(delete("/api/rdv/" + id).header("Authorization", secretaire)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/rdv/corbeille/" + id).header("Authorization", admin)).andExpect(status().isNoContent());
        assertEquals(1, consultations.count());
    }

    // ---------------------------------------------------------------- comptes

    @Test
    void adminModifieUnCompteEtReinitialiseSonMotDePasse() throws Exception {
        MvcResult cree = mvc.perform(post("/api/admin/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Nouvelle\",\"email\":\"test.nouvelle@casafami.test\",\"motDePasse\":\"premier-motdepasse\",\"role\":\"SECRETAIRE\"}"))
                .andExpect(status().isCreated()).andReturn();
        String id = extraire(cree, "id");
        mvc.perform(put("/api/admin/users/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Nouvelle Rabe\",\"email\":\"TEST.nouvelle@casafami.test\",\"telephone\":\"0341112233\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nom").value("Nouvelle Rabe"));
        mvc.perform(put("/api/admin/users/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"X\",\"email\":\"test.admin@casafami.test\"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/users/" + id + "/password").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motDePasse\":\"court\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/users/" + id + "/password").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motDePasse\":\"second-motdepasse\"}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test.nouvelle@casafami.test\",\"motDePasse\":\"premier-motdepasse\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test.nouvelle@casafami.test\",\"motDePasse\":\"second-motdepasse\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/users/" + id).header("Authorization", admin)).andExpect(status().isNoContent());
    }

    @Test
    void changementDeSonPropreMotDePasseExigeLAncien() throws Exception {
        tokenFor("test.changer@casafami.test", Role.SECRETAIRE);
        String bearer = "Bearer " + jwtService.genererToken(userDetailsService.loadUserByUsername("test.changer@casafami.test"));
        mvc.perform(put("/api/profile/password").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ancienMotDePasse\":\"faux\",\"nouveauMotDePasse\":\"nouveau-motdepasse\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/profile/password").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ancienMotDePasse\":\"motdepasse-test-1\",\"nouveauMotDePasse\":\"nouveau-motdepasse\"}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test.changer@casafami.test\",\"motDePasse\":\"nouveau-motdepasse\"}")).andExpect(status().isOk());
        // remet l'ancien mot de passe pour les exécutions suivantes
        users.findByEmailIgnoreCase("test.changer@casafami.test").ifPresent(u -> { u.setMotDePasse(encoder.encode("motdepasse-test-1")); users.save(u); });
    }

    @Test
    void sixiemeEchecDeConnexionEstBloque() throws Exception {
        String email = "test.bloque" + System.nanoTime() + "@casafami.test";
        String mauvais = "{\"email\":\"" + email + "\",\"motDePasse\":\"mauvais\"}";
        for (int i = 0; i < 5; i++) mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(mauvais)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(mauvais))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.message").value(containsString("Trop de tentatives")));
    }

    // ---------------------------------------------------------------- maladies et traitements

    @Test
    void medecinGereLesTraitementsDUneMaladie() throws Exception {
        MvcResult m = mvc.perform(post("/api/maladies").header("Authorization", medecin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Anémie\",\"symptomes\":\"fatigue, pâleur\"}")).andExpect(status().isCreated()).andReturn();
        String id = extraire(m, "id");
        MvcResult t = mvc.perform(post("/api/maladies/" + id + "/traitements").header("Authorization", medecin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"medicament\":\"Fer\",\"posologie\":\"1 cp/jour\",\"duree\":\"3 mois\",\"remarques\":\"à jeun\"}")).andExpect(status().isCreated()).andReturn();
        String tid = extraire(t, "id");
        mvc.perform(post("/api/maladies/" + id + "/traitements").header("Authorization", medecin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"medicament\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/maladies/" + id + "/traitements/" + tid).header("Authorization", medecin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"medicament\":\"Fer + acide folique\",\"posologie\":\"1 cp/jour\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/maladies/" + id).header("Authorization", sageFemme)).andExpect(jsonPath("$.traitements[0].medicament").value("Fer + acide folique"));
        mvc.perform(post("/api/maladies/" + id + "/traitements").header("Authorization", sageFemme).contentType(MediaType.APPLICATION_JSON)
                .content("{\"medicament\":\"X\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/maladies/" + id + "/traitements/" + tid).header("Authorization", medecin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/maladies/" + id).header("Authorization", medecin)).andExpect(jsonPath("$.traitements.length()").value(0));
    }

    // ---------------------------------------------------------------- éditeur du site

    @Test
    void editeurDuSiteLecturePubliqueEcritureSuperAdmin() throws Exception {
        mvc.perform(get("/api/site-content")).andExpect(status().isOk()).andExpect(content().json("{}"));
        String corps = "{\"texte\":\"Bienvenue chez nous\",\"offsetX\":120,\"offsetY\":-40}";
        mvc.perform(put("/api/site-content/accueil.hero.titre").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().isForbidden());
        mvc.perform(put("/api/site-content/accueil.hero.titre").contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/site-content/accueil.hero.titre").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().isOk());
        mvc.perform(put("/api/site-content/mauvaise cle!").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().is4xxClientError());
        mvc.perform(put("/api/site-content/x.y").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"offsetX\":999999}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.offsetX").value(3000));
        mvc.perform(get("/api/site-content")).andExpect(jsonPath("$['accueil.hero.titre'].texte").value("Bienvenue chez nous"))
                .andExpect(jsonPath("$['accueil.hero.titre'].offsetX").value(120));
        // réinitialisation d'un élément puis d'une page
        mvc.perform(delete("/api/site-content/accueil.hero.titre").header("Authorization", admin)).andExpect(status().isNoContent());
        assertEquals(1, siteContent.count());
        mvc.perform(delete("/api/site-content").param("prefixe", "x.").header("Authorization", admin)).andExpect(status().isNoContent());
        assertEquals(0, siteContent.count());
    }

    // ---------------------------------------------------------------- statistiques, exports, calendrier

    @Test
    void statistiquesEtDroitsParRole() throws Exception {
        creer(jourOuvre(1), "08:00:00", "Stat Un", "0341111111");
        creer(jourOuvre(1), "09:00:00", "Stat Deux", "0342222222");
        LocalDate d = jourOuvre(1);
        mvc.perform(get("/api/stats/synthese").param("debut", d.toString()).param("fin", d.toString()).header("Authorization", secretaire))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.parStatut.EN_ATTENTE").value(2))
                .andExpect(jsonPath("$.nouveauxPatients").value(2)).andExpect(jsonPath("$.parHeure.length()").value(2));
        mvc.perform(get("/api/stats/mensuel").param("mois", "6").header("Authorization", medecin)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6));
        mvc.perform(get("/api/stats/synthese").header("Authorization", sageFemme)).andExpect(status().isForbidden());
        mvc.perform(get("/api/stats/synthese").param("debut", "2026-02-01").param("fin", "2026-01-01").header("Authorization", admin)).andExpect(status().isBadRequest());
    }

    @Test
    void exportCsvEtPdfAvecProtectionContreLesFormules() throws Exception {
        LocalDate d = jourOuvre(1);
        mvc.perform(post("/api/rdv").contentType(MediaType.APPLICATION_JSON).content(rdvJson(d, "08:00:00", "=HYPERLINK(\\\"http://x\\\")", "0341111111", null))).andExpect(status().isCreated());
        MvcResult csv = mvc.perform(get("/api/export/rendez-vous").param("debut", d.toString()).param("fin", d.toString()).param("format", "csv").header("Authorization", secretaire))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", containsString("attachment"))).andReturn();
        String texte = csv.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(texte.startsWith("﻿\"N°\"".replace("\"N°\"", "N°")), "BOM + en-tête");
        assertTrue(texte.contains("\"'=HYPERLINK"), "la formule doit être neutralisée : " + texte);
        MvcResult pdf = mvc.perform(get("/api/export/rendez-vous").param("debut", d.toString()).param("fin", d.toString()).param("format", "pdf").header("Authorization", sageFemme))
                .andExpect(status().isOk()).andReturn();
        assertTrue(new String(pdf.getResponse().getContentAsByteArray(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF"));
        mvc.perform(get("/api/export/rendez-vous")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/export/rendez-vous").param("format", "xml").header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/export/rendez-vous").param("debut", "2020-01-01").param("fin", "2026-01-01").header("Authorization", admin)).andExpect(status().isBadRequest());
    }

    @Test
    void calendrierMensuelListeLesRdvEtLesJoursFermes() throws Exception {
        LocalDate d = jourOuvre(1);
        creer(d, "08:00:00", "Cal Un", "0341111111");
        mvc.perform(post("/api/admin/jours-fermes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + d.plusDays(10) + "\",\"motif\":\"Congé\"}")).andExpect(status().isCreated());
        mvc.perform(get("/api/rdv/calendrier").param("mois", String.format("%d-%02d", d.getYear(), d.getMonthValue())).header("Authorization", medecin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rendezVous.length()").value(1));
        mvc.perform(get("/api/rdv/calendrier").param("mois", "pas-un-mois").header("Authorization", medecin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/rdv/calendrier").param("mois", "2026-10")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- rappels et journal d'audit

    @Test
    void rappelDeLaVeilleEnvoyeUneSeuleFois() {
        RendezVous rdv = new RendezVous();
        rdv.setNomClient("Rappel Test");
        rdv.setAgeClient(30);
        rdv.setTelephoneClient("0341234567");
        rdv.setDateRdv(LocalDate.now(ZONE).plusDays(1));
        rdv.setHeureRdv(LocalTime.of(9, 0));
        rdv.setStatut(StatutRDV.CONFIRME);
        rdvs.save(rdv);
        assertEquals(1, rappelService.envoyerRappelsPourDemain());
        assertEquals(0, rappelService.envoyerRappelsPourDemain());
        assertTrue(rdvs.findById(rdv.getId()).orElseThrow().getRappelEnvoye());
    }

    @Test
    void journalDAuditTraceLesActionsEtResteReserveAuSuperAdmin() throws Exception {
        String id = extraire(creer(jourOuvre(1), "08:00:00", "Audit Test", "0341111111"), "id");
        confirmerAvec(id, "test.medecin@casafami.test", 200);
        mvc.perform(get("/api/admin/journal").param("action", "RDV_CONFIRME").param("q", "Audit Test").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu[0].acteurEmail").value("test.admin@casafami.test"))
                .andExpect(jsonPath("$.contenu[0].action").value("RDV_CONFIRME"));
        mvc.perform(get("/api/admin/journal").param("action", "RDV_CREE").param("q", "Audit Test").header("Authorization", admin))
                .andExpect(jsonPath("$.contenu[0].acteurRole").value("PUBLIC"));
        mvc.perform(get("/api/admin/journal").header("Authorization", secretaire)).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- créneaux « vivants », période à venir, services

    @Test
    void creneauPrisDisparaitDeLaListeEtLesAutresRestent() throws Exception {
        LocalDate date = jourOuvre(2);
        String avant = mvc.perform(get("/api/rdv/creneaux").param("date", date.toString())).andReturn().getResponse().getContentAsString();
        assertTrue(avant.contains("08:00"));
        assertEquals(201, creer(date, "08:00:00", "Premier Patient", "0341111111").getResponse().getStatus());
        mvc.perform(get("/api/rdv/creneaux").param("date", date.toString()))
                .andExpect(content().string(not(containsString("08:00"))))
                .andExpect(content().string(containsString("08:30")))
                .andExpect(jsonPath("$.length()").value(11));
        // second patient sur le même créneau : refus 409, puis la liste réaffichée ne contient plus 08:00
        assertEquals(409, creer(date, "08:00:00", "Second Patient", "0342222222").getResponse().getStatus());
        mvc.perform(get("/api/rdv/creneaux/" + date)).andExpect(jsonPath("$.length()").value(11));
    }

    @Test
    void periodeAVenirListeLesRdvDesTrenteProchainsJours() throws Exception {
        creer(jourOuvre(1), "09:00:00", "Bientot Patient", "0341111111");
        creer(jourOuvre(30), "09:00:00", "Loin Patient", "0342222222");
        String corps = mvc.perform(get("/api/rdv/dashboard").param("periode", "a_venir").header("Authorization", secretaire))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(corps.contains("Bientot Patient"), corps);
        assertTrue(corps.contains("\"total\":1"), corps);
    }

    @Test
    void personnelVoitLeMotifDansMesRdvMaisPasLesVisiteurs() throws Exception {
        String cle = extraire(creer(jourOuvre(1), "10:00:00", "Motif Patient", "0343333333"), "cleSuivi");
        mvc.perform(get("/api/rdv/suivi/" + cle)).andExpect(jsonPath("$.motif").doesNotExist());
        mvc.perform(get("/api/rdv/recherche").param("q", "0343333333").header("Authorization", secretaire)).andExpect(jsonPath("$[0].motif").value("Suivi"));
    }

    @Test
    void superAdminModifieUnServiceSansChangerLaPhoto() throws Exception {
        com.cabinet.cabinet.entity.Service service = new com.cabinet.cabinet.entity.Service();
        service.setNom("Ancien nom");
        service.setDescription("Ancienne description");
        service.setImageUrl("/uploads/services/existante.png");
        service = services.save(service);
        org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder put =
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/services/" + service.getId());
        put.with(request -> { request.setMethod("PUT"); return request; });
        mvc.perform(put.param("nom", "Nouveau nom").param("description", "Nouvelle description").param("duree", "45 minutes").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nom").value("Nouveau nom"))
                .andExpect(jsonPath("$.imageUrl").value("/uploads/services/existante.png"));
        org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder interdit =
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/services/" + service.getId());
        interdit.with(request -> { request.setMethod("PUT"); return request; });
        mvc.perform(interdit.param("nom", "X").param("description", "Y").header("Authorization", secretaire)).andExpect(status().isForbidden());
    }

    private Long idDe(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow().getId();
    }

    /** Parcours complet : attribution par le super administrateur, acceptation par la personne, confirmation finale (si l'attribution est refusée, on s'arrête là). */
    private void confirmerAvec(String id, String emailPraticien, int statutAttendu) throws Exception {
        attribuerA(id, emailPraticien, statutAttendu);
        if (statutAttendu != 200) return;
        mvc.perform(put("/api/rdv/" + id + "/accepter").header("Authorization", emailPraticien.contains("medecin") ? medecin : sageFemme)).andExpect(status().isOk());
        mvc.perform(put("/api/rdv/" + id + "/confirmer").header("Authorization", admin)).andExpect(status().isOk());
    }

    private void attribuerA(String id, String emailPraticien, int statutAttendu) throws Exception {
        mvc.perform(put("/api/rdv/" + id + "/praticien").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"praticienId\":" + idDe(emailPraticien) + "}")).andExpect(status().is(statutAttendu));
    }

    /** Reproduit l'accident d'encodage : octets UTF-8 relus comme du Windows-1252 (le texte cassé n'est pas écrit en dur dans le fichier). */
    private static String casse(String correct) {
        return new String(correct.getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.Charset.forName("windows-1252"));
    }

    // ---------------------------------------------------------------- recherche par nom, 7 jours sur 7, encodage

    @Test
    void rechercheParNomDesNomsCourtsEtDesAccents() throws Exception {
        creer(jourOuvre(1), "08:00:00", "bolo", "0341111111");      // nom d'un seul mot, très court
        creer(jourOuvre(1), "09:00:00", "Éloïse Andriamampianina", "0342222222");
        mvc.perform(get("/api/rdv/recherche").param("q", "bolo").header("Authorization", secretaire)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/rdv/recherche").param("q", "eloise").header("Authorization", secretaire)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/rdv/recherche").param("q", "ANDRIAM").header("Authorization", secretaire)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/rdv/recherche").param("q", "loise").header("Authorization", secretaire)).andExpect(jsonPath("$.length()").value(0)); // début de mot, pas milieu
    }

    @Test
    void lePersonnelRetrouveAussiLesAnciensRdvParNomEtParNumero() throws Exception {
        RendezVous ancien = new RendezVous();
        ancien.setNomClient("Ancienne Patiente");
        ancien.setAgeClient(30);
        ancien.setTelephoneClient("0345555555");
        ancien.setDateRdv(LocalDate.now(ZONE).minusDays(90));
        ancien.setHeureRdv(LocalTime.of(9, 0));
        ancien.setStatut(StatutRDV.TERMINE);
        rdvs.save(ancien);
        mvc.perform(get("/api/rdv/recherche").param("q", "ancienne").header("Authorization", secretaire)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/rdv/recherche").param("q", "034 55 555 55").header("Authorization", secretaire)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void leCabinetRecoitLes7JoursParDefaut() throws Exception {
        horaireJours.deleteAll();
        parametresCabinet.deleteAll();
        planningService.initialiserParDefaut();
        try {
            LocalDate samedi = LocalDate.now(ZONE);
            while (samedi.getDayOfWeek() != DayOfWeek.SATURDAY) samedi = samedi.plusDays(1);
            samedi = samedi.plusWeeks(1);
            LocalDate dimanche = samedi.plusDays(1);
            assertEquals(12, planningService.creneaux(samedi).size());
            assertEquals(12, planningService.creneaux(dimanche).size());
            mvc.perform(get("/api/rdv/creneaux/" + samedi)).andExpect(jsonPath("$.length()").value(12));
            assertEquals(201, creer(samedi, "08:00:00", "Samedi Patiente", "0341234567").getResponse().getStatus());
            assertEquals(201, creer(dimanche, "08:00:00", "Dimanche Patiente", "0341234568").getResponse().getStatus());
        } finally {
            planningParDefaut();
        }
    }

    @Test
    void ancienPlanningLundiVendrediNonPersonnaliseEstOuvertAuWeekEnd_maisUnPlanningModifieNEstPasTouche() throws Exception {
        planningParDefaut(); // = exactement l'ancienne valeur par défaut (lundi–vendredi 8-11 / 14-17)
        try {
            assertTrue(planningService.migrerAncienPlanningParDefaut());
            assertFalse(planningService.migrerAncienPlanningParDefaut(), "déjà migré");
            LocalDate dimanche = LocalDate.now(ZONE);
            while (dimanche.getDayOfWeek() != DayOfWeek.SUNDAY) dimanche = dimanche.plusDays(1);
            assertEquals(12, planningService.creneaux(dimanche.plusWeeks(1)).size());

            // planning personnalisé par le super administrateur : horaires du matin différents → jamais modifié
            mvc.perform(put("/api/admin/planning").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                    .content(planningJson(30, 1, "09:00", "12:00", "14:00", "17:00"))).andExpect(status().isOk());
            assertFalse(planningService.migrerAncienPlanningParDefaut());
            assertEquals(0, planningService.creneaux(dimanche.plusWeeks(1)).size(), "le week-end fermé par l'administrateur reste fermé");
        } finally {
            planningParDefaut();
        }
    }

    @Test
    void unTexteCasseEnvoyeAuServeurEstRepareAvantEnregistrement() throws Exception {
        com.cabinet.cabinet.entity.Service service = new com.cabinet.cabinet.entity.Service();
        service.setNom(casse("Consultation générale"));
        service.setDescription(casse("Consultation médicale"));
        service = services.save(service);
        com.cabinet.cabinet.entity.Service relu = services.findById(service.getId()).orElseThrow();
        assertEquals("Consultation générale", relu.getNom());
        assertEquals("Consultation médicale", relu.getDescription());
    }

    @Test
    void auDemarrageLesTextesCassesEtLesNumerosAnciensSontRemisEnOrdre() throws Exception {
        jdbc.update("insert into service (nom, description) values (?, ?)", casse("Échographie"), casse("Suivi médical de votre bébé"));
        String ancienNumero = "+" + "26" + "1343462622";
        jdbc.update("insert into rendez_vous (nom_client, age_client, telephone_client, date_rdv, heure_rdv, statut, date_creation, supprime) values (?, 30, ?, current_date + 40, '09:00', 'EN_ATTENTE', now(), false)",
                "Patient Ancien", ancienNumero);
        assertTrue(reparateur.reparerTextes() >= 2);
        assertTrue(reparateur.normaliserTelephones() >= 1);
        assertEquals("Échographie", jdbc.queryForObject("select nom from service where description like 'Suivi m%'", String.class));
        assertEquals("Suivi médical de votre bébé", jdbc.queryForObject("select description from service where nom = 'Échographie'", String.class));
        assertEquals("0343462622", jdbc.queryForObject("select telephone_client from rendez_vous where nom_client = 'Patient Ancien'", String.class));
        // la recherche par numéro retrouve ce patient quel que soit le format tapé
        mvc.perform(get("/api/rdv/recherche").param("q", "034 34 626 22").header("Authorization", secretaire)).andExpect(jsonPath("$.length()").value(1));
        assertEquals(0, reparateur.reparerTextes(), "une seconde passe ne change plus rien");
    }

    // ---------------------------------------------------------------- attribution par le super administrateur, occupation, notifications

    @Test
    void lePatientNeChoisitPlusSonPraticien() throws Exception {
        Long idMedecin = idDe("test.medecin@casafami.test");
        mvc.perform(post("/api/rdv").contentType(MediaType.APPLICATION_JSON).content(rdvJson(jourOuvre(1), "09:00:00", "Patient Libre", "0341111111", idMedecin)))
                .andExpect(status().isCreated()).andReturn();
        assertEquals(1, rdvs.count());
        assertNull(rdvs.findAll().get(0).getPraticien(), "un patient ne peut pas désigner son praticien");
        mvc.perform(get("/api/rdv/form-options")).andExpect(status().isOk()).andExpect(jsonPath("$.praticiens.length()").value(0));
        mvc.perform(get("/api/rdv/form-options").header("Authorization", secretaire)).andExpect(jsonPath("$.praticiens.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void superAdminChoisitQuiPrendLeRdv_laPersonneRecoitUneNotification() throws Exception {
        String id = extraire(creer(jourOuvre(1), "09:00:00", "Notif Patiente", "0341111111"), "id");
        attribuerA(id, "test.medecin@casafami.test", 200);
        mvc.perform(get("/api/notifications").header("Authorization", medecin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.nonLues").value(1))
                .andExpect(jsonPath("$.notifications[0].type").value("RDV_ATTRIBUE"))
                .andExpect(jsonPath("$.notifications[0].message").value(containsString("Notif Patiente")))
                .andExpect(jsonPath("$.notifications[0].creePar").value(containsString("Test")));
        // la sage-femme n'a rien reçu et ne peut pas toucher à la notification d'un collègue
        mvc.perform(get("/api/notifications").header("Authorization", sageFemme)).andExpect(jsonPath("$.nonLues").value(0));
        String notifId = extraire(mvc.perform(get("/api/notifications").header("Authorization", medecin)).andReturn(), "id");
        mvc.perform(put("/api/notifications/" + notifId + "/lue").header("Authorization", sageFemme)).andExpect(status().isNotFound());
        mvc.perform(put("/api/notifications/" + notifId + "/lue").header("Authorization", medecin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications").header("Authorization", medecin)).andExpect(jsonPath("$.nonLues").value(0));
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        // le nom de la personne choisie apparaît dans la liste des rendez-vous
        mvc.perform(get("/api/rdv/dashboard").param("periode", "a_venir").header("Authorization", admin))
                .andExpect(jsonPath("$.rendezVous[0].praticienNom").value("Test MEDECIN"));
    }

    @Test
    void occupationDuPersonnelParCreneau_etRefusSiLaPersonneEstDejaPrise() throws Exception {
        mvc.perform(put("/api/admin/planning").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(planningJson(30, 2, "08:00", "11:00", "14:00", "17:00"))).andExpect(status().isOk());
        LocalDate date = jourOuvre(2);
        String premier = extraire(creer(date, "09:00:00", "Premier Patient", "0341111111"), "id");
        String second = extraire(creer(date, "09:00:00", "Second Patient", "0342222222"), "id");
        confirmerAvec(premier, "test.medecin@casafami.test", 200);

        mvc.perform(get("/api/admin/occupation").param("date", date.toString()).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creneaux.length()").value(12))
                .andExpect(jsonPath("$.capaciteParCreneau").value(2))
                .andExpect(jsonPath("$.personnel[?(@.nom == 'Test MEDECIN')].occupations['09:00'].patient").value("Premier Patient"))
                .andExpect(jsonPath("$.personnel[?(@.nom == 'Test SAGE_FEMME')].total").value(0))
                .andExpect(jsonPath("$.sansPraticien['09:00'][0].patient").value("Second Patient"));

        // le médecin est pris à 09:00 : le second rendez-vous ne peut pas lui être confié, mais la sage-femme est libre
        confirmerAvec(second, "test.medecin@casafami.test", 409);
        confirmerAvec(second, "test.sf@casafami.test", 200);
        mvc.perform(get("/api/admin/occupation").param("date", date.toString()).header("Authorization", admin))
                .andExpect(jsonPath("$.personnel[?(@.nom == 'Test SAGE_FEMME')].total").value(1))
                .andExpect(jsonPath("$.sansPraticien.length()").value(0));
        mvc.perform(get("/api/admin/occupation").param("date", date.toString()).header("Authorization", secretaire)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/occupation").param("date", date.toString()).header("Authorization", medecin)).andExpect(status().isForbidden());
    }

    @Test
    void changerLaPersonnePrevientLAncienneEtLaNouvelle_etLAnnulationPrevientLaPersonneAttribuee() throws Exception {
        String id = extraire(creer(jourOuvre(1), "10:00:00", "Changement Patient", "0341111111"), "id");
        mvc.perform(put("/api/rdv/" + id + "/praticien").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"praticienId\":" + idDe("test.medecin@casafami.test") + "}")).andExpect(status().isOk());
        mvc.perform(put("/api/rdv/" + id + "/praticien").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"praticienId\":" + idDe("test.sf@casafami.test") + "}")).andExpect(status().isOk());
        mvc.perform(get("/api/notifications").header("Authorization", medecin)).andExpect(jsonPath("$.nonLues").value(2))
                .andExpect(jsonPath("$.notifications[0].type").value("RDV_RETIRE"));
        mvc.perform(get("/api/notifications").header("Authorization", sageFemme)).andExpect(jsonPath("$.nonLues").value(1))
                .andExpect(jsonPath("$.notifications[0].type").value("RDV_ATTRIBUE"));
        // seul le super administrateur peut attribuer
        mvc.perform(put("/api/rdv/" + id + "/praticien").header("Authorization", medecin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"praticienId\":" + idDe("test.medecin@casafami.test") + "}")).andExpect(status().isForbidden());
        // annulation par le patient : la sage-femme est prévenue
        mvc.perform(put("/api/rdv/" + id + "/annuler").header("Authorization", secretaire)).andExpect(status().isOk());
        mvc.perform(get("/api/notifications").header("Authorization", sageFemme)).andExpect(jsonPath("$.notifications[0].type").value("RDV_ANNULE"));
        // un rendez-vous annulé ne se réattribue plus
        mvc.perform(put("/api/rdv/" + id + "/praticien").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"praticienId\":" + idDe("test.medecin@casafami.test") + "}")).andExpect(status().isBadRequest());
    }

    @Test
    void nouvelleDemande_previentLeSuperAdmin_etUnClicOuvreLaDemandePourL_attribuer() throws Exception {
        LocalDate jour = jourOuvre(1);
        // réservation faite par un patient sur le site public
        String id = extraire(creer(jour, "08:30:00", "Nouvelle Patiente", "0341111111"), "id");
        mvc.perform(get("/api/notifications").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.nonLues").value(1))
                .andExpect(jsonPath("$.notifications[0].type").value("RDV_NOUVEAU"))
                .andExpect(jsonPath("$.notifications[0].titre").value("Nouvelle demande de rendez-vous"))
                .andExpect(jsonPath("$.notifications[0].message").value(containsString("à attribuer")))
                .andExpect(jsonPath("$.notifications[0].message").value(containsString("Nouvelle Patiente")))
                .andExpect(jsonPath("$.notifications[0].creePar").value("Le patient"))
                .andExpect(jsonPath("$.notifications[0].lien").value("/admin/dashboard?rdv=" + id));
        // les autres membres du personnel ne sont pas dérangés : c'est le super administrateur qui attribue
        for (String jeton : new String[]{medecin, sageFemme, secretaire}) {
            mvc.perform(get("/api/notifications").header("Authorization", jeton)).andExpect(jsonPath("$.nonLues").value(0));
        }
        // le lien mène à une demande que le super administrateur peut ouvrir, puis attribuer
        mvc.perform(get("/api/rdv/" + id).header("Authorization", admin)).andExpect(status().isOk()).andExpect(jsonPath("$.praticienId").isEmpty());
        attribuerA(id, "test.medecin@casafami.test", 200);

        // un rendez-vous saisi par la secrétaire prévient aussi le super administrateur
        mvc.perform(post("/api/rdv").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(rdvJson(jour, "09:00:00", "Saisie Secretaire", "0342222222", null)))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/notifications").header("Authorization", admin)).andExpect(jsonPath("$.nonLues").value(2))
                .andExpect(jsonPath("$.notifications[0].creePar").value("Test SECRETAIRE (secrétaire)"));
        // …mais pas celui qu'il saisit lui-même
        mvc.perform(post("/api/rdv").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(rdvJson(jour, "09:30:00", "Saisie Admin", "0343333333", null)))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/notifications").header("Authorization", admin)).andExpect(jsonPath("$.nonLues").value(2));
    }

    @Test
    void leSuperAdminEstPreveuDeTout_creationModificationAnnulationCorbeille_parLePatientEtParLePersonnel() throws Exception {
        LocalDate jour = jourOuvre(1);
        String[] types = {"RDV_NOUVEAU", "RDV_MODIFIE", "RDV_ANNULE", "RDV_CORBEILLE"};

        // création : le patient, la secrétaire, un médecin et une sage-femme
        creer(jour, "08:00:00", "Client Public", "0341000001");
        mvc.perform(get("/api/notifications").header("Authorization", admin)).andExpect(jsonPath("$.notifications[0].type").value("RDV_NOUVEAU"))
                .andExpect(jsonPath("$.notifications[0].creePar").value("Le patient"));
        String[][] createurs = {{"secretaire", "secrétaire", "08:30:00"}, {"medecin", "médecin", "09:00:00"}, {"sageFemme", "sage-femme", "09:30:00"}};
        for (String[] c : createurs) {
            String jeton = c[0].equals("secretaire") ? secretaire : (c[0].equals("medecin") ? medecin : sageFemme);
            mvc.perform(post("/api/rdv").header("Authorization", jeton).contentType(MediaType.APPLICATION_JSON).content(rdvJson(jour, c[2], "Client " + c[1], "03420000" + c[2].substring(0, 2), null)))
                    .andExpect(status().isCreated());
            mvc.perform(get("/api/notifications").header("Authorization", admin)).andExpect(jsonPath("$.notifications[0].type").value("RDV_NOUVEAU"))
                    .andExpect(jsonPath("$.notifications[0].creePar").value(containsString("(" + c[1] + ")")))
                    .andExpect(jsonPath("$.notifications[0].message").value(containsString("Client " + c[1])));
        }
        mvc.perform(get("/api/notifications").header("Authorization", admin)).andExpect(jsonPath("$.nonLues").value(4));

        // modification par la secrétaire : déplacement du rendez-vous créé par le patient
        MvcResult lePatient = creer(jour, "10:00:00", "Client A Modifier", "0341000002");
        String id = extraire(lePatient, "id");
        String jeton = extraire(lePatient, "clientAccessToken");
        mvc.perform(put("/api/rdv/" + id).header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(rdvJson(jour, "10:30:00", "Client A Modifier", "0341000002", null)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/notifications").header("Authorization", admin)).andExpect(jsonPath("$.notifications[0].type").value("RDV_MODIFIE"))
                .andExpect(jsonPath("$.notifications[0].message").value(containsString("déplacé")))
                .andExpect(jsonPath("$.notifications[0].lien").value("/admin/dashboard?rdv=" + id));

        // annulation par le patient, puis par l'équipe
        mvc.perform(put("/api/rdv/" + id + "/annuler").header("X-RDV-Access-Token", jeton)).andExpect(status().isOk());
        mvc.perform(get("/api/notifications").header("Authorization", admin)).andExpect(jsonPath("$.notifications[0].type").value("RDV_ANNULE"))
                .andExpect(jsonPath("$.notifications[0].message").value(containsString("Le patient a annulé")))
                .andExpect(jsonPath("$.notifications[0].creePar").value("Le patient"));
        String autre = extraire(creer(jour, "14:00:00", "Client Annule Par Equipe", "0341000003"), "id");
        mvc.perform(put("/api/rdv/" + autre + "/annuler").header("Authorization", secretaire)).andExpect(status().isOk());
        mvc.perform(get("/api/notifications").header("Authorization", admin)).andExpect(jsonPath("$.notifications[0].type").value("RDV_ANNULE"))
                .andExpect(jsonPath("$.notifications[0].message").value(containsString("annulé par l'équipe")))
                .andExpect(jsonPath("$.notifications[0].creePar").value(containsString("(secrétaire)")));

        // mise à la corbeille par un membre du personnel : le lien ouvre la corbeille
        String aSupprimer = extraire(creer(jour, "14:30:00", "Client Corbeille", "0341000004"), "id");
        mvc.perform(delete("/api/rdv/" + aSupprimer).header("Authorization", secretaire)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications").header("Authorization", admin)).andExpect(jsonPath("$.notifications[0].type").value("RDV_CORBEILLE"))
                .andExpect(jsonPath("$.notifications[0].lien").value("/corbeille")).andExpect(jsonPath("$.notifications[0].creePar").value(containsString("(secrétaire)")));

        // les gestes du super administrateur lui-même ne le notifient pas
        long avant = Long.parseLong(extraire(mvc.perform(get("/api/notifications").header("Authorization", admin)).andReturn(), "nonLues"));
        mvc.perform(put("/api/rdv/" + autre).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(rdvJson(jour, "14:00:00", "Client Annule Par Equipe", "0341000003", null))).andExpect(status().isOk());
        String proprio = extraire(creer(jour, "10:00:00", "Client Admin Annule", "0341000005"), "id");
        long apresCreation = Long.parseLong(extraire(mvc.perform(get("/api/notifications").header("Authorization", admin)).andReturn(), "nonLues"));
        mvc.perform(put("/api/rdv/" + proprio + "/annuler").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(delete("/api/rdv/" + proprio).header("Authorization", admin)).andExpect(status().isNoContent());
        long apres = Long.parseLong(extraire(mvc.perform(get("/api/notifications").header("Authorization", admin)).andReturn(), "nonLues"));
        org.junit.jupiter.api.Assertions.assertEquals(avant + 1, apresCreation, "seule la demande du patient prévient le super administrateur");
        org.junit.jupiter.api.Assertions.assertEquals(apresCreation, apres, "annuler ou supprimer lui-même ne le notifie pas");
        // les autres membres du personnel ne reçoivent pas ces notifications de suivi
        for (String t : types) mvc.perform(get("/api/notifications").header("Authorization", medecin)).andExpect(jsonPath("$.notifications[?(@.type=='" + t + "')]").isEmpty());
    }

    @Test
    void mesPatients_listeLesRdvAttribues_detailProtege_etLesNotificationsOuvrentLaBonnePage() throws Exception {
        LocalDate jour = jourOuvre(1);
        String premier = extraire(creer(jour, "09:00:00", "Patient Du Medecin", "0341111111"), "id");
        String second = extraire(creer(jour, "09:30:00", "Patient De La Sage-femme", "0342222222"), "id");
        String libre = extraire(creer(jour, "10:30:00", "Patient Non Attribue", "0343333333"), "id");
        attribuerA(premier, "test.medecin@casafami.test", 200);
        attribuerA(second, "test.sf@casafami.test", 200);

        // chacun ne voit que ses rendez-vous ; ceux qu'il n'a pas encore acceptés sont marqués comme tels
        mvc.perform(get("/api/rdv/mes-patients").header("Authorization", medecin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].nomClient").value("Patient Du Medecin"))
                .andExpect(jsonPath("$[0].personnelConfirme").value(false)).andExpect(jsonPath("$[0].heureRdv").value("09:00:00"));
        mvc.perform(get("/api/rdv/mes-patients").header("Authorization", sageFemme)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nomClient").value("Patient De La Sage-femme"));
        mvc.perform(put("/api/rdv/" + premier + "/accepter").header("Authorization", medecin)).andExpect(status().isOk());
        mvc.perform(get("/api/rdv/mes-patients").header("Authorization", medecin)).andExpect(jsonPath("$[0].personnelConfirme").value(true));
        mvc.perform(get("/api/rdv/mes-patients").header("Authorization", admin)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/rdv/mes-patients")).andExpect(status().isUnauthorized());

        // détail d'un rendez-vous : le sien ou un rendez-vous encore libre, jamais celui d'un collègue
        mvc.perform(get("/api/rdv/" + premier).header("Authorization", medecin)).andExpect(status().isOk()).andExpect(jsonPath("$.nomClient").value("Patient Du Medecin"));
        mvc.perform(get("/api/rdv/" + libre).header("Authorization", medecin)).andExpect(status().isOk());
        mvc.perform(get("/api/rdv/" + second).header("Authorization", medecin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/rdv/" + second).header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/rdv/" + second).header("Authorization", secretaire)).andExpect(status().isOk());
        mvc.perform(get("/api/rdv/999999").header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/rdv/" + premier)).andExpect(status().isUnauthorized());

        // un clic sur la notification ouvre la page où l'on traite le rendez-vous
        mvc.perform(get("/api/notifications").header("Authorization", medecin)).andExpect(jsonPath("$.notifications[0].type").value("RDV_ATTRIBUE"))
                .andExpect(jsonPath("$.notifications[0].lien").value("/espace/mes-patients?rdv=" + premier));
        mvc.perform(get("/api/notifications").header("Authorization", admin)).andExpect(jsonPath("$.notifications[0].type").value("RDV_ACCEPTE"))
                .andExpect(jsonPath("$.notifications[0].lien").value("/admin/dashboard?rdv=" + premier));
    }

    @Test
    void deuxConfirmations_lePersonnelAccepte_puisLeSuperAdminConfirme() throws Exception {
        MvcResult cree = creer(jourOuvre(1), "09:30:00", "Double Confirmation", "0341111111");
        String id = extraire(cree, "id");
        String cle = extraire(cree, "cleSuivi");
        attribuerA(id, "test.medecin@casafami.test", 200);
        // seule la demande du patient a prévenu le super administrateur (l'attribution est son propre geste)
        mvc.perform(get("/api/notifications").header("Authorization", admin)).andExpect(jsonPath("$.nonLues").value(1))
                .andExpect(jsonPath("$.notifications[0].type").value("RDV_NOUVEAU"));
        // le patient ne voit aucun nom tant que le cabinet n'a pas confirmé
        mvc.perform(get("/api/rdv/suivi/" + cle)).andExpect(status().isOk()).andExpect(jsonPath("$.praticienNom").isEmpty());
        // le super administrateur ne peut pas confirmer avant la personne attribuée
        mvc.perform(put("/api/rdv/" + id + "/confirmer").header("Authorization", admin)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("n'a pas encore accepté")));
        // 1re confirmation : la personne attribuée -> le super administrateur est notifié
        mvc.perform(put("/api/rdv/" + id + "/accepter").header("Authorization", medecin)).andExpect(status().isOk()).andExpect(jsonPath("$.personnelConfirme").value(true))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"));
        mvc.perform(get("/api/notifications").header("Authorization", admin)).andExpect(jsonPath("$.nonLues").value(2))
                .andExpect(jsonPath("$.notifications[0].type").value("RDV_ACCEPTE"))
                .andExpect(jsonPath("$.notifications[0].message").value(containsString("Test MEDECIN")))
                .andExpect(jsonPath("$.notifications[0].message").value(containsString("Double Confirmation")));
        // accepter deux fois ne crée pas une seconde notification
        mvc.perform(put("/api/rdv/" + id + "/accepter").header("Authorization", medecin)).andExpect(status().isOk());
        mvc.perform(get("/api/notifications").header("Authorization", admin)).andExpect(jsonPath("$.nonLues").value(2));
        // 2e confirmation : le super administrateur -> le rendez-vous est confirmé, la personne en est informée, le patient voit son nom
        mvc.perform(put("/api/rdv/" + id + "/confirmer").header("Authorization", admin)).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("CONFIRME"));
        mvc.perform(get("/api/notifications").header("Authorization", medecin)).andExpect(jsonPath("$.notifications[0].type").value("RDV_CONFIRME"));
        mvc.perform(get("/api/rdv/suivi/" + cle)).andExpect(jsonPath("$.praticienNom").value("Test MEDECIN"));
        // changer de personne remet l'acceptation à zéro
        mvc.perform(put("/api/rdv/" + id + "/praticien").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"praticienId\":" + idDe("test.sf@casafami.test") + "}")).andExpect(status().isOk()).andExpect(jsonPath("$.personnelConfirme").value(false));
    }

    @Test
    void lePraticienAccepteSeulementSesRdv_etSecretaireNeChangePasLaPersonne() throws Exception {
        String id = extraire(creer(jourOuvre(1), "10:30:00", "Mon Rdv Patient", "0341111111"), "id");
        mvc.perform(put("/api/rdv/" + id + "/praticien").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"praticienId\":" + idDe("test.medecin@casafami.test") + "}")).andExpect(status().isOk());
        mvc.perform(put("/api/rdv/" + id + "/accepter").header("Authorization", sageFemme)).andExpect(status().isForbidden()); // pas le sien
        mvc.perform(put("/api/rdv/" + id + "/confirmer").header("Authorization", medecin)).andExpect(status().isForbidden());    // la confirmation finale est au super administrateur
        mvc.perform(put("/api/rdv/" + id + "/accepter").header("Authorization", medecin)).andExpect(status().isOk()).andExpect(jsonPath("$.personnelConfirme").value(true)); // le sien
        // la secrétaire modifie le rendez-vous : la personne attribuée est conservée même si elle en envoie une autre
        LocalDate date = jourOuvre(1);
        mvc.perform(put("/api/rdv/" + id).header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON)
                .content(rdvJson(date, "10:30:00", "Mon Rdv Patient", "0341111111", idDe("test.sf@casafami.test"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.praticienNom").value("Test MEDECIN"));
        // déplacement du rendez-vous : la personne attribuée est prévenue
        mvc.perform(put("/api/rdv/" + id).header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON)
                .content(rdvJson(date, "14:00:00", "Mon Rdv Patient", "0341111111", null))).andExpect(status().isOk())
                .andExpect(jsonPath("$.personnelConfirme").value(false)); // nouvel horaire en attente : la personne doit accepter de nouveau
        mvc.perform(get("/api/notifications").header("Authorization", medecin)).andExpect(jsonPath("$.notifications[0].type").value("RDV_DEPLACE"));
    }

    @Test
    void supprimerUnCompteAvecDesNotificationsFonctionne() throws Exception {
        tokenFor("test.ephemere2@casafami.test", Role.SAGE_FEMME);
        Long idEphemere = idDe("test.ephemere2@casafami.test");
        String id = extraire(creer(jourOuvre(2), "11:00:00".replace("11:00", "10:00"), "Ephemere Patient", "0341111111"), "id");
        mvc.perform(put("/api/rdv/" + id + "/praticien").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"praticienId\":" + idEphemere + "}")).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/users/" + idEphemere).header("Authorization", admin)).andExpect(status().isNoContent());
        assertNull(rdvs.findById(Long.parseLong(id)).orElseThrow().getPraticien());
    }
}
