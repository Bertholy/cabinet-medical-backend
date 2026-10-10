package com.cabinet.cabinet;

import com.cabinet.cabinet.util.TextRepair;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Garde-fous contre les caractères cassés (« mÃ©dicale » au lieu de « médicale »).
 * Le premier test scanne tous les fichiers du projet : il échoue dès qu'un fichier source contient un double encodage
 * ou le caractère de remplacement U+FFFD, avant même que cela n'atteigne un utilisateur.
 */
class EncodageTests {
    // Ces fichiers contiennent volontairement des exemples de texte cassé (ils servent à les détecter et à les réparer).
    private static final Set<String> EXEMPLES_VOLONTAIRES = Set.of("TextRepair.java", "TextRepairListener.java", "DonneesReparateur.java", "EncodageTests.java", "check-encoding.mjs");
    private static final Set<String> DOSSIERS_IGNORES = Set.of("node_modules", "target", "dist", ".git", "uploads", "backups", ".mvn");
    private static final Set<String> EXTENSIONS = Set.of("java", "jsx", "js", "mjs", "css", "html", "svg", "json", "md", "properties", "sql", "ps1", "xml", "txt", "editorconfig", "gitattributes");

    // Ã n'existe pas en français ; Â n'est cassé que suivi d'un signe latin-1 (« Â » + espace insécable, « Â« »…) ; « Âge » est correct.
    private static final java.util.regex.Pattern CASSE = java.util.regex.Pattern.compile("[\\u00C3\\uFFFD]|\\u00C2[\\u00A0-\\u00BF]|\\u00E2\\u20AC");
    private static final String C3 = String.valueOf((char) 0x00C3), C2 = String.valueOf((char) 0x00C2), E2 = String.valueOf((char) 0x00E2), FFFD = String.valueOf((char) 0xFFFD);

    @Test
    void aucunFichierDuProjetNeContientDeCaractereCasse() throws IOException {
        Path racine = Path.of("..", "..").toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(racine.resolve("frontend")), "racine du projet introuvable : " + racine);
        List<String> problemes = new ArrayList<>();
        try (Stream<Path> fichiers = Files.walk(racine)) {
            fichiers.filter(Files::isRegularFile)
                    .filter(f -> racine.relativize(f).getNameCount() > 0 && DOSSIERS_IGNORES.stream().noneMatch(d -> racine.relativize(f).toString().replace('\\', '/').contains(d + "/") || racine.relativize(f).toString().replace('\\', '/').startsWith(d + "/")))
                    .filter(f -> !EXEMPLES_VOLONTAIRES.contains(f.getFileName().toString()))
                    .filter(f -> EXTENSIONS.contains(extension(f)))
                    .forEach(f -> controler(racine, f, problemes));
        }
        assertTrue(problemes.isEmpty(), "Caractères cassés détectés :\n" + String.join("\n", problemes));
    }

    private static String extension(Path f) {
        String nom = f.getFileName().toString();
        return nom.contains(".") ? nom.substring(nom.lastIndexOf('.') + 1).toLowerCase() : nom.replace(".", "");
    }

    private static void controler(Path racine, Path fichier, List<String> problemes) {
        byte[] octets;
        try { octets = Files.readAllBytes(fichier); } catch (IOException e) { return; }
        String relatif = racine.relativize(fichier).toString();
        String texte = new String(octets, StandardCharsets.UTF_8);
        java.util.regex.Matcher casse = CASSE.matcher(texte);
        if (casse.find()) {
            int index = casse.start();
            problemes.add(" - " + relatif + " (près de : « " + texte.substring(Math.max(0, index - 15), Math.min(texte.length(), index + 25)).replace('\n', ' ') + " »)");
        }
        // Un script PowerShell avec des accents doit être enregistré en UTF-8 AVEC BOM, sinon Windows PowerShell 5 le lit en ANSI.
        boolean accents = false;
        for (byte b : octets) if (b < 0) { accents = true; break; }
        boolean bom = octets.length >= 3 && (octets[0] & 0xFF) == 0xEF && (octets[1] & 0xFF) == 0xBB && (octets[2] & 0xFF) == 0xBF;
        if (relatif.endsWith(".ps1") && accents && !bom) problemes.add(" - " + relatif + " (script PowerShell avec accents sans BOM UTF-8)");
    }

    // ------------------------------------------------------------------ réparation d'un texte déjà cassé

    private static String cassé(String correct) {
        // Reproduit l'accident : octets UTF-8 relus comme du Windows-1252.
        return new String(correct.getBytes(StandardCharsets.UTF_8), java.nio.charset.Charset.forName("windows-1252"));
    }

    @Test
    void reparationDesTextesCassesTrouvesEnBase() {
        assertEquals("Consultation médicale générale", TextRepair.repair("Consultation mÃ©dicale gÃ©nÃ©rale"));
        assertEquals("Échographie", TextRepair.repair("Ã‰chographie"));
        assertEquals("Un accompagnement tout au long de votre maternité.", TextRepair.repair("Un accompagnement tout au long de votre maternitÃ©."));
        assertEquals("Prélèvement sanguin", TextRepair.repair("PrÃ©lÃ¨vement sanguin"));
        for (String correct : new String[]{"à côté, où l'été ça m'est égal", "Œuf, cœur, ÉCOLE, François, Zoé", "Soins infirmiers — pansements « express » €", "naïve garçon Noël"}) {
            assertEquals(correct, TextRepair.repair(cassé(correct)), "réparation de : " + correct);
        }
        assertEquals("médical", TextRepair.repair(cassé(cassé("médical"))), "double encodage");
    }

    @Test
    void unTexteCorrectOuAmbiguNEstJamaisModifie() {
        for (String texte : new String[]{"médicale", "Échographie", "À bientôt", "Soins infirmiers", "", "034 12 345 67", "SÃO PAULO", "Â", "Ã", "prix 5 €", "Ça va très bien !"}) {
            assertEquals(texte, TextRepair.repair(texte), texte);
        }
        assertNull(TextRepair.repair(null));
    }
}
