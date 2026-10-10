package com.cabinet.cabinet.config;

import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.enums.Role;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SuperAdminBootstrap implements ApplicationRunner {
    private final UtilisateurRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.countByRole(Role.SUPER_ADMIN) > 0) return;
        String email = environment.getProperty("CASAFAMI_SUPER_ADMIN_EMAIL", "").trim().toLowerCase();
        String password = environment.getProperty("CASAFAMI_SUPER_ADMIN_PASSWORD", "");
        if (email.isBlank() || password.isBlank()) return;

        Utilisateur user = users.findByEmailIgnoreCase(email).orElseGet(() -> {
            Utilisateur created = new Utilisateur();
            created.setNom("Super administrateur");
            created.setEmail(email);
            created.setMotDePasse(passwordEncoder.encode(password));
            created.setRole(Role.SUPER_ADMIN);
            return created;
        });
        user.setRole(Role.SUPER_ADMIN);
        users.save(user);
    }
}
