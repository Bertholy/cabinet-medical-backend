package com.cabinet.cabinet;

import com.cabinet.cabinet.entity.ReinitialisationMotDePasse;
import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.enums.Role;
import com.cabinet.cabinet.repository.NotificationRepository;
import com.cabinet.cabinet.repository.ReinitialisationMotDePasseRepository;
import com.cabinet.cabinet.service.CourrierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MotDePasseOublieTests extends BaseApiTest {
    private static final String EMAIL = "oubli.test@casafami.test";
    private static final String ANCIEN = "ancien-motdepasse-1";
    private static final AtomicInteger ADRESSE = new AtomicInteger(1);

    @MockitoBean CourrierService courrier;
    @Autowired ReinitialisationMotDePasseRepository liens;
    @Autowired NotificationRepository notifications;

    @BeforeEach
    void preparer() {
        notifications.deleteAll();
        liens.deleteAll();
        Utilisateur u = users.findByEmailIgnoreCase(EMAIL).orElseGet(Utilisateur::new);
        u.setNom("Oubli Test");
        u.setEmail(EMAIL);
        u.setRole(Role.MEDECIN);
        u.setMotDePasse(encoder.encode(ANCIEN));
        users.save(u);
        reset(courrier);
        when(courrier.envoyer(any(), any(), any())).thenReturn(true);
    }

    /** Chaque appel utilise une adresse IP différente : la limite par adresse ne perturbe pas les autres tests. */
    private ResultActions poster(String url, String json, String ip) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json).with(r -> { r.setRemoteAddr(ip); return r; }));
    }

    private String nouvelleAdresse() { return "10.1.0." + ADRESSE.getAndIncrement(); }

    private ResultActions demander(String email, String ip) throws Exception {
        return poster("/api/auth/mot-de-passe-oublie", "{\"email\":\"" + email + "\"}", ip);
    }

    private ResultActions reinitialiser(String token, String motDePasse, String ip) throws Exception {
        return poster("/api/auth/reinitialiser", "{\"token\":\"" + token + "\",\"motDePasse\":\"" + motDePasse + "\"}", ip);
    }

    private String dernierLien() {
        ArgumentCaptor<String> texte = ArgumentCaptor.forClass(String.class);
        verify(courrier, atLeastOnce()).envoyer(eq(EMAIL), any(), texte.capture());
        Matcher m = Pattern.compile("(http://site.test/medecin/reinitialiser\\?token=)([0-9a-f]{64})").matcher(texte.getValue());
        assertTrue(m.find(), "le lien de réinitialisation doit figurer dans l'e-mail : " + texte.getValue());
        return m.group(2);
    }

    private int connexion(String motDePasse) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"motDePasse\":\"" + motDePasse + "\"}")).andReturn().getResponse().getStatus();
    }

    @Test
    void parcoursComplet_demande_lien_nouveauMotDePasse_etLienUsageUnique() throws Exception {
        demander(EMAIL, nouvelleAdresse()).andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value(containsString("Si un compte correspond")));
        String lien = dernierLien();
        assertEquals(64, lien.length());

        // les super administrateurs sont prévenus (même sans serveur d'e-mails)
        mvc.perform(get("/api/notifications").header("Authorization", admin)).andExpect(jsonPath("$.nonLues").value(1))
                .andExpect(jsonPath("$.notifications[0].type").value("MOT_DE_PASSE"))
                .andExpect(jsonPath("$.notifications[0].lien").value("/admin/utilisateurs"))
                .andExpect(jsonPath("$.notifications[0].message").value(containsString("Oubli Test")));
        // la demande seule ne change rien
        assertEquals(200, connexion(ANCIEN));

        reinitialiser(lien, "nouveau-motdepasse-2", nouvelleAdresse()).andExpect(status().isOk());
        assertEquals(401, connexion(ANCIEN));
        assertEquals(200, connexion("nouveau-motdepasse-2"));

        // le lien ne sert qu'une fois
        reinitialiser(lien, "encore-un-autre-mdp-3", nouvelleAdresse()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("invalide ou a expiré")));
        assertEquals(200, connexion("nouveau-motdepasse-2"));
        // seule l'empreinte du lien est conservée : le lien lui-même n'est nulle part en base
        assertTrue(liens.findAll().stream().noneMatch(l -> l.getTokenHash().equals(lien)));
    }

    @Test
    void adresseInconnue_memeReponse_aucunEnvoi() throws Exception {
        String connu = demander(EMAIL, nouvelleAdresse()).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        reset(courrier);
        String inconnu = demander("personne@casafami.test", nouvelleAdresse()).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertEquals(connu, inconnu, "la réponse ne doit pas révéler si le compte existe");
        verify(courrier, never()).envoyer(any(), any(), any());
        demander("   ", nouvelleAdresse()).andExpect(status().is4xxClientError());
    }

    @Test
    void motDePasseTropCourt_refuse_etLeLienResteUtilisable() throws Exception {
        demander(EMAIL, nouvelleAdresse());
        String lien = dernierLien();
        reinitialiser(lien, "court", nouvelleAdresse()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("10")));
        reinitialiser(lien, "assez-long-pour-etre-valide", nouvelleAdresse()).andExpect(status().isOk());
    }

    @Test
    void lienExpire_ouInconnu_refuse() throws Exception {
        demander(EMAIL, nouvelleAdresse());
        String lien = dernierLien();
        ReinitialisationMotDePasse demande = liens.findAll().get(0);
        demande.setExpireLe(LocalDateTime.now().minusMinutes(1));
        liens.save(demande);
        reinitialiser(lien, "assez-long-pour-etre-valide", nouvelleAdresse()).andExpect(status().isBadRequest());
        reinitialiser("0".repeat(64), "assez-long-pour-etre-valide", nouvelleAdresse()).andExpect(status().isBadRequest());
        assertEquals(200, connexion(ANCIEN));
    }

    @Test
    void uneNouvelleDemandeInvalideLAncienLien_etLeNombreDeDemandesEstLimite() throws Exception {
        demander(EMAIL, nouvelleAdresse());
        String premier = dernierLien();
        reset(courrier);
        when(courrier.envoyer(any(), any(), any())).thenReturn(true);
        demander(EMAIL, nouvelleAdresse());
        String second = dernierLien();
        assertNotEquals(premier, second);
        reinitialiser(premier, "assez-long-pour-etre-valide", nouvelleAdresse()).andExpect(status().isBadRequest());
        reinitialiser(second, "assez-long-pour-etre-valide", nouvelleAdresse()).andExpect(status().isOk());

        // au plus 3 demandes par compte et par 15 minutes (les suivantes sont ignorées en silence)
        liens.deleteAll();
        reset(courrier);
        when(courrier.envoyer(any(), any(), any())).thenReturn(true);
        for (int i = 0; i < 5; i++) demander(EMAIL, nouvelleAdresse()).andExpect(status().isAccepted());
        verify(courrier, times(3)).envoyer(eq(EMAIL), any(), any());
    }

    @Test
    void limiteParAdresse_etSuppressionDuCompteAvecDemandes() throws Exception {
        String ip = nouvelleAdresse();
        for (int i = 0; i < 10; i++) demander("anonyme" + i + "@casafami.test", ip).andExpect(status().isAccepted());
        demander("anonyme11@casafami.test", ip).andExpect(status().isTooManyRequests());

        // un compte qui a des demandes en cours se supprime sans erreur
        demander(EMAIL, nouvelleAdresse());
        assertFalse(liens.findAll().isEmpty());
        mvc.perform(delete("/api/admin/users/" + idDe(EMAIL)).header("Authorization", admin)).andExpect(status().isNoContent());
        assertTrue(liens.findAll().isEmpty());
    }
}
