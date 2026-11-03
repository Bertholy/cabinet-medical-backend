package com.cabinet.cabinet;

import com.cabinet.cabinet.repository.NotificationRepository;
import com.cabinet.cabinet.repository.RendezVousRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Patient venu au cabinet sans rendez-vous : la secrétaire voit qui est libre, puis l'envoie tout de suite en consultation. */
class SansRendezVousTests extends BaseApiTest {
    @Autowired RendezVousRepository rdvs;
    @Autowired com.cabinet.cabinet.repository.ServiceRepository servicesRepo;
    @Autowired NotificationRepository notifications;

    @BeforeEach
    @AfterEach
    void nettoyer() {
        notifications.deleteAll();
        rdvs.deleteAll();
    }

    private String corps(Long praticienId, String nom) {
        return corps(praticienId, nom, 31, "FEMME");
    }

    private String corps(Long praticienId, String nom, int age, String sexe) {
        return "{\"nomClient\":\"" + nom + "\",\"ageClient\":" + age + ",\"sexe\":\"" + sexe + "\",\"telephoneClient\":\"034 12 345 67\",\"motif\":\"Fièvre depuis hier\",\"praticienId\":" + praticienId + "}";
    }

    /** Un enfant n'a pas de téléphone : le contact est celui de son parent (accompagnant). */
    private String corpsEnfant(Long praticienId, String nom, int age, String sexe) {
        return "{\"nomClient\":\"" + nom + "\",\"ageClient\":" + age + ",\"sexe\":\"" + sexe + "\",\"praticienId\":" + praticienId
                + ",\"accompagnantNom\":\"Rasoa Marie\",\"accompagnantTelephone\":\"032 11 222 33\",\"accompagnantLien\":\"Parent\",\"accompagnantSexe\":\"FEMME\"}";
    }

