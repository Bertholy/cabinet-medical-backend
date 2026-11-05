package com.cabinet.cabinet;

import com.cabinet.cabinet.entity.Notification;
import com.cabinet.cabinet.entity.RendezVous;
import com.cabinet.cabinet.enums.StatutRDV;
import com.cabinet.cabinet.repository.NotificationRepository;
import com.cabinet.cabinet.repository.RendezVousRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Une personne du personnel à qui un rendez-vous est attribué peut seulement l'ACCEPTER (la secrétaire est alors prévenue pour le confirmer au patient)
 * ou le CONFIRMER elle-même (elle l'accepte du même coup, et personne n'est prévenu).
 */
class AcceptationConfirmationTests extends BaseApiTest {
    @Autowired RendezVousRepository rdvs;
    @Autowired NotificationRepository notifications;

    @BeforeEach
    @AfterEach
    void nettoyer() {
        notifications.deleteAll();
        rdvs.deleteAll();
    }

    private LocalDate jourOuvre() {
        LocalDate d = LocalDate.now(ZoneId.of("Indian/Antananarivo")).plusDays(1);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) d = d.plusDays(1);
        return d;
    }

    /** Un rendez-vous en attente, déjà attribué à la sage-femme de test. */
    private Long rdvAttribue(String nom, String heure) {
        RendezVous r = new RendezVous();
        r.setNomClient(nom); r.setAgeClient(30); r.setTelephoneClient("034 12 000 0" + heure.charAt(1));
        r.setDateRdv(jourOuvre()); r.setHeureRdv(LocalTime.parse(heure)); r.setStatut(StatutRDV.EN_ATTENTE);
        r.setPraticien(users.findByEmail("test.sf@casafami.test").orElseThrow());
        return rdvs.save(r).getId();
    }

    private List<Notification> recues(String email) {
        return notifications.findByDestinataireIdOrderByCreeLeDescIdDesc(idDe(email), PageRequest.of(0, 50));
    }

    @Test
    void accepter_previent_la_secretaire_et_les_super_administrateurs_pour_confirmer() throws Exception {
        Long id = rdvAttribue("Rasoa Hanta", "09:00");
        mvc.perform(put("/api/rdv/" + id + "/accepter").header("Authorization", sageFemme)).andExpect(status().isOk());

        List<Notification> secretaire = recues("test.secretaire@casafami.test");
        assertEquals(1, secretaire.size());
        assertEquals("RDV_ACCEPTE", secretaire.get(0).getType());
        assertTrue(secretaire.get(0).getLien().startsWith("/espace/secretariat?rdv=" + id), "la secrétaire ouvre la demande sur son tableau de bord");
        assertEquals(1, recues("test.admin@casafami.test").size());
        assertEquals(0, recues("test.sf@casafami.test").size(), "celle qui accepte n'est pas prévenue de sa propre action");
        assertEquals(0, recues("test.medecin@casafami.test").size(), "le personnel soignant n'est pas prévenu des acceptations des collègues");

        // la secrétaire peut maintenant le confirmer
        mvc.perform(put("/api/rdv/" + id + "/confirmer").header("Authorization", secretaire())).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("CONFIRME"));
    }

    @Test
    void confirmer_soi_meme_accepte_du_meme_coup_sans_prevenir_la_secretaire() throws Exception {
        Long id = rdvAttribue("Rakoto Jean", "09:30");
        mvc.perform(put("/api/rdv/" + id + "/confirmer").header("Authorization", sageFemme))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("CONFIRME")).andExpect(jsonPath("$.personnelConfirme").value(true));

        assertEquals(0, recues("test.secretaire@casafami.test").size(), "aucune notification pour la secrétaire");
        assertEquals(0, recues("test.admin@casafami.test").size(), "ni pour le super administrateur");
        assertEquals(0, recues("test.sf@casafami.test").size(), "ni pour celle qui vient de le confirmer");
    }

    @Test
    void la_secretaire_ne_confirme_pas_un_rendez_vous_pas_encore_accepte() throws Exception {
        Long id = rdvAttribue("Lova", "10:00");
        mvc.perform(put("/api/rdv/" + id + "/confirmer").header("Authorization", secretaire)).andExpect(status().isConflict());
    }

    @Test
    void decliner_previent_la_secretaire_libere_la_personne_et_permet_de_changer_de_personne() throws Exception {
        Long id = rdvAttribue("Rasoa Hanta", "10:30");
        mvc.perform(put("/api/rdv/" + id + "/decliner").header("Authorization", sageFemme).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"motif\":\"Je suis en congé ce jour-là\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.praticienId").doesNotExist())
                .andExpect(jsonPath("$.declinePar").value("Test SAGE_FEMME"))
                .andExpect(jsonPath("$.declineMotif").value("Je suis en congé ce jour-là"));

        // la secrétaire et le super administrateur sont prévenus, pas celle qui décline
        List<Notification> secretaire = recues("test.secretaire@casafami.test");
        assertEquals(1, secretaire.size());
        assertEquals("RDV_DECLINE", secretaire.get(0).getType());
        assertTrue(secretaire.get(0).getLien().startsWith("/espace/secretariat?rdv=" + id));
        assertTrue(secretaire.get(0).getMessage().contains("Je suis en congé"));
        assertEquals(1, recues("test.admin@casafami.test").size());
        assertEquals(0, recues("test.sf@casafami.test").size());

        // « Changer de personne » : la secrétaire le confie au médecin, la trace du refus disparaît
        Long medecinId = idDe("test.medecin@casafami.test");
        mvc.perform(put("/api/rdv/" + id + "/praticien").header("Authorization", secretaire()).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"praticienId\":" + medecinId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.praticienId").value(medecinId)).andExpect(jsonPath("$.declinePar").doesNotExist());
        assertEquals(1, recues("test.medecin@casafami.test").size(), "le médecin est prévenu qu'on lui confie le rendez-vous");
    }

    @Test
    void chaque_decision_du_personnel_previent_la_secretaire() throws Exception {
        Long accepte = rdvAttribue("Rakoto Jean", "11:00");
        Long decline = rdvAttribue("Lova", "11:30");
        mvc.perform(put("/api/rdv/" + accepte + "/accepter").header("Authorization", sageFemme)).andExpect(status().isOk());
        mvc.perform(put("/api/rdv/" + decline + "/decliner").header("Authorization", sageFemme)).andExpect(status().isOk());
        List<String> types = recues("test.secretaire@casafami.test").stream().map(Notification::getType).sorted().toList();
        assertEquals(List.of("RDV_ACCEPTE", "RDV_DECLINE"), types);
    }

    @Test
    void on_ne_decline_que_ses_rendez_vous_en_cours() throws Exception {
        Long id = rdvAttribue("Tiana", "12:30");
        // un collègue ne peut pas décliner à la place de la personne attribuée
        mvc.perform(put("/api/rdv/" + id + "/decliner").header("Authorization", medecin)).andExpect(status().isForbidden());
        // une fois confirmé, il est trop tard
        mvc.perform(put("/api/rdv/" + id + "/confirmer").header("Authorization", sageFemme)).andExpect(status().isOk());
        mvc.perform(put("/api/rdv/" + id + "/decliner").header("Authorization", sageFemme)).andExpect(status().isBadRequest());
        // la secrétaire n'a pas à décliner à la place du personnel
        Long autre = rdvAttribue("Hery", "12:00");
        mvc.perform(put("/api/rdv/" + autre + "/decliner").header("Authorization", secretaire())).andExpect(status().isForbidden());
    }

    private String secretaire() { return secretaire; }
}
