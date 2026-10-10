package com.cabinet.cabinet;

import com.cabinet.cabinet.enums.Role;
import com.cabinet.cabinet.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MessagerieTests extends BaseApiTest {
    @Autowired MessageRepository messages;

    @BeforeEach
    void vider() { messages.deleteAll(); }

    private String corps(String texte) { return "{\"contenu\":\"" + texte + "\"}"; }

    @Test
    void envoiReception_nonLus_etMarquageCommeLu() throws Exception {
        Long idMedecin = idDe("test.medecin@casafami.test");
        Long idAdmin = idDe("test.admin@casafami.test");

        mvc.perform(post("/api/messagerie/conversation/" + idMedecin).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps("Bonjour docteur")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.deMoi").value(true)).andExpect(jsonPath("$.contenu").value("Bonjour docteur"));
        mvc.perform(post("/api/messagerie/conversation/" + idMedecin).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps("Pouvez-vous passer au cabinet ?")))
                .andExpect(status().isCreated());

        // le médecin voit 2 messages non lus, venant de l'administrateur
        mvc.perform(get("/api/messagerie/non-lus").header("Authorization", medecin)).andExpect(jsonPath("$.nonLus").value(2));
        mvc.perform(get("/api/messagerie/contacts").header("Authorization", medecin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.nonLus").value(2))
                .andExpect(jsonPath("$.contacts[0].id").value(idAdmin.intValue()))
                .andExpect(jsonPath("$.contacts[0].nonLus").value(2))
                .andExpect(jsonPath("$.contacts[0].dernier.extrait").value(containsString("passer au cabinet")))
                .andExpect(jsonPath("$.contacts[0].dernier.moi").value(false));
        // l'expéditeur n'a rien de non lu
        mvc.perform(get("/api/messagerie/non-lus").header("Authorization", admin)).andExpect(jsonPath("$.nonLus").value(0));

        // ouvrir la conversation marque les messages reçus comme lus ; l'ordre est chronologique
        mvc.perform(get("/api/messagerie/conversation/" + idAdmin).header("Authorization", medecin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2)).andExpect(jsonPath("$.messages[0].contenu").value("Bonjour docteur"))
                .andExpect(jsonPath("$.messages[0].deMoi").value(false)).andExpect(jsonPath("$.messages[1].contenu").value(containsString("passer")));
        mvc.perform(get("/api/messagerie/non-lus").header("Authorization", medecin)).andExpect(jsonPath("$.nonLues").doesNotExist()).andExpect(jsonPath("$.nonLus").value(0));

        // réponse : visible côté administrateur, avec « lu » encore faux
        mvc.perform(post("/api/messagerie/conversation/" + idAdmin).header("Authorization", medecin).contentType(MediaType.APPLICATION_JSON).content(corps("J'arrive à 15h")))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/messagerie/non-lus").header("Authorization", admin)).andExpect(jsonPath("$.nonLus").value(1));
        mvc.perform(get("/api/messagerie/conversation/" + idMedecin).header("Authorization", admin))
                .andExpect(jsonPath("$.messages.length()").value(3)).andExpect(jsonPath("$.messages[2].deMoi").value(false))
                .andExpect(jsonPath("$.messages[0].deMoi").value(true)).andExpect(jsonPath("$.messages[0].lu").value(true));
    }

    @Test
    void conversationsPrivees_validations_etDroits() throws Exception {
        Long idMedecin = idDe("test.medecin@casafami.test");
        Long idSage = idDe("test.sf@casafami.test");
        mvc.perform(post("/api/messagerie/conversation/" + idMedecin).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps("Confidentiel")))
                .andExpect(status().isCreated());

        // une tierce personne ne voit rien de cette conversation (ni dans ses non-lus, ni dans sa conversation avec l'un des deux)
        mvc.perform(get("/api/messagerie/non-lus").header("Authorization", sageFemme)).andExpect(jsonPath("$.nonLus").value(0));
        mvc.perform(get("/api/messagerie/conversation/" + idMedecin).header("Authorization", sageFemme)).andExpect(jsonPath("$.messages.length()").value(0));
        mvc.perform(get("/api/messagerie/conversation/" + idDe("test.admin@casafami.test")).header("Authorization", sageFemme)).andExpect(jsonPath("$.messages.length()").value(0));
        // lire la conversation d'un autre ne marque pas ses messages comme lus
        mvc.perform(get("/api/messagerie/non-lus").header("Authorization", medecin)).andExpect(jsonPath("$.nonLus").value(1));

        // validations
        mvc.perform(post("/api/messagerie/conversation/" + idMedecin).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps("   "))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/messagerie/conversation/" + idMedecin).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps("x".repeat(2001)))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/messagerie/conversation/" + idDe("test.admin@casafami.test")).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps("à moi-même"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/messagerie/conversation/99999999").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps("inconnu"))).andExpect(status().isNotFound());
        assertEquals(1, messages.count());

        // tous les rôles du personnel peuvent écrire ; sans connexion, rien
        for (String jeton : new String[]{secretaire, medecin, sageFemme}) {
            mvc.perform(get("/api/messagerie/contacts").header("Authorization", jeton)).andExpect(status().isOk());
        }
        mvc.perform(post("/api/messagerie/conversation/" + idSage).header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(corps("Bonjour")))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/messagerie/contacts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/messagerie/non-lus")).andExpect(status().isUnauthorized());
    }

    @Test
    void supprimerUnCompte_supprimeSesMessages() throws Exception {
        String jeton = tokenFor("test.ephemere.msg@casafami.test", Role.SAGE_FEMME);
        Long idEphemere = idDe("test.ephemere.msg@casafami.test");
        mvc.perform(post("/api/messagerie/conversation/" + idDe("test.admin@casafami.test")).header("Authorization", jeton).contentType(MediaType.APPLICATION_JSON).content(corps("Je pars")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/messagerie/conversation/" + idEphemere).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(corps("Au revoir")))
                .andExpect(status().isCreated());
        assertEquals(2, messages.count());
        mvc.perform(delete("/api/admin/users/" + idEphemere).header("Authorization", admin)).andExpect(status().isNoContent());
        assertEquals(0, messages.count());
    }
}