    @Test
    void la_secretaire_voit_qui_est_libre_maintenant() throws Exception {
        Long medecinId = idDe("test.medecin@casafami.test");
        mvc.perform(get("/api/rdv/sans-rendez-vous/libres").header("Authorization", secretaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heure").exists())
                .andExpect(jsonPath("$.praticiens[?(@.id == " + medecinId + ")].libre").value(true));
    }

    @Test
    void consultation_immediate_creee_confirmee_et_acceptee_sans_sms() throws Exception {
        Long medecinId = idDe("test.medecin@casafami.test");
        mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(corps(medecinId, "Rakoto Jean")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("CONFIRME"))
                .andExpect(jsonPath("$.sansRendezVous").value(true))
                .andExpect(jsonPath("$.personnelConfirme").value(true))
                .andExpect(jsonPath("$.praticienId").value(medecinId.intValue()))
                .andExpect(jsonPath("$.cleSuivi").exists());
    }

    @Test
    void la_personne_reçue_n_est_plus_libre_puis_un_second_patient_est_refuse() throws Exception {
        Long medecinId = idDe("test.medecin@casafami.test");
        mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(corps(medecinId, "Premier Patient")))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/rdv/sans-rendez-vous/libres").header("Authorization", secretaire))
                .andExpect(jsonPath("$.praticiens[?(@.id == " + medecinId + ")].libre").value(false));
        MvcResult refus = mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(corps(medecinId, "Second Patient")))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertEquals(409, refus.getResponse().getStatus(), refus.getResponse().getContentAsString());
        // une autre personne libre peut recevoir le second patient
        Long sfId = idDe("test.sf@casafami.test");
        mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(corps(sfId, "Second Patient")))
                .andExpect(status().isCreated());
    }

    @Test
    void la_personne_est_prevenue_et_la_consultation_peut_etre_terminee() throws Exception {
        Long medecinId = idDe("test.medecin@casafami.test");
        MvcResult cree = mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(corps(medecinId, "Patient Notifie")))
                .andExpect(status().isCreated()).andReturn();
        mvc.perform(get("/api/notifications").header("Authorization", medecin))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Patient sans rendez-vous")));
        String id = extraire(cree, "id");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/rdv/" + id + "/terminer").header("Authorization", medecin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("TERMINE"));
        // une fois terminée, la personne est de nouveau libre
        mvc.perform(get("/api/rdv/sans-rendez-vous/libres").header("Authorization", secretaire))
                .andExpect(jsonPath("$.praticiens[?(@.id == " + medecinId + ")].libre").value(true));
    }

    @Test
    void seuls_les_droits_attribues_donnent_acces() throws Exception {
        Long medecinId = idDe("test.medecin@casafami.test");
        // un médecin n'a pas ce droit d'origine ; le patient anonyme non plus
        mvc.perform(get("/api/rdv/sans-rendez-vous/libres").header("Authorization", medecin)).andExpect(status().isForbidden());
        mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", medecin).contentType(MediaType.APPLICATION_JSON).content(corps(medecinId, "X Y"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/rdv/sans-rendez-vous/libres")).andExpect(status().is4xxClientError());
        // le super administrateur l'a toujours
        mvc.perform(get("/api/rdv/sans-rendez-vous/libres").header("Authorization", admin)).andExpect(status().isOk());
    }

    @Test
    void donnees_invalides_refusees() throws Exception {
        Long medecinId = idDe("test.medecin@casafami.test");
        mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nomClient\":\"A\",\"ageClient\":31,\"telephoneClient\":\"034 12 345 67\",\"praticienId\":" + medecinId + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nomClient\":\"Bon Nom\",\"ageClient\":31,\"sexe\":\"FEMME\",\"telephoneClient\":\"034 12 345 67\"}"))
                .andExpect(status().isBadRequest());
        // le sexe est obligatoire, et doit être HOMME ou FEMME
        mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nomClient\":\"Sans Sexe\",\"ageClient\":31,\"telephoneClient\":\"034 12 345 67\",\"praticienId\":" + medecinId + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(corps(medecinId, "Sexe Faux", 31, "AUTRE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void la_civilite_suit_l_age_et_le_sexe() throws Exception {
        Long medecinId = idDe("test.medecin@casafami.test");
        Long sfId = idDe("test.sf@casafami.test");
        mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(corps(medecinId, "Rakoto Paul", 40, "HOMME")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sexe").value("HOMME")).andExpect(jsonPath("$.civilite").value("Mr"));
        mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(corps(sfId, "Rasoa Marie", 40, "FEMME")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.civilite").value("Mme"));
        // avant 12 ans : « Enfant », de 12 à 17 ans : « Adolescent », quel que soit le sexe ; la civilité est recalculée à chaque lecture
        rdvs.deleteAll();   // le médecin redevient libre
        mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corpsEnfant(medecinId, "Petit Lova", 7, "HOMME")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.civilite").value("Enfant")).andExpect(jsonPath("$.sexe").value("HOMME"));
    }

    @Test
    void urgence_remplie_par_un_accompagnant_avec_son_numero_comme_contact() throws Exception {
        Long medecinId = idDe("test.medecin@casafami.test");
        // le patient n'a pas de numéro : celui de la personne qui l'accompagne sert de contact
        mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nomClient\":\"Patient non identifié\",\"ageClient\":45,\"sexe\":\"HOMME\",\"urgence\":true,\"praticienId\":" + medecinId
                        + ",\"accompagnantNom\":\"Rasoa Marie\",\"accompagnantTelephone\":\"032 11 222 33\",\"accompagnantLien\":\"Épouse\",\"accompagnantSexe\":\"FEMME\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.urgence").value(true))
                .andExpect(jsonPath("$.accompagnantNom").value("Rasoa Marie"))
                .andExpect(jsonPath("$.accompagnantLien").value("Épouse"))
                .andExpect(jsonPath("$.accompagnantSexe").value("FEMME"))
                .andExpect(jsonPath("$.accompagnantCivilite").value("Mme"))
                .andExpect(jsonPath("$.telephoneClient").value("0321122233"));
        // la personne est prévenue comme d'une urgence
        mvc.perform(get("/api/notifications").header("Authorization", medecin)).andExpect(status().isOk()).andExpect(content().string(containsString("URGENCE")));
    }

    @Test
    void sans_urgence_le_telephone_du_patient_reste_obligatoire() throws Exception {
        Long medecinId = idDe("test.medecin@casafami.test");
        mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nomClient\":\"Sans Numero\",\"ageClient\":30,\"sexe\":\"FEMME\",\"praticienId\":" + medecinId + ",\"accompagnantTelephone\":\"032 11 222 33\"}"))
                .andExpect(status().isBadRequest());
        // en urgence mais sans aucun numéro : refusé aussi
        mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nomClient\":\"Sans Numero\",\"ageClient\":30,\"sexe\":\"FEMME\",\"urgence\":true,\"praticienId\":" + medecinId + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void un_enfant_n_a_pas_de_telephone_le_contact_est_son_parent() throws Exception {
        Long medecinId = idDe("test.medecin@casafami.test");
        // le numéro du parent sert de contact ; le téléphone saisi pour l'enfant est ignoré
        mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(corpsEnfant(medecinId, "Petit Lova", 9, "HOMME")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.civilite").value("Enfant"))
                .andExpect(jsonPath("$.telephoneClient").value("0321122233"))
                .andExpect(jsonPath("$.accompagnantNom").value("Rasoa Marie"))
                .andExpect(jsonPath("$.accompagnantCivilite").value("Mme"));
        rdvs.deleteAll();
        // sans parent, ou sans son numéro : refusé, même si l'enfant a un numéro
        mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(corps(medecinId, "Petit Lova", 9, "HOMME")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nomClient\":\"Petit Lova\",\"ageClient\":9,\"sexe\":\"HOMME\",\"praticienId\":" + medecinId + ",\"accompagnantNom\":\"Rasoa Marie\"}"))
                .andExpect(status().isBadRequest());
        // 18 ans : adulte, son propre numéro suffit
        mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(corps(medecinId, "Jeune Adulte", 18, "HOMME")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.telephoneClient").value("0341234567"));
    }

    @Test
    void un_patient_peut_venir_pour_plusieurs_services() throws Exception {
        Long medecinId = idDe("test.medecin@casafami.test");
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (String nom : new String[]{"Service multi A", "Service multi B", "Service multi C"}) {
            com.cabinet.cabinet.entity.Service s = new com.cabinet.cabinet.entity.Service();
            s.setNom(nom); s.setDescription("x"); s.setImageUrl("/uploads/services/existante.png");
            ids.add(servicesRepo.save(s).getId());
        }
        try {
            mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nomClient\":\"Trois Services\",\"ageClient\":33,\"sexe\":\"FEMME\",\"telephoneClient\":\"034 12 345 67\",\"praticienId\":" + medecinId + ",\"serviceIds\":[" + ids.get(0) + "," + ids.get(1) + "," + ids.get(2) + "]}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.serviceNom").value("Service multi A"))
                    .andExpect(jsonPath("$.servicesNoms.length()").value(3))
                    .andExpect(jsonPath("$.servicesNoms[1]").value("Service multi B"))
                    .andExpect(jsonPath("$.servicesNoms[2]").value("Service multi C"));
            rdvs.deleteAll();
            // un seul service (ancienne forme) et service inconnu
            mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nomClient\":\"Un Service\",\"ageClient\":33,\"sexe\":\"FEMME\",\"telephoneClient\":\"034 12 345 67\",\"praticienId\":" + medecinId + ",\"serviceId\":" + ids.get(1) + "}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.servicesNoms.length()").value(1)).andExpect(jsonPath("$.serviceNom").value("Service multi B"));
            rdvs.deleteAll();
            mvc.perform(post("/api/rdv/sans-rendez-vous").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nomClient\":\"Service Inconnu\",\"ageClient\":33,\"sexe\":\"FEMME\",\"telephoneClient\":\"034 12 345 67\",\"praticienId\":" + medecinId + ",\"serviceIds\":[" + ids.get(0) + ",999999]}"))
                    .andExpect(status().isBadRequest());
        } finally {
            rdvs.deleteAll();
            ids.forEach(servicesRepo::deleteById);
        }
    }

    @Test
    void civilite_calculee_par_la_regle_commune() {
        org.junit.jupiter.api.Assertions.assertEquals("Adolescent", com.cabinet.cabinet.util.Civilite.de(17, "FEMME"));
        org.junit.jupiter.api.Assertions.assertEquals("Adolescent", com.cabinet.cabinet.util.Civilite.de(12, "HOMME"));
        org.junit.jupiter.api.Assertions.assertEquals("Enfant", com.cabinet.cabinet.util.Civilite.de(11, "FEMME"));
        org.junit.jupiter.api.Assertions.assertEquals("Mme", com.cabinet.cabinet.util.Civilite.de(18, "FEMME"));
        org.junit.jupiter.api.Assertions.assertEquals("Mr", com.cabinet.cabinet.util.Civilite.de(18, "homme"));
        org.junit.jupiter.api.Assertions.assertNull(com.cabinet.cabinet.util.Civilite.de(30, null));
        org.junit.jupiter.api.Assertions.assertNull(com.cabinet.cabinet.util.Civilite.de(30, "autre"));
        org.junit.jupiter.api.Assertions.assertEquals("Enfant", com.cabinet.cabinet.util.Civilite.de(5, null));
    }
}
