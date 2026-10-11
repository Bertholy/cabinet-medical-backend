package com.cabinet.cabinet;

import com.cabinet.cabinet.repository.SiteContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Éditeur du site : mise en forme des textes (gras, taille, couleur…) et textes ajoutés. */
class SiteEditeurTests extends BaseApiTest {
    @Autowired SiteContentRepository contenus;

    @BeforeEach
    void vider() { contenus.deleteAll(); }

    private org.springframework.test.web.servlet.ResultActions envoyer(String cle, String jeton, String json) throws Exception {
        var requete = put("/api/site-content/" + cle).contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(jeton == null ? requete : requete.header("Authorization", jeton));
    }

    @Test
    void miseEnFormeEnregistreeRelueEtRetiree() throws Exception {
        envoyer("accueil.hero.titre", admin, "{\"texte\":\"Bienvenue chez nous\",\"style\":{\"gras\":true,\"italique\":true,\"taille\":36,\"couleur\":\"#0F766E\",\"alignement\":\"center\"}}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.style.gras").value(true)).andExpect(jsonPath("$.style.taille").value(36))
                .andExpect(jsonPath("$.style.couleur").value("#0f766e")).andExpect(jsonPath("$.style.alignement").value("center"));
        // le site public relit le texte et sa mise en forme
        mvc.perform(get("/api/site-content")).andExpect(jsonPath("$['accueil.hero.titre'].texte").value("Bienvenue chez nous"))
                .andExpect(jsonPath("$['accueil.hero.titre'].style.italique").value(true));
        // modifier le texte seul conserve la mise en forme
        envoyer("accueil.hero.titre", admin, "{\"texte\":\"Autre titre\"}").andExpect(jsonPath("$.style.taille").value(36));
        // un objet vide retire la mise en forme (le texte reste)
        envoyer("accueil.hero.titre", admin, "{\"style\":{}}").andExpect(jsonPath("$.style").isEmpty()).andExpect(jsonPath("$.texte").value("Autre titre"));
        // sans texte ni mise en forme ni position, l'élément disparaît de la base
        envoyer("accueil.hero.titre", admin, "{\"texte\":\"\"}").andExpect(status().isOk());
        mvc.perform(get("/api/site-content")).andExpect(content().json("{}"));
    }

    @Test
    void miseEnFormeInvalideRefusee() throws Exception {
        String[] mauvais = {"{\"style\":{\"taille\":5}}", "{\"style\":{\"taille\":500}}", "{\"style\":{\"couleur\":\"red\"}}", "{\"style\":{\"couleur\":\"#12345\"}}",
                "{\"style\":{\"alignement\":\"justify\"}}", "{\"style\":{\"police\":\"Comic Sans\"}}", "{\"style\":{\"couleur\":\"#fff;background:url(x)\"}}"};
        for (String corps : mauvais) envoyer("accueil.hero.titre", admin, corps).andExpect(status().isBadRequest());
        mvc.perform(get("/api/site-content")).andExpect(content().json("{}"));
    }

    @Test
    void textesAjoutesEtDroits() throws Exception {
        envoyer("ajout.accueil.bas.k1", admin, "{\"texte\":\"Fermé le 25 décembre\\nBonnes fêtes !\",\"style\":{\"gras\":true}}").andExpect(status().isOk());
        mvc.perform(get("/api/site-content")).andExpect(jsonPath("$['ajout.accueil.bas.k1'].texte").value(containsString("Bonnes fêtes")));
        mvc.perform(delete("/api/site-content/ajout.accueil.bas.k1").header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/site-content")).andExpect(content().json("{}"));
        // personne d'autre que le super administrateur ne modifie le site
        for (String jeton : new String[]{secretaire, medecin, null}) envoyer("ajout.accueil.bas.k2", jeton, "{\"texte\":\"Intrus\"}").andExpect(status().is4xxClientError());
    }
}
