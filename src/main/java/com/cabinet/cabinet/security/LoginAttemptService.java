package com.cabinet.cabinet.security;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freine les attaques par essais répétés de mots de passe :
 * 5 échecs sur un même compte depuis une même adresse, ou 30 échecs depuis une même adresse, bloquent la connexion 15 minutes.
 * Les compteurs sont en mémoire (ils repartent à zéro au redémarrage du serveur).
 */
@Service
public class LoginAttemptService {
    static final int MAX_PAR_COMPTE = 5;
    static final int MAX_PAR_ADRESSE = 30;
    static final Duration FENETRE = Duration.ofMinutes(15);

    private record Compteur(int echecs, Instant debut) { }

    private final Map<String, Compteur> compteurs = new ConcurrentHashMap<>();

    private static String cleCompte(String email, String adresse) { return "c|" + email.trim().toLowerCase() + "|" + adresse; }
    private static String cleAdresse(String adresse) { return "a|" + adresse; }

    public boolean estBloque(String email, String adresse) {
        return depasse(cleCompte(email, adresse), MAX_PAR_COMPTE) || depasse(cleAdresse(adresse), MAX_PAR_ADRESSE);
    }

    public void echec(String email, String adresse) {
        incrementer(cleCompte(email, adresse));
        incrementer(cleAdresse(adresse));
    }

    public void succes(String email, String adresse) {
        compteurs.remove(cleCompte(email, adresse));
    }

    /** Minutes restantes avant le déblocage (au moins 1). */
    public long minutesRestantes(String email, String adresse) {
        Compteur c = compteurs.get(cleCompte(email, adresse));
        Compteur a = compteurs.get(cleAdresse(adresse));
        Instant fin = Instant.now();
        if (c != null && c.echecs() >= MAX_PAR_COMPTE) fin = max(fin, c.debut().plus(FENETRE));
        if (a != null && a.echecs() >= MAX_PAR_ADRESSE) fin = max(fin, a.debut().plus(FENETRE));
        return Math.max(1, Duration.between(Instant.now(), fin).toMinutes() + 1);
    }

    private static Instant max(Instant x, Instant y) { return x.isAfter(y) ? x : y; }

    private boolean depasse(String cle, int limite) {
        Compteur c = compteurs.get(cle);
        if (c == null) return false;
        if (c.debut().plus(FENETRE).isBefore(Instant.now())) { compteurs.remove(cle); return false; }
        return c.echecs() >= limite;
    }

    private void incrementer(String cle) {
        Instant maintenant = Instant.now();
        compteurs.merge(cle, new Compteur(1, maintenant), (ancien, neuf) ->
                ancien.debut().plus(FENETRE).isBefore(maintenant) ? neuf : new Compteur(ancien.echecs() + 1, ancien.debut()));
        if (compteurs.size() > 10_000) compteurs.entrySet().removeIf(e -> e.getValue().debut().plus(FENETRE).isBefore(maintenant));
    }
}
