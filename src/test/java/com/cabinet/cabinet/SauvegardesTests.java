package com.cabinet.cabinet;

import com.cabinet.cabinet.service.SauvegardeService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SauvegardesTests extends BaseApiTest {
    @Autowired SauvegardeService sauvegardes;

    @Test
    void sauvegardeManuelle_telechargement_conservation_etDroits() throws Exception {
        Assumptions.assumeTrue(sauvegardes.pgDumpDisponible(), "pg_dump n'est pas installé : test ignoré");
        Path dossier = sauvegardes.getDossier();
        if (Files.isDirectory(dossier)) try (Stream<Path> f = Files.walk(dossier)) { f.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(dossier)).forEach(p -> p.toFile().delete()); }

        String reponse = mvc.perform(post("/api/admin/sauvegardes").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nom").value(matchesPattern("casafami-\\d{8}-\\d{6}\\.dump")))
                .andReturn().getResponse().getContentAsString();
        String nom = reponse.replaceAll(".*\"nom\":\"(casafami-[0-9-]+\\.dump)\".*", "$1");

        mvc.perform(get("/api/admin/sauvegardes").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pgDumpDisponible").value(true)).andExpect(jsonPath("$.enRetard").value(false))
                .andExpect(jsonPath("$.fichiers[0].nom").value(nom)).andExpect(jsonPath("$.fichiers[0].type").value("base"));

        // le fichier téléchargé est bien une sauvegarde PostgreSQL (signature « PGDMP »)
        byte[] octets = mvc.perform(get("/api/admin/sauvegardes/" + nom).header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(nom))).andReturn().getResponse().getContentAsByteArray();
        assertEquals("PGDMP", new String(octets, 0, 5));

        // aucun chemin ne peut être injecté ; un nom valide mais absent n'existe pas
        mvc.perform(get("/api/admin/sauvegardes/application.properties").header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/sauvegardes/..%2F..%2Fpom.xml").header("Authorization", admin)).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/admin/sauvegardes/casafami-20200101-000000.dump").header("Authorization", admin)).andExpect(status().isNotFound());

        // seules les 2 dernières sauvegardes sont conservées (réglage de test)
        Thread.sleep(1100); mvc.perform(post("/api/admin/sauvegardes").header("Authorization", admin)).andExpect(status().isOk());
        Thread.sleep(1100); mvc.perform(post("/api/admin/sauvegardes").header("Authorization", admin)).andExpect(status().isOk());
        assertEquals(2, sauvegardes.lister().stream().filter(f -> f.type().equals("base")).count());
        mvc.perform(get("/api/admin/sauvegardes/" + nom).header("Authorization", admin)).andExpect(status().isNotFound()); // la plus ancienne a été supprimée

        // droits : super administrateur uniquement
        for (String jeton : new String[]{secretaire, medecin, sageFemme}) {
            mvc.perform(get("/api/admin/sauvegardes").header("Authorization", jeton)).andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/sauvegardes").header("Authorization", jeton)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/sauvegardes")).andExpect(status().isUnauthorized());
    }

    @Test
    void sansSauvegardeLeSystemeSeSignaleEnRetard() throws Exception {
        Path dossier = sauvegardes.getDossier();
        if (Files.isDirectory(dossier)) try (Stream<Path> f = Files.walk(dossier)) { f.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(dossier)).forEach(p -> p.toFile().delete()); }
        mvc.perform(get("/api/admin/sauvegardes").header("Authorization", admin)).andExpect(jsonPath("$.enRetard").value(true))
                .andExpect(jsonPath("$.fichiers.length()").value(0));
    }
}
