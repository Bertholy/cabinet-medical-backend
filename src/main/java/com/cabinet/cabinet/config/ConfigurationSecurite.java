package com.cabinet.cabinet.config;

import io.jsonwebtoken.io.Decoders;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Refuse de démarrer avec une clé de connexion absente, trop courte ou connue publiquement : une clé écrite dans le code
 * (ou publiée sur GitHub) permettrait à n'importe qui de fabriquer une connexion de super administrateur.
 */
@Component
public class ConfigurationSecurite implements InitializingBean {
    /** Ancienne valeur par défaut, restée dans l'historique Git : elle est considérée comme compromise. */
    private static final String ANCIENNE_CLE_PUBLIQUE = "NDA0RTYzNTM2RTcxMzI3QTY5NkI1OTdCM0Y0NDIyMjY0ODU2NkI1OTdCM0Y0NDIyNjQ=";

    private final String cle;

    public ConfigurationSecurite(@Value("${jwt.secret:}") String cle) {
        this.cle = cle == null ? "" : cle.trim();
    }

    @Override
    public void afterPropertiesSet() {
        String aide = " Créez le fichier backend/cabinet/config/application.properties à partir de config/application.example.properties"
                + " (ou définissez la variable d'environnement CASAFAMI_JWT_SECRET). Générer une clé : openssl rand -base64 48";
        if (cle.isEmpty()) throw new IllegalStateException("La clé de connexion (CASAFAMI_JWT_SECRET) n'est pas définie." + aide);
        if (cle.equals(ANCIENNE_CLE_PUBLIQUE)) throw new IllegalStateException("La clé de connexion est l'ancienne valeur publique du dépôt : choisissez-en une nouvelle." + aide);
        byte[] octets;
        try { octets = Decoders.BASE64.decode(cle); }
        catch (RuntimeException e) { throw new IllegalStateException("La clé de connexion (CASAFAMI_JWT_SECRET) n'est pas du Base64 valide." + aide, e); }
        if (octets.length < 32) throw new IllegalStateException("La clé de connexion est trop courte (256 bits minimum, soit 32 octets)." + aide);
    }
}
