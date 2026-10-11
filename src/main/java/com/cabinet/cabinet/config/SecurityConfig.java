package com.cabinet.cabinet.config;

import com.cabinet.cabinet.security.CustomUserDetailsService;
import com.cabinet.cabinet.security.JwtAuthenticationFilter;
import com.cabinet.cabinet.security.PermissionService;
import com.cabinet.cabinet.security.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final CustomUserDetailsService userDetailsService;
    private final PermissionService permissions;

    /** Origines autorisées à appeler l'API depuis un navigateur (séparées par des virgules). En production : l'adresse publique du site. */
    @org.springframework.beans.factory.annotation.Value("${app.cors.origins:http://localhost:3000,http://localhost:5173,http://127.0.0.1:3000,http://127.0.0.1:5173}")
    private List<String> originesAutorisees;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                // ============ ENDPOINTS PUBLICS ============
                .requestMatchers("/api/auth/**", "/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/services/**", "/api/planning", "/api/planning/**", "/api/site-content").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/theme").permitAll()

                // ============ SITE PUBLIC ET CABINET (permissions réglables dans « Rôles et permissions ») ============
                .requestMatchers("/api/site-content", "/api/site-content/**").access(permissions.exige(Permissions.SITE_MODIFIER))
                .requestMatchers("/api/theme", "/api/theme/**").access(permissions.exige(Permissions.SITE_MODIFIER))
                .requestMatchers(HttpMethod.POST, "/api/services/**").access(permissions.exige(Permissions.SERVICES_GERER))
                .requestMatchers(HttpMethod.PUT, "/api/services/**").access(permissions.exige(Permissions.SERVICES_GERER))
                .requestMatchers(HttpMethod.DELETE, "/api/services/**").access(permissions.exige(Permissions.SERVICES_GERER))
                .requestMatchers("/api/admin/planning", "/api/admin/jours-fermes", "/api/admin/jours-fermes/**", "/api/admin/rappels", "/api/admin/rappels/**").access(permissions.exige(Permissions.PLANNING_GERER))
                .requestMatchers("/api/admin/occupation", "/api/admin/occupation/**").access(permissions.exige(Permissions.OCCUPATION_VOIR))
                .requestMatchers("/api/admin/journal", "/api/admin/journal/**").access(permissions.exige(Permissions.JOURNAL_VOIR))

                // ============ ADMINISTRATION (verrouillée : super administrateur) ============
                .requestMatchers("/api/admin/users", "/api/admin/users/**").access(permissions.exige(Permissions.COMPTES_GERER))
                .requestMatchers("/api/admin/permissions", "/api/admin/permissions/**").access(permissions.exige(Permissions.PERMISSIONS_GERER))
                .requestMatchers("/api/admin/sauvegardes", "/api/admin/sauvegardes/**").access(permissions.exige(Permissions.SAUVEGARDES_GERER))
                .requestMatchers("/api/admin/**").hasRole("SUPER_ADMIN")

                // ============ PERSONNEL CONNECTÉ ============
                .requestMatchers("/api/messagerie/**").hasAnyRole("SUPER_ADMIN", "MEDECIN", "SAGE_FEMME", "SECRETAIRE", "INFIRMIERE")
                .requestMatchers("/api/profile/**").hasAnyRole("SUPER_ADMIN", "MEDECIN", "SAGE_FEMME", "SECRETAIRE", "INFIRMIERE")
                .requestMatchers("/api/notifications", "/api/notifications/**").hasAnyRole("SUPER_ADMIN", "MEDECIN", "SAGE_FEMME", "SECRETAIRE", "INFIRMIERE")

                // ============ FICHES MÉDICALES, DOSSIERS, PATIENTS ============
                .requestMatchers(HttpMethod.GET, "/api/maladies/**").access(permissions.exige(Permissions.MALADIES_LIRE))
                .requestMatchers("/api/maladies/**").access(permissions.exige(Permissions.MALADIES_MODIFIER))
                .requestMatchers(HttpMethod.DELETE, "/api/dossiers/**").access(permissions.exige(Permissions.DOSSIERS_SUPPRIMER))
                .requestMatchers(HttpMethod.GET, "/api/dossiers/**").access(permissions.exige(Permissions.DOSSIERS_LIRE))
                .requestMatchers("/api/dossiers/**").access(permissions.exige(Permissions.DOSSIERS_MODIFIER))
                .requestMatchers("/api/stats/**").access(permissions.exige(Permissions.STATS_VOIR))
                .requestMatchers("/api/export/**").access(permissions.exige(Permissions.EXPORT_RDV))
                .requestMatchers("/api/patients/**").access(permissions.exige(Permissions.PATIENTS_RECHERCHER))

                // ============ CLIENT : PRISE DE RDV (pour le personnel, RDV_CREER est vérifiée dans le contrôleur) ============
                .requestMatchers(HttpMethod.POST, "/api/rdv", "/api/rendez-vous").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/rdv/creneaux", "/api/rdv/creneaux/**", "/api/rendez-vous/creneaux", "/api/rendez-vous/creneaux/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/rdv/telephone/**", "/api/rdv/recherche", "/api/rendez-vous/recherche").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/rdv/form-options", "/api/rendez-vous/form-options").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/rdv/*/pdf", "/api/rendez-vous/*/pdf").permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/rdv/*/annuler", "/api/rendez-vous/*/annuler").permitAll()

                // ============ RENDEZ-VOUS (personnel) ============
                .requestMatchers(HttpMethod.GET, "/api/rdv/corbeille", "/api/rendez-vous/corbeille").access(permissions.exige(Permissions.RDV_CORBEILLE))
                .requestMatchers(HttpMethod.PUT, "/api/rdv/*/restaurer", "/api/rendez-vous/*/restaurer").access(permissions.exige(Permissions.RDV_CORBEILLE))
                .requestMatchers(HttpMethod.DELETE, "/api/rdv/corbeille", "/api/rdv/corbeille/**", "/api/rendez-vous/corbeille", "/api/rendez-vous/corbeille/**").access(permissions.exige(Permissions.RDV_SUPPRIMER_DEFINITIVEMENT))
                .requestMatchers(HttpMethod.GET, "/api/rdv/statut/**", "/api/rendez-vous/statut/**").hasAnyRole("SUPER_ADMIN", "MEDECIN")
                .requestMatchers(HttpMethod.PUT, "/api/rdv/*/praticien", "/api/rendez-vous/*/praticien").access(permissions.exige(Permissions.RDV_ATTRIBUER))
                .requestMatchers(HttpMethod.PUT, "/api/rdv/*/confirmer", "/api/rendez-vous/*/confirmer").access(permissions.exige(Permissions.RDV_CONFIRMER))
                .requestMatchers(HttpMethod.PUT, "/api/rdv/*/accepter", "/api/rendez-vous/*/accepter").access(permissions.exige(Permissions.RDV_ACCEPTER))
                .requestMatchers(HttpMethod.PUT, "/api/rdv/*/terminer", "/api/rendez-vous/*/terminer").access(permissions.exige(Permissions.RDV_TERMINER))
                .requestMatchers(HttpMethod.GET, "/api/rdv", "/api/rdv/**", "/api/rendez-vous", "/api/rendez-vous/**").access(permissions.exige(Permissions.RDV_VOIR))
                .requestMatchers("/api/rdv/**", "/api/rendez-vous/**").access(permissions.exige(Permissions.RDV_MODIFIER))

                // ============ TOUT LE RESTE ============
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                        "{\"error\":\"Non authentifié\",\"message\":\"Un token JWT valide est requis\",\"status\":401}"
                    );
                })
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(originesAutorisees);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
