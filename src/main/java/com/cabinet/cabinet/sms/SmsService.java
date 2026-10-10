package com.cabinet.cabinet.sms;

import com.cabinet.cabinet.util.PhoneUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Envoi de SMS.
 *
 * - sms.mode=simulation (par défaut) : le message est seulement écrit dans les journaux du serveur, rien n'est envoyé.
 * - sms.mode=twilio : envoi réel via l'API Twilio (renseigner sms.twilio.account-sid, auth-token et from).
 *
 * L'envoi a lieu APRÈS la validation de la transaction en cours (un rendez-vous annulé par une erreur ne déclenche pas de SMS)
 * et en arrière-plan : un opérateur lent ne bloque jamais l'enregistrement d'un rendez-vous. Une erreur d'envoi est journalisée
 * mais ne fait jamais échouer l'action métier.
 */
@Service
@Slf4j
public class SmsService {

    @Value("${sms.mode:simulation}")
    private String mode;

    @Value("${sms.twilio.account-sid:}")
    private String accountSid;

    @Value("${sms.twilio.auth-token:}")
    private String authToken;

    /** Numéro expéditeur Twilio (+1…) ou identifiant d'expéditeur alphanumérique « CASAFAMI » si l'opérateur l'autorise. */
    @Value("${sms.twilio.from:}")
    private String from;

    @Value("${sms.twilio.base-url:https://api.twilio.com}")
    private String twilioBaseUrl;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "sms-sender");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public void envoyerSms(String numeroSaisi, String message) {
        // Les opérateurs exigent le format international alors que les numéros sont enregistrés au format local (034…) : conversion ici.
        String numero = numeroSaisi != null && numeroSaisi.startsWith("+") ? numeroSaisi : PhoneUtils.toInternational(numeroSaisi);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    planifier(numero, message);
                }
            });
        } else {
            planifier(numero, message);
        }
    }

    private void planifier(String numero, String message) {
        if ("twilio".equalsIgnoreCase(mode)) executor.submit(() -> envoyerViaTwilio(numero, message));
        else envoyerEnSimulation(numero, message);
    }

    private void envoyerEnSimulation(String numero, String message) {
        log.info("");
        log.info("═══════════════════════════════════════════════════════");
        log.info("📱 SMS SIMULÉ");
        log.info("   Destinataire : {}", numero);
        log.info("   Message      : {}", message);
        log.info("═══════════════════════════════════════════════════════");
        log.info("");
    }

    /** @return true si Twilio a accepté le message (code 201). */
    public boolean envoyerViaTwilio(String numero, String message) {
        if (accountSid.isBlank() || authToken.isBlank() || from.isBlank()) {
            log.error("SMS non envoyé à {} : sms.mode=twilio mais sms.twilio.account-sid / auth-token / from ne sont pas renseignés.", numero);
            return false;
        }
        try {
            String corps = "To=" + encoder(numero) + "&From=" + encoder(from) + "&Body=" + encoder(message);
            String identifiants = Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
            HttpRequest requete = HttpRequest.newBuilder(URI.create(twilioBaseUrl + "/2010-04-01/Accounts/" + accountSid + "/Messages.json"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Basic " + identifiants)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(corps))
                    .build();
            HttpResponse<String> reponse = http.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() == 201) {
                log.info("SMS envoyé à {}", numero);
                return true;
            }
            log.error("SMS refusé par Twilio pour {} (code {}) : {}", numero, reponse.statusCode(), reponse.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Envoi du SMS à {} impossible : {}", numero, e.getMessage());
        }
        return false;
    }

    private static String encoder(String valeur) {
        return URLEncoder.encode(valeur, StandardCharsets.UTF_8);
    }
}
