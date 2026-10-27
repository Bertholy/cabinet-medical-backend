package com.cabinet.cabinet;

import com.cabinet.cabinet.enums.Role;
import com.cabinet.cabinet.repository.NotificationRepository;
import com.cabinet.cabinet.repository.PermissionUtilisateurRepository;
import com.cabinet.cabinet.repository.RendezVousRepository;
import com.cabinet.cabinet.security.PermissionService;
import com.cabinet.cabinet.security.Permissions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Rôles et permissions : les droits d'origine sont inchangés, le super administrateur peut les régler par rôle et par personne. */
class PermissionsTests extends BaseApiTest {
    @Autowired PermissionService permissions;
    @Autowired PermissionUtilisateurRepository exceptions;
    @Autowired RendezVousRepository rdvs;
    @Autowired NotificationRepository notifications;

    @BeforeEach
    void preparer() {
        remettreAZero();
        notifications.deleteAll();
        rdvs.deleteAll();
    }

    @AfterEach
    void remettreAZero() {
        for (Role r : PermissionService.ROLES_REGLABLES) permissions.retablirRole(r);
        exceptions.deleteAll();
        permissions.invalider();
        notifications.deleteAll();   // les notifications de changement de droits ne doivent pas fausser les autres tests
    }

    private static String liste(String... codes) {
        StringBuilder sb = new StringBuilder("{\"permissions\":[");
        for (int i = 0; i < codes.length; i++) sb.append(i > 0 ? "," : "").append('"').append(codes[i]).append('"');
        return sb.append("]}").toString();
    }

    private void definirRole(String role, String... codes) throws Exception {
        mvc.perform(put("/api/admin/permissions/roles/" + role).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(liste(codes)))
                .andExpect(status().isOk());
    }

