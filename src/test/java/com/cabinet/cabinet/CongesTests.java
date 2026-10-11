package com.cabinet.cabinet;

import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.enums.Role;
import com.cabinet.cabinet.repository.JourFermeRepository;
import com.cabinet.cabinet.repository.NotificationRepository;
import com.cabinet.cabinet.repository.RendezVousRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Jours de congé : notification dans le compte de chaque membre du personnel et SMS à ceux qui ont un numéro. */
class CongesTests extends BaseApiTest {
    @Autowired NotificationRepository notifications;
    @Autowired JourFermeRepository joursFermes;
    @Autowired RendezVousRepository rdvs;

    @BeforeEach
    void preparer() {
        notifications.deleteAll();
        rdvs.deleteAll();
        joursFermes.deleteAll();
    }

    private void telephone(String email, String numero) {
        Utilisateur u = users.findByEmailIgnoreCase(email).orElseThrow();
        u.setTelephone(numero);
        users.save(u);
    }

    private LocalDate jourOuvre(int plus) {
        LocalDate d = LocalDate.now(ZoneId.of("Indian/Antananarivo")).plusDays(plus);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) d = d.plusDays(1);
        return d;
    }

    @Test
    void lesCongesPrevientLePersonnelParNotificationEtSms() throws Exception {
        telephone("test.medecin@casafami.test", "034 11 111 11");
        telephone("test.sf@casafami.test", "0329876543");
        telephone("test.secretaire@casafami.test", null);
        String autreAdmin = tokenFor("test.admin2@casafami.test", Role.SUPER_ADMIN);
        long attendusSms = users.findAll().stream().filter(u -> !u.getEmail().equals("test.admin@casafami.test") && u.getTelephone() != null && !u.getTelephone().isBlank()).count();
        long attenduPersonnes = users.count() - 1; // tout le monde sauf celui qui pose le congé

        LocalDate conge = jourOuvre(40);
        // un rendez-vous existe déjà ce jour-là : seul le super administrateur en est averti
        mvc.perform(post("/api/rdv").contentType(MediaType.APPLICATION_JSON).content("{\"nomClient\":\"Patient Deja Pris\",\"ageClient\":30,\"telephoneClient\":\"0341234567\",\"motif\":\"x\",\"dateRdv\":\"" + conge + "\",\"heureRdv\":\"09:00:00\"}"))
                .andExpect(status().isCreated());
        notifications.deleteAll();

        mvc.perform(post("/api/admin/jours-fermes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + conge + "\",\"motif\":\"Fête nationale\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.personnelPrevenu").value((int) attenduPersonnes))
                .andExpect(jsonPath("$.smsEnvoyes").value((int) attendusSms));

        // le médecin : notification claire + lien vers le calendrier
        mvc.perform(get("/api/notifications").header("Authorization", medecin)).andExpect(jsonPath("$.nonLues").value(1))
                .andExpect(jsonPath("$.notifications[0].type").value("CONGE"))
                .andExpect(jsonPath("$.notifications[0].titre").value("Jour de fermeture du cabinet"))
                .andExpect(jsonPath("$.notifications[0].message").value(containsString("Fête nationale")))
                .andExpect(jsonPath("$.notifications[0].message").value(containsString("fermé le")))
                .andExpect(jsonPath("$.notifications[0].lien").value("/calendrier"));
        // la secrétaire (sans téléphone) reçoit quand même la notification
        mvc.perform(get("/api/notifications").header("Authorization", secretaire)).andExpect(jsonPath("$.notifications[0].type").value("CONGE"));
        // celui qui pose le congé n'est pas notifié de son propre geste
        mvc.perform(get("/api/notifications").header("Authorization", admin)).andExpect(jsonPath("$.nonLues").value(0));
        // l'autre super administrateur est averti du rendez-vous déjà pris et envoyé vers la page des horaires
        mvc.perform(get("/api/notifications").header("Authorization", autreAdmin)).andExpect(jsonPath("$.notifications[0].lien").value("/admin/planning"))
                .andExpect(jsonPath("$.notifications[0].message").value(containsString("1 rendez-vous existent déjà")));

        // rouvrir le jour : le personnel est prévenu aussi
        String id = extraire(mvc.perform(get("/api/planning")).andReturn(), "id");
        mvc.perform(delete("/api/admin/jours-fermes/" + id).header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications").header("Authorization", medecin)).andExpect(jsonPath("$.notifications[0].titre").value("Fermeture annulée"))
                .andExpect(jsonPath("$.notifications[0].message").value(containsString("finalement ouvert")));

        // seul le super administrateur peut poser un congé
        mvc.perform(post("/api/admin/jours-fermes").header("Authorization", medecin).contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"" + conge + "\"}")).andExpect(status().isForbidden());
    }
}
