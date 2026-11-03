package com.cabinet.cabinet;

import com.cabinet.cabinet.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** « Mon profil » : chacun corrige son propre nom et son téléphone ; l'e-mail et le rôle ne bougent pas. */
class ProfilTests extends BaseApiTest {

    @Test
    void chacun_corrige_son_nom_et_son_telephone() throws Exception {
        String moi = tokenFor("test.profil@casafami.test", Role.SECRETAIRE);
        mvc.perform(put("/api/profile").header("Authorization", moi).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"  Rasoa Hanta \",\"telephone\":\"034 12 345 67\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Rasoa Hanta"))
                .andExpect(jsonPath("$.telephone").value("034 12 345 67"))
                .andExpect(jsonPath("$.email").value("test.profil@casafami.test"))
                .andExpect(jsonPath("$.role").value("SECRETAIRE"));
        mvc.perform(get("/api/profile").header("Authorization", moi))
                .andExpect(jsonPath("$.nom").value("Rasoa Hanta")).andExpect(jsonPath("$.telephone").value("034 12 345 67"));
        // un téléphone vide l'efface
        mvc.perform(put("/api/profile").header("Authorization", moi).contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"Rasoa Hanta\",\"telephone\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.telephone").doesNotExist());
    }

    @Test
    void le_nom_est_obligatoire_et_les_longueurs_sont_limitees() throws Exception {
        String moi = tokenFor("test.profil@casafami.test", Role.SECRETAIRE);
        mvc.perform(put("/api/profile").header("Authorization", moi).contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"  \",\"telephone\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/profile").header("Authorization", moi).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Rasoa\",\"telephone\":\"0123456789012345678901\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void il_faut_etre_connecte() throws Exception {
        mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"Rasoa\"}")).andExpect(status().is4xxClientError());
    }
}
