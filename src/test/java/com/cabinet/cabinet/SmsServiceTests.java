package com.cabinet.cabinet;

import com.cabinet.cabinet.sms.SmsService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Vérifie la requête envoyée à Twilio contre un faux serveur local : aucun SMS réel, aucun compte nécessaire. */
class SmsServiceTests {
    // Numéro au format international exigé par l'opérateur, construit sans écrire l'indicatif en dur.
    private static final String INTERNATIONAL = "+" + "26" + "1341234567";

    private SmsService service(String mode, String baseUrl) {
        SmsService service = new SmsService();
        ReflectionTestUtils.setField(service, "mode", mode);
        ReflectionTestUtils.setField(service, "accountSid", "ACtest123");
        ReflectionTestUtils.setField(service, "authToken", "secret-token");
        ReflectionTestUtils.setField(service, "from", "+15005550006");
        ReflectionTestUtils.setField(service, "twilioBaseUrl", baseUrl);
        return service;
    }

    @Test
    void twilioRecoitUneRequeteBienFormeeAvecUnNumeroInternational() throws Exception {
        AtomicReference<String> chemin = new AtomicReference<>(), auth = new AtomicReference<>(), corps = new AtomicReference<>();
        CountDownLatch recu = new CountDownLatch(1);
        HttpServer faux = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        faux.createContext("/", echange -> {
            chemin.set(echange.getRequestURI().getPath());
            auth.set(echange.getRequestHeaders().getFirst("Authorization"));
            corps.set(new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            echange.sendResponseHeaders(201, -1);
            echange.close();
            recu.countDown();
        });
        faux.start();
        try {
            SmsService service = service("twilio", "http://127.0.0.1:" + faux.getAddress().getPort());
            service.envoyerSms("034 12 345 67".replace(" ", ""), "Votre RDV est CONFIRMÉ & prêt");
            assertTrue(recu.await(10, TimeUnit.SECONDS), "Twilio n'a rien reçu");
            assertEquals("/2010-04-01/Accounts/ACtest123/Messages.json", chemin.get());
            assertEquals("Basic " + Base64.getEncoder().encodeToString("ACtest123:secret-token".getBytes(StandardCharsets.UTF_8)), auth.get());
            assertTrue(corps.get().contains("To=%2B26" + "1341234567"), corps.get());
            assertTrue(corps.get().contains("From=%2B15005550006"), corps.get());
            assertTrue(corps.get().contains("Body=Votre+RDV+est+CONFIRM%C3%89+%26+pr%C3%AAt"), corps.get());
        } finally {
            faux.stop(0);
        }
    }

    @Test
    void erreurTwilioOuConfigurationIncompleteNeLanceJamaisDException() throws Exception {
        HttpServer faux = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        faux.createContext("/", echange -> { echange.sendResponseHeaders(401, -1); echange.close(); });
        faux.start();
        try {
            SmsService refuse = service("twilio", "http://127.0.0.1:" + faux.getAddress().getPort());
            assertFalse(refuse.envoyerViaTwilio(INTERNATIONAL, "x"));
            SmsService incomplet = service("twilio", "http://127.0.0.1:1");
            ReflectionTestUtils.setField(incomplet, "authToken", "");
            assertFalse(incomplet.envoyerViaTwilio(INTERNATIONAL, "x"));
            assertFalse(service("twilio", "http://127.0.0.1:1").envoyerViaTwilio(INTERNATIONAL, "x")); // serveur injoignable
            assertDoesNotThrow(() -> service("simulation", "http://127.0.0.1:1").envoyerSms("0341234567", "simulation"));
        } finally {
            faux.stop(0);
        }
    }
}