    private LocalDate jourOuvre(int plus) {
        LocalDate d = LocalDate.now(ZoneId.of("Indian/Antananarivo")).plusDays(plus);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) d = d.plusDays(1);
        return d;
    }

    private String corpsRdv(LocalDate jour, String heure) {
        return "{\"nomClient\":\"Patient Droits\",\"ageClient\":30,\"telephoneClient\":\"0341234567\",\"motif\":\"x\",\"dateRdv\":\"" + jour + "\",\"heureRdv\":\"" + heure + "\"}";
    }

    @Test
    void lesDroitsDOrigineNeChangentRien() throws Exception {
        // statistiques : médecin et secrétaire, pas la sage-femme
        mvc.perform(get("/api/stats/synthese").header("Authorization", medecin)).andExpect(status().isOk());
        mvc.perform(get("/api/stats/synthese").header("Authorization", secretaire)).andExpect(status().isOk());
        mvc.perform(get("/api/stats/synthese").header("Authorization", sageFemme)).andExpect(status().isForbidden());
        // fiches maladies : lecture au personnel soignant, jamais à la secrétaire
        mvc.perform(get("/api/maladies").header("Authorization", sageFemme)).andExpect(status().isOk());
        mvc.perform(get("/api/maladies").header("Authorization", secretaire)).andExpect(status().isForbidden());
        // corbeille : secrétaire et super administrateur seulement
        mvc.perform(get("/api/rdv/corbeille").header("Authorization", secretaire)).andExpect(status().isOk());
        mvc.perform(get("/api/rdv/corbeille").header("Authorization", medecin)).andExpect(status().isForbidden());
        // administration : super administrateur seulement
        for (String url : new String[]{"/api/admin/users", "/api/admin/permissions", "/api/admin/journal", "/api/admin/occupation?date=" + jourOuvre(5), "/api/admin/sauvegardes"}) {
            mvc.perform(get(url).header("Authorization", admin)).andExpect(status().isOk());
            mvc.perform(get(url).header("Authorization", medecin)).andExpect(status().isForbidden());
            mvc.perform(get(url).header("Authorization", secretaire)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/permissions")).andExpect(status().isUnauthorized());
    }

    @Test
    void ecranDesDroitsListeLeCatalogueLesRolesEtLesPersonnes() throws Exception {
        mvc.perform(get("/api/admin/permissions").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogue", hasSize(Permissions.CATALOGUE.size())))
                .andExpect(jsonPath("$.roles", hasSize(4)))
                .andExpect(jsonPath("$.roles[?(@.role=='SECRETAIRE')].permissions[0]", hasItem("EXPORT_RDV")))
                .andExpect(jsonPath("$.roles[?(@.role=='SECRETAIRE')].modifie", hasItem(false)))
                .andExpect(jsonPath("$.personnes[?(@.email=='test.admin@casafami.test')].effectives", hasItem(hasSize(Permissions.CATALOGUE.size()))));
    }

    @Test
    void accorderEtRetirerUnDroitAUnRoleSAppliqueToutDeSuite() throws Exception {
        // la secrétaire garde la corbeille mais perd les statistiques ; le médecin gagne la corbeille
        definirRole("SECRETAIRE", Permissions.RDV_VOIR, Permissions.RDV_CORBEILLE, Permissions.EXPORT_RDV);
        mvc.perform(get("/api/stats/synthese").header("Authorization", secretaire)).andExpect(status().isForbidden());
        mvc.perform(get("/api/rdv/corbeille").header("Authorization", secretaire)).andExpect(status().isOk());
        // les droits non listés sont retirés : plus de recherche de patients ni de création
        mvc.perform(get("/api/patients/recherche?q=abc").header("Authorization", secretaire)).andExpect(status().isForbidden());

        definirRole("MEDECIN", Permissions.RDV_VOIR, Permissions.RDV_CORBEILLE, Permissions.STATS_VOIR);
        mvc.perform(get("/api/rdv/corbeille").header("Authorization", medecin)).andExpect(status().isOk());
        mvc.perform(get("/api/maladies").header("Authorization", medecin)).andExpect(status().isForbidden());

        // retour aux droits d'origine
        mvc.perform(post("/api/admin/permissions/roles/MEDECIN/defauts").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/permissions/roles/SECRETAIRE/defauts").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/rdv/corbeille").header("Authorization", medecin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/maladies").header("Authorization", medecin)).andExpect(status().isOk());
        mvc.perform(get("/api/stats/synthese").header("Authorization", secretaire)).andExpect(status().isOk());
    }

    @Test
    void uneExceptionPersonnelleNeToucheQueCettePersonne() throws Exception {
        Long idSageFemme = idDe("test.sf@casafami.test");
        Long idMedecin = idDe("test.medecin@casafami.test");
        // accordé à la sage-femme seulement
        mvc.perform(put("/api/admin/permissions/utilisateurs/" + idSageFemme).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"STATS_VOIR\",\"accorde\":true}")).andExpect(status().isOk());
        mvc.perform(get("/api/stats/synthese").header("Authorization", sageFemme)).andExpect(status().isOk());
        // retiré au médecin seulement, bien que son rôle l'ait
        mvc.perform(put("/api/admin/permissions/utilisateurs/" + idMedecin).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"STATS_VOIR\",\"accorde\":false}")).andExpect(status().isOk());
        mvc.perform(get("/api/stats/synthese").header("Authorization", medecin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/stats/synthese").header("Authorization", secretaire)).andExpect(status().isOk());
        // « selon le rôle » supprime l'exception
        mvc.perform(put("/api/admin/permissions/utilisateurs/" + idMedecin).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"STATS_VOIR\",\"accorde\":null}")).andExpect(status().isOk());
        mvc.perform(get("/api/stats/synthese").header("Authorization", medecin)).andExpect(status().isOk());
        // tout remettre pour une personne
        mvc.perform(post("/api/admin/permissions/utilisateurs/" + idSageFemme + "/defauts").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/stats/synthese").header("Authorization", sageFemme)).andExpect(status().isForbidden());
    }

    @Test
    void laPersonneEstPrevenueQuandUnAccesLuiEstAccordeOuRetire() throws Exception {
        Long idSecretaire = idDe("test.secretaire@casafami.test");
        mvc.perform(get("/api/notifications").header("Authorization", secretaire)).andExpect(jsonPath("$.nonLues").value(0));
        // un droit accordé en plus : la secrétaire le lit dans sa cloche
        mvc.perform(put("/api/admin/permissions/utilisateurs/" + idSecretaire).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"OCCUPATION_VOIR\",\"accorde\":true}")).andExpect(status().isOk());
        mvc.perform(get("/api/notifications").header("Authorization", secretaire)).andExpect(jsonPath("$.nonLues").value(1))
                .andExpect(jsonPath("$.notifications[0].type").value("ACCES_MODIFIE"))
                .andExpect(jsonPath("$.notifications[0].titre").value("Nouvel accès"))
                .andExpect(jsonPath("$.notifications[0].message", containsString("Vous pouvez maintenant voir l'occupation du personnel")));
        // le même réglage répété ne change rien : pas de nouvelle notification
        mvc.perform(put("/api/admin/permissions/utilisateurs/" + idSecretaire).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"OCCUPATION_VOIR\",\"accorde\":true}")).andExpect(status().isOk());
        mvc.perform(get("/api/notifications").header("Authorization", secretaire)).andExpect(jsonPath("$.nonLues").value(1));
        // les autres membres de l'équipe ne sont pas concernés
        mvc.perform(get("/api/notifications").header("Authorization", medecin)).andExpect(jsonPath("$.nonLues").value(0));
        // le droit retiré : elle en est aussi prévenue
        mvc.perform(put("/api/admin/permissions/utilisateurs/" + idSecretaire).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"OCCUPATION_VOIR\",\"accorde\":null}")).andExpect(status().isOk());
        mvc.perform(get("/api/notifications").header("Authorization", secretaire)).andExpect(jsonPath("$.nonLues").value(2))
                .andExpect(jsonPath("$.notifications[0].titre").value("Accès retiré"));
        // un réglage de rôle prévient chaque personne du rôle : la secrétaire perd la recherche de patients
        definirRole("SECRETAIRE", Permissions.RDV_VOIR);
        mvc.perform(get("/api/notifications").header("Authorization", secretaire)).andExpect(jsonPath("$.nonLues").value(3))
                .andExpect(jsonPath("$.notifications[0].message", containsString("Vous ne pouvez plus")));
    }

    @Test
    void lesDroitsDAdministrationRestentReservesAuSuperAdministrateur() throws Exception {
        for (String interdit : new String[]{Permissions.COMPTES_GERER, Permissions.PERMISSIONS_GERER, Permissions.SAUVEGARDES_GERER}) {
            mvc.perform(put("/api/admin/permissions/roles/MEDECIN").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(liste(interdit)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("réservée au super administrateur")));
            mvc.perform(put("/api/admin/permissions/utilisateurs/" + idDe("test.sf@casafami.test")).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"" + interdit + "\",\"accorde\":true}")).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/admin/users").header("Authorization", medecin)).andExpect(status().isForbidden());
        // permission inconnue, rôle non réglable, super administrateur
        mvc.perform(put("/api/admin/permissions/roles/MEDECIN").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(liste("N_IMPORTE_QUOI")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/permissions/roles/SUPER_ADMIN").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(liste()))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/permissions/roles/INCONNU").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(liste()))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/permissions/utilisateurs/" + idDe("test.admin@casafami.test")).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"STATS_VOIR\",\"accorde\":false}")).andExpect(status().isBadRequest());
        // le super administrateur garde tout, quoi qu'il arrive
        mvc.perform(get("/api/stats/synthese").header("Authorization", admin)).andExpect(status().isOk());
    }

    @Test
    void personneSaufLeSuperAdministrateurNePeutRegler() throws Exception {
        for (String token : new String[]{medecin, sageFemme, secretaire}) {
            mvc.perform(put("/api/admin/permissions/roles/SECRETAIRE").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(liste(Permissions.RDV_VOIR)))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/permissions/utilisateurs/" + idDe("test.sf@casafami.test")).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"STATS_VOIR\",\"accorde\":true}")).andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/admin/permissions/roles/SECRETAIRE").contentType(MediaType.APPLICATION_JSON).content(liste())).andExpect(status().isUnauthorized());
    }

    @Test
    void laConnexionEtLeProfilRenvoientLesDroits() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"test.secretaire@casafami.test\",\"motDePasse\":\"motdepasse-test-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasItem("RDV_CORBEILLE")))
                .andExpect(jsonPath("$.permissions", not(hasItem("DOSSIERS_LIRE"))));
        mvc.perform(get("/api/profile").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissions", hasSize(Permissions.CATALOGUE.size())));
        definirRole("SECRETAIRE", Permissions.RDV_VOIR);
        mvc.perform(get("/api/profile").header("Authorization", secretaire))
                .andExpect(jsonPath("$.permissions", hasSize(1))).andExpect(jsonPath("$.permissions", hasItem("RDV_VOIR")));
    }

    @Test
    void creerUnRendezVousDemandeLaPermissionAuPersonnelMaisJamaisAuPatient() throws Exception {
        LocalDate jour = jourOuvre(50);
        definirRole("SECRETAIRE", Permissions.RDV_VOIR);
        mvc.perform(post("/api/rdv").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(corpsRdv(jour, "09:00:00")))
                .andExpect(status().isForbidden());
        // le patient (sans compte) réserve toujours
        mvc.perform(post("/api/rdv").contentType(MediaType.APPLICATION_JSON).content(corpsRdv(jour, "09:00:00"))).andExpect(status().isCreated());
        // le droit rendu, la secrétaire peut créer
        definirRole("SECRETAIRE", Permissions.RDV_VOIR, Permissions.RDV_CREER);
        mvc.perform(post("/api/rdv").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(corpsRdv(jour, "09:30:00")))
                .andExpect(status().isCreated());
    }

    @Test
    void laDelegationDeLAttributionEtDeLaConfirmationFonctionneDeBoutEnBout() throws Exception {
        LocalDate jour = jourOuvre(52);
        MvcResult cree = mvc.perform(post("/api/rdv").contentType(MediaType.APPLICATION_JSON).content(corpsRdv(jour, "10:00:00"))).andExpect(status().isCreated()).andReturn();
        String id = extraire(cree, "id");
        long medecinId = idDe("test.medecin@casafami.test");
        String attribution = "{\"praticienId\":" + medecinId + "}";

        // par défaut, la secrétaire ne peut ni attribuer ni confirmer
        mvc.perform(put("/api/rdv/" + id + "/praticien").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(attribution)).andExpect(status().isForbidden());
        mvc.perform(put("/api/rdv/" + id + "/confirmer").header("Authorization", secretaire)).andExpect(status().isForbidden());

        // le super administrateur lui délègue les deux étapes
        definirRole("SECRETAIRE", Permissions.RDV_VOIR, Permissions.RDV_ATTRIBUER, Permissions.RDV_CONFIRMER);
        mvc.perform(put("/api/rdv/" + id + "/praticien").header("Authorization", secretaire).contentType(MediaType.APPLICATION_JSON).content(attribution)).andExpect(status().isOk());
        mvc.perform(put("/api/rdv/" + id + "/confirmer").header("Authorization", secretaire)).andExpect(status().isConflict()); // le médecin n'a pas encore accepté
        mvc.perform(put("/api/rdv/" + id + "/accepter").header("Authorization", medecin)).andExpect(status().isOk());
        mvc.perform(put("/api/rdv/" + id + "/confirmer").header("Authorization", secretaire)).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("CONFIRME"));
    }

    @Test
    void retirerLaPermissionDAccepterEmpecheLePraticienDAccepter() throws Exception {
        LocalDate jour = jourOuvre(54);
        String id = extraire(mvc.perform(post("/api/rdv").contentType(MediaType.APPLICATION_JSON).content(corpsRdv(jour, "10:00:00"))).andExpect(status().isCreated()).andReturn(), "id");
        mvc.perform(put("/api/rdv/" + id + "/praticien").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"praticienId\":" + idDe("test.medecin@casafami.test") + "}")).andExpect(status().isOk());
        definirRole("MEDECIN", Permissions.RDV_VOIR, Permissions.DOSSIERS_LIRE);
        mvc.perform(put("/api/rdv/" + id + "/accepter").header("Authorization", medecin)).andExpect(status().isForbidden());
    }

    @Test
    void chaqueChangementDeDroitsEstJournalise() throws Exception {
        definirRole("INFIRMIERE", Permissions.RDV_VOIR);
        mvc.perform(put("/api/admin/permissions/utilisateurs/" + idDe("test.sf@casafami.test")).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"STATS_VOIR\",\"accorde\":true}")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/journal?action=PERMISSIONS_ROLE&q=INFIRMIERE").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mvc.perform(get("/api/admin/journal?action=PERMISSIONS_PERSONNE&q=STATS_VOIR").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void supprimerUnCompteEffaceSesExceptions() throws Exception {
        String jetable = tokenFor("test.jetable@casafami.test", Role.SECRETAIRE);
        Long id = idDe("test.jetable@casafami.test");
        mvc.perform(put("/api/admin/permissions/utilisateurs/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"STATS_VOIR\",\"accorde\":false}")).andExpect(status().isOk());
        mvc.perform(get("/api/stats/synthese").header("Authorization", jetable)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/users/" + id).header("Authorization", admin)).andExpect(status().isNoContent());
    }
}
