package com.cabinet.cabinet;

import com.cabinet.cabinet.repository.NotificationRepository;
import com.cabinet.cabinet.repository.RendezVousRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Petits nombres du menu du personnel : ce qui est nouveau depuis la dernière visite de chaque page. */
class NouveautesTests extends BaseApiTest {
    @Autowired RendezVousRepository rdvs;
    @Autowired NotificationRepository notifications;

    @BeforeEach
    void preparer() { notifications.deleteAll(); rdvs.deleteAll(); }

    private String avant() throws Exception {
        MvcResult r = mvc.perform(get("/api/nouveautes").header("Authorization", admin)).andExpect(status().isOk()).andReturn();
        return LocalDateTime.parse(extraire(r, "maintenant")).minusMinutes(1).toString();
    }

    private LocalDate jourOuvre(int plus) {
        LocalDate d = LocalDate.now(ZoneId.of("Indian/Antananarivo")).plusDays(plus);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) d = d.plusDays(1);
        return d;
    }

    private String creerRdv() throws Exception {
        MvcResult r = mvc.perform(post("/api/rdv").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nomClient\":\"Patient Nouveaute\",\"ageClient\":30,\"telephoneClient\":\"0341234567\",\"motif\":\"x\",\"dateRdv\":\"" + jourOuvre(60) + "\",\"heureRdv\":\"10:30:00\"}"))
                .andExpect(status().isCreated()).andReturn();
        return extraire(r, "id");
    }

    @Test
    void sansConnexionOnNeVoitRien() throws Exception {
        mvc.perform(get("/api/nouveautes")).andExpect(status().isUnauthorized());
    }

    @Test
    void sansHeureConnueAucunNombreEtLHeureDuServeurEstRenvoyee() throws Exception {
        creerRdv();
        mvc.perform(get("/api/nouveautes").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.maintenant").exists())
                .andExpect(jsonPath("$.rdv").value(0)).andExpect(jsonPath("$.corbeille").value(0));
    }

    @Test
    void unNouveauRendezVousApparaitPourLeSuperAdminPasPourUnSoignantNonConcerne() throws Exception {
        String depuis = avant();
        String id = creerRdv();
        mvc.perform(get("/api/nouveautes?rdv=" + depuis + "&calendrier=" + depuis).header("Authorization", admin))
                .andExpect(jsonPath("$.rdv").value(1)).andExpect(jsonPath("$.calendrier").value(1));
        mvc.perform(get("/api/nouveautes?rdv=" + depuis).header("Authorization", secretaire)).andExpect(jsonPath("$.rdv").value(1));
        // le médecin ne voit comme nouveau que ce qui lui est confié
        mvc.perform(get("/api/nouveautes?rdv=" + depuis).header("Authorization", medecin)).andExpect(jsonPath("$.rdv").value(0)).andExpect(jsonPath("$.aAccepter").value(0));
        mvc.perform(put("/api/rdv/" + id + "/praticien").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"praticienId\":" + idDe("test.medecin@casafami.test") + "}")).andExpect(status().isOk());
        mvc.perform(get("/api/nouveautes?rdv=" + depuis).header("Authorization", medecin)).andExpect(jsonPath("$.rdv").value(1)).andExpect(jsonPath("$.aAccepter").value(1));
        mvc.perform(put("/api/rdv/" + id + "/accepter").header("Authorization", medecin)).andExpect(status().isOk());
        mvc.perform(get("/api/nouveautes?rdv=" + depuis).header("Authorization", medecin)).andExpect(jsonPath("$.aAccepter").value(0));
        // une heure postérieure : plus rien de nouveau
        String plusTard = LocalDateTime.parse(depuis).plusMinutes(10).toString();
        mvc.perform(get("/api/nouveautes?rdv=" + plusTard).header("Authorization", admin)).andExpect(jsonPath("$.rdv").value(0));
    }

    @Test
    void laCorbeilleEtLeJournalCompte() throws Exception {
        String depuis = avant();
        String id = creerRdv();
        mvc.perform(delete("/api/rdv/" + id).header("Authorization", secretaire)).andExpect(status().isNoContent());
        mvc.perform(get("/api/nouveautes?corbeille=" + depuis + "&journal=" + depuis).header("Authorization", admin))
                .andExpect(jsonPath("$.corbeille").value(1)).andExpect(jsonPath("$.journal").value(greaterThanOrEqualTo(1)));
        mvc.perform(get("/api/nouveautes?corbeille=" + depuis).header("Authorization", secretaire)).andExpect(jsonPath("$.corbeille").value(1));
    }

    @Test
    void chaqueCompteurDependDesDroitsDeLaPersonne() throws Exception {
        String depuis = avant();
        // la secrétaire n'a ni dossiers, ni journal, ni acceptation ; le médecin n'a pas la corbeille ni le journal
        mvc.perform(get("/api/nouveautes?dossiers=" + depuis + "&journal=" + depuis).header("Authorization", secretaire))
                .andExpect(jsonPath("$", not(hasKey("dossiers")))).andExpect(jsonPath("$", not(hasKey("journal")))).andExpect(jsonPath("$", not(hasKey("aAccepter"))));
        mvc.perform(get("/api/nouveautes?corbeille=" + depuis + "&journal=" + depuis).header("Authorization", medecin))
                .andExpect(jsonPath("$", not(hasKey("corbeille")))).andExpect(jsonPath("$", not(hasKey("journal")))).andExpect(jsonPath("$.dossiers").value(0));
        // une valeur illisible est ignorée (pas d'erreur)
        mvc.perform(get("/api/nouveautes?rdv=n-importe-quoi").header("Authorization", admin)).andExpect(status().isOk()).andExpect(jsonPath("$.rdv").value(0));
    }
}
