package com.cabinet.cabinet;

import com.cabinet.cabinet.entity.RendezVous;
import com.cabinet.cabinet.repository.NotificationRepository;
import com.cabinet.cabinet.repository.RendezVousRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Réservation en ligne : un même nom ou un même numéro de téléphone ne peut pas faire deux réservations avant 7 jours (le personnel est exempté). */
@TestPropertySource(properties = "app.reservation.delai-jours=7")
class ReservationsTests extends BaseApiTest {
    @Autowired RendezVousRepository rdvs;
    @Autowired NotificationRepository notifications;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void preparer() { notifications.deleteAll(); rdvs.deleteAll(); }

    private LocalDate jourOuvre(int plus) {
        LocalDate d = LocalDate.now(ZoneId.of("Indian/Antananarivo")).plusDays(plus);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) d = d.plusDays(1);
        return d;
    }

    private ResultActions reserver(String nom, String tel, String heure, String token) throws Exception {
        var requete = post("/api/rdv").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nomClient\":\"" + nom + "\",\"ageClient\":30,\"telephoneClient\":\"" + tel + "\",\"motif\":\"x\",\"dateRdv\":\"" + jourOuvre(70) + "\",\"heureRdv\":\"" + heure + "\"}");
        return mvc.perform(token == null ? requete : requete.header("Authorization", token));
    }

    @Test
    void unMemeNomNePeutPasReserverDeuxFoisAvantSeptJours() throws Exception {
        reserver("Rakoto Jean", "0341111111", "08:00:00", null).andExpect(status().isCreated());
        reserver("Rakoto Jean", "0342222222", "08:30:00", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RESERVATION_RECENTE"))
                .andExpect(jsonPath("$.critere").value("NOM"))
                .andExpect(jsonPath("$.message", containsString("Une réservation existe déjà au nom de")))
                .andExpect(jsonPath("$.disponibleLe").exists()).andExpect(jsonPath("$.dateRdvExistant").exists()).andExpect(jsonPath("$.heureRdvExistant").value("08:00"));
        // aucun deuxième rendez-vous n'a été créé
        org.junit.jupiter.api.Assertions.assertEquals(1, rdvs.count());
    }

    @Test
    void lesMajusculesLesAccentsLOrdreEtLesEspacesNeContournentPasLaRegle() throws Exception {
        reserver("Rakoto Jean", "0341111111", "08:00:00", null).andExpect(status().isCreated());
        for (String variante : new String[]{"rakoto jean", "JEAN RAKOTO", "  Jéan   Rakoto ", "Rakoto-Jean"})
            reserver(variante, "0343333333", "09:00:00", null).andExpect(status().isUnprocessableContent());
    }

    @Test
    void unMemeNumeroNePeutPasReserverDeuxFoisAvantSeptJoursQuelQueSoitLeNom() throws Exception {
        reserver("Rakoto Jean", "034 11 111 11", "08:00:00", null).andExpect(status().isCreated());
        // un autre nom avec le même numéro est refusé, quel que soit le format saisi
        for (String numero : new String[]{"0341111111", "034 11 111 11", "34 11 111 11", "+261 34 11 111 11", "261341111111"}) {
            reserver("Rasoa Marie", numero, "09:30:00", null)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("RESERVATION_RECENTE"))
                    .andExpect(jsonPath("$.critere").value("TELEPHONE"))
                    .andExpect(jsonPath("$.message", containsString("ce numéro de téléphone")))
                    .andExpect(jsonPath("$.disponibleLe").exists()).andExpect(jsonPath("$.heureRdvExistant").value("08:00"));
        }
        org.junit.jupiter.api.Assertions.assertEquals(1, rdvs.count());
        // un autre numéro avec un autre nom reste possible
        reserver("Rasoa Marie", "0342222222", "09:30:00", null).andExpect(status().isCreated());
        // le personnel peut saisir un rendez-vous au même numéro
        reserver("Rasoa Zo", "0341111111", "10:00:00", secretaire).andExpect(status().isCreated());
    }

    @Test
    void lePremierRdvAnnuleLibereAussiLeNumero() throws Exception {
        reserver("Rabe Paul", "0341111111", "08:00:00", null).andExpect(status().isCreated());
        reserver("Soa Lala", "0341111111", "08:30:00", null).andExpect(status().isUnprocessableContent());
        mvc.perform(put("/api/rdv/" + rdvs.findAll().get(0).getId() + "/annuler").header("Authorization", secretaire)).andExpect(status().isOk());
        reserver("Soa Lala", "0341111111", "08:30:00", null).andExpect(status().isCreated());
    }

    @Test
    void leClientPeutReserverDeNouveauApresAnnulationOuApresSeptJours() throws Exception {
        reserver("Rabe Paul", "0341111111", "08:00:00", null).andExpect(status().isCreated());
        // annulée par le personnel : la place est libérée
        Long id = rdvs.findAll().get(0).getId();
        mvc.perform(put("/api/rdv/" + id + "/annuler").header("Authorization", secretaire)).andExpect(status().isOk());
        reserver("Rabe Paul", "0341111111", "08:30:00", null).andExpect(status().isCreated());
        // la réservation date de plus de 7 jours : de nouveau possible
        RendezVous actif = rdvs.findAll().stream().filter(r -> !"ANNULE".equals(r.getStatut().name())).findFirst().orElseThrow();
        jdbc.update("update rendez_vous set date_creation = ? where id = ?", LocalDateTime.now(ZoneId.of("Indian/Antananarivo")).minusDays(8), actif.getId());
        reserver("Rabe Paul", "0341111111", "09:00:00", null).andExpect(status().isCreated());
    }

    @Test
    void lePersonnelPeutSaisirDeuxRendezVousAuMemeNom() throws Exception {
        reserver("Randria Luc", "0341111111", "08:00:00", null).andExpect(status().isCreated());
        reserver("Randria Luc", "0341111111", "08:30:00", secretaire).andExpect(status().isCreated());
        reserver("Randria Luc", "0341111111", "09:00:00", medecin).andExpect(status().isCreated());
    }

    @Test
    void unRendezVousALaCorbeilleNeBloquePas() throws Exception {
        reserver("Razafy Tina", "0341111111", "08:00:00", null).andExpect(status().isCreated());
        Long id = rdvs.findAll().get(0).getId();
        mvc.perform(delete("/api/rdv/" + id).header("Authorization", secretaire)).andExpect(status().isNoContent());
        reserver("Razafy Tina", "0341111111", "08:30:00", null).andExpect(status().isCreated());
    }
}
