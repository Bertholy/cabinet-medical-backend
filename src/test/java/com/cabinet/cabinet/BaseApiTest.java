package com.cabinet.cabinet;

import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.enums.Role;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import com.cabinet.cabinet.security.CustomUserDetailsService;
import com.cabinet.cabinet.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Socle des tests d'API : MockMvc avec la sécurité réelle et un jeton pour chaque rôle (base de test « casafami_test »). */
@SpringBootTest
@ActiveProfiles("test")
abstract class BaseApiTest {
    @Autowired WebApplicationContext context;
    @Autowired UtilisateurRepository users;
    @Autowired JwtService jwtService;
    @Autowired CustomUserDetailsService userDetailsService;
    @Autowired PasswordEncoder encoder;

    MockMvc mvc;
    String admin, medecin, sageFemme, secretaire;

    @BeforeEach
    void socle() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        admin = tokenFor("test.admin@casafami.test", Role.SUPER_ADMIN);
        medecin = tokenFor("test.medecin@casafami.test", Role.MEDECIN);
        sageFemme = tokenFor("test.sf@casafami.test", Role.SAGE_FEMME);
        secretaire = tokenFor("test.secretaire@casafami.test", Role.SECRETAIRE);
    }

    String tokenFor(String email, Role role) {
        users.findByEmailIgnoreCase(email).orElseGet(() -> {
            Utilisateur u = new Utilisateur();
            u.setNom("Test " + role);
            u.setEmail(email);
            u.setMotDePasse(encoder.encode("motdepasse-test-1"));
            u.setRole(role);
            return users.save(u);
        });
        return "Bearer " + jwtService.genererToken(userDetailsService.loadUserByUsername(email));
    }

    Long idDe(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow().getId();
    }

    /** Valeur d'un champ texte ou numérique dans la réponse JSON (première occurrence). */
    static String extraire(MvcResult r, String champ) throws Exception {
        Matcher m = Pattern.compile("\"" + champ + "\"\\s*:\\s*\"?([^\",}\\]]+)").matcher(r.getResponse().getContentAsString());
        if (!m.find()) throw new AssertionError("Champ « " + champ + " » absent de : " + r.getResponse().getContentAsString());
        return m.group(1);
    }
}
