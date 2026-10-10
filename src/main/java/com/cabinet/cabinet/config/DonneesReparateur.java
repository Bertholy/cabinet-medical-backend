package com.cabinet.cabinet.config;

import com.cabinet.cabinet.service.AuditService;
import com.cabinet.cabinet.util.PhoneUtils;
import com.cabinet.cabinet.util.TextRepair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Remise en ordre des données à chaque démarrage (rapide et sans effet sur des données déjà correctes) :
 *
 * 1. Textes à double encodage (« mÃ©dicale » → « médicale ») : réparés dans toutes les colonnes de texte de l'application.
 *    À l'écriture, TextRepairListener empêche déjà ces textes d'entrer ; cette étape nettoie ce qui a pu être inséré
 *    par un script SQL ou un import extérieur.
 * 2. Numéros de téléphone : tous ramenés à la forme locale à 10 chiffres (034 12 345 67), quelle que soit la façon dont
 *    ils avaient été saisis, pour que la recherche par numéro retrouve toujours le bon patient.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DonneesReparateur implements ApplicationRunner {
    private record Colonne(String table, String id, String colonne) { }

    private static final List<Colonne> COLONNES_TEXTE = List.of(
            new Colonne("service", "id", "nom"), new Colonne("service", "id", "description"), new Colonne("service", "id", "duree"),
            new Colonne("maladie", "id", "nom"), new Colonne("maladie", "id", "description"), new Colonne("maladie", "id", "symptomes"),
            new Colonne("traitement", "id", "medicament"), new Colonne("traitement", "id", "posologie"),
            new Colonne("traitement", "id", "duree"), new Colonne("traitement", "id", "remarques"),
            new Colonne("rendez_vous", "id", "nom_client"), new Colonne("rendez_vous", "id", "motif"),
            new Colonne("utilisateur", "id", "nom"),
            new Colonne("dossier_patient", "id", "nom"), new Colonne("dossier_patient", "id", "allergies"),
            new Colonne("dossier_patient", "id", "antecedents"), new Colonne("dossier_patient", "id", "notes"),
            new Colonne("consultation", "id", "notes"), new Colonne("consultation", "id", "diagnostic"),
            new Colonne("consultation", "id", "ordonnance"), new Colonne("consultation", "id", "auteur_nom"),
            new Colonne("site_content", "cle", "texte"), new Colonne("jour_ferme", "id", "motif"));

    private static final List<Colonne> COLONNES_TELEPHONE = List.of(
            new Colonne("rendez_vous", "id", "telephone_client"), new Colonne("consultation", "id", "telephone"),
            new Colonne("dossier_patient", "id", "telephone"));

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    @Override
    public void run(ApplicationArguments args) {
        int textes = reparerTextes();
        int telephones = normaliserTelephones();
        if (textes > 0) {
            log.warn("{} texte(s) à double encodage réparé(s) dans la base.", textes);
            audit.logAs("systeme", "SYSTEME", "ENCODAGE_REPARE", null, null, textes + " texte(s) réparé(s) (ex. « mÃ©dicale » → « médicale »)");
        }
        if (telephones > 0) log.info("{} numéro(s) de téléphone ramené(s) au format local.", telephones);
    }

    public int reparerTextes() {
        int total = 0;
        for (Colonne c : COLONNES_TEXTE) {
            try {
                List<Map<String, Object>> lignes = jdbc.queryForList("select " + c.id() + " as id, " + c.colonne() + " as v from " + c.table()
                        + " where " + c.colonne() + " ~ '[ÃÂâ]'");
                for (Map<String, Object> ligne : lignes) {
                    String avant = (String) ligne.get("v");
                    String apres = TextRepair.repair(avant);
                    if (apres != null && !apres.equals(avant)) {
                        jdbc.update("update " + c.table() + " set " + c.colonne() + " = ? where " + c.id() + " = ?", apres, ligne.get("id"));
                        total++;
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Contrôle d'encodage ignoré pour {}.{} : {}", c.table(), c.colonne(), e.getMessage());
            }
        }
        return total;
    }

    public int normaliserTelephones() {
        int total = 0;
        for (Colonne c : COLONNES_TELEPHONE) {
            try {
                List<Map<String, Object>> lignes = jdbc.queryForList("select " + c.id() + " as id, " + c.colonne() + " as v from " + c.table()
                        + " where " + c.colonne() + " is not null");
                for (Map<String, Object> ligne : lignes) {
                    String avant = (String) ligne.get("v");
                    String apres = PhoneUtils.canonical(avant);
                    if (apres == null || apres.equals(avant)) continue;
                    try {
                        jdbc.update("update " + c.table() + " set " + c.colonne() + " = ? where " + c.id() + " = ?", apres, ligne.get("id"));
                        total++;
                    } catch (RuntimeException conflit) {
                        log.warn("Numéro {} conservé tel quel dans {} (le format local existe déjà).", avant, c.table());
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Normalisation des numéros ignorée pour {}.{} : {}", c.table(), c.colonne(), e.getMessage());
            }
        }
        return total;
    }
}
