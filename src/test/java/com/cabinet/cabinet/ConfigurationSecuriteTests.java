package com.cabinet.cabinet;

import com.cabinet.cabinet.config.ConfigurationSecurite;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** Le serveur doit refuser de démarrer avec une clé de connexion absente, trop courte ou publique. */
class ConfigurationSecuriteTests {

    @Test
    void refuseUneCleAbsente() {
        assertThrows(IllegalStateException.class, () -> new ConfigurationSecurite("").afterPropertiesSet());
        assertThrows(IllegalStateException.class, () -> new ConfigurationSecurite("   ").afterPropertiesSet());
        assertThrows(IllegalStateException.class, () -> new ConfigurationSecurite(null).afterPropertiesSet());
    }

    @Test
    void refuseLAncienneClePubliqueDuDepot() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ConfigurationSecurite("NDA0RTYzNTM2RTcxMzI3QTY5NkI1OTdCM0Y0NDIyMjY0ODU2NkI1OTdCM0Y0NDIyNjQ=").afterPropertiesSet());
        assertTrue(e.getMessage().contains("publique"));
    }

    @Test
    void refuseUneCleTropCourteOuMalFormee() {
        String courte = Base64.getEncoder().encodeToString("trop-courte".getBytes());
        assertThrows(IllegalStateException.class, () -> new ConfigurationSecurite(courte).afterPropertiesSet());
        assertThrows(IllegalStateException.class, () -> new ConfigurationSecurite("pas du base64 !!").afterPropertiesSet());
    }

    @Test
    void accepteUneCleSolide() {
        String solide = Base64.getEncoder().encodeToString(new byte[48]);
        assertDoesNotThrow(() -> new ConfigurationSecurite(solide).afterPropertiesSet());
    }

    @Test
    void lesSecretsNeSontPlusDansLeDepot() throws Exception {
        String propriete = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/application.properties"));
        assertFalse(propriete.contains("NDA0RTYz"), "l'ancienne clé JWT ne doit plus figurer dans application.properties");
        assertTrue(propriete.contains("${CASAFAMI_DB_PASSWORD:}"), "aucun mot de passe de base par défaut");
        assertTrue(propriete.contains("${CASAFAMI_JWT_SECRET:}"), "aucune clé JWT par défaut");
    }
}
