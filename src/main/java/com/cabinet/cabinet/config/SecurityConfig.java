package com.cabinet.cabinet.config;

import com.cabinet.cabinet.security.CustomUserDetailsService;
import com.cabinet.cabinet.security.JwtAuthenticationFilter;
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
                .requestMatchers("/api/site-content", "/api/site-content/**").hasRole("SUPER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/services/**").hasRole("SUPER_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/services/**").hasRole("SUPER_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/services/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/api/messagerie/**").hasAnyRole("SUPER_ADMIN", "MEDECIN", "SAGE_FEMME", "SECRETAIRE", "INFIRMIERE")
                .requestMatchers("/api/profile/**").hasAnyRole("SUPER_ADMIN", "MEDECIN", "SAGE_FEMME", "SECRETAIRE", "INFIRMIERE")
                // Fiches médicales : lecture réservée au personnel soignant, écriture au médecin / super admin.
                .requestMatchers(HttpMethod.GET, "/api/maladies/**").hasAnyRole("SUPER_ADMIN", "MEDECIN", "SAGE_FEMME", "INFIRMIERE")
                .requestMatchers("/api/maladies/**").hasAnyRole("SUPER_ADMIN", "MEDECIN")
                .requestMatchers("/api/admin/**").hasRole("SUPER_ADMIN")
                // Dossiers médicaux : jamais accessibles à la secrétaire ; suppression d'un compte rendu réservée au médecin.
                .requestMatchers(HttpMethod.DELETE, "/api/dossiers/**").hasAnyRole("SUPER_ADMIN", "MEDECIN")
                .requestMatchers("/api/dossiers/**").hasAnyRole("SUPER_ADMIN", "MEDECIN", "SAGE_FEMME", "INFIRMIERE")
                .requestMatchers("/api/stats/**").hasAnyRole("SUPER_ADMIN", "MEDECIN", "SECRETAIRE")
                .requestMatchers("/api/export/**").hasAnyRole("SUPER_ADMIN", "SECRETAIRE", "MEDECIN", "SAGE_FEMME", "INFIRMIERE")
                .requestMatchers("/api/patients/**").hasAnyRole("SUPER_ADMIN", "SECRETAIRE", "MEDECIN", "SAGE_FEMME", "INFIRMIERE")
                .requestMatchers(HttpMethod.GET, "/api/theme").permitAll()
                .requestMatchers("/api/theme", "/api/theme/**").hasRole("SUPER_ADMIN")

                // Client : prise de RDV
                .requestMatchers(HttpMethod.POST, "/api/rdv", "/api/rendez-vous").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/rdv/creneaux", "/api/rdv/creneaux/**", "/api/rendez-vous/creneaux", "/api/rendez-vous/creneaux/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/rdv/telephone/**", "/api/rdv/recherche", "/api/rendez-vous/recherche").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/rdv/form-options", "/api/rendez-vous/form-options").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/rdv/*/pdf", "/api/rendez-vous/*/pdf").permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/rdv/*/annuler", "/api/rendez-vous/*/annuler").permitAll()

                .requestMatchers(HttpMethod.GET, "/api/rdv/corbeille", "/api/rendez-vous/corbeille").hasAnyRole("SUPER_ADMIN", "SECRETAIRE")
                .requestMatchers(HttpMethod.PUT, "/api/rdv/*/restaurer", "/api/rendez-vous/*/restaurer").hasAnyRole("SUPER_ADMIN", "SECRETAIRE")
                .requestMatchers(HttpMethod.DELETE, "/api/rdv/corbeille", "/api/rdv/corbeille/**", "/api/rendez-vous/corbeille", "/api/rendez-vous/corbeille/**").hasRole("SUPER_ADMIN")

                // ============ DASHBOARD MÉDECIN (protégé) ============
                .requestMatchers(HttpMethod.GET, "/api/rdv", "/api/rendez-vous").hasAnyRole("SUPER_ADMIN", "MEDECIN", "SAGE_FEMME", "SECRETAIRE", "INFIRMIERE")
                .requestMatchers(HttpMethod.GET, "/api/rdv/statut/**", "/api/rendez-vous/statut/**").hasAnyRole("SUPER_ADMIN", "MEDECIN")
                .requestMatchers(HttpMethod.PUT, "/api/rdv/*/praticien", "/api/rendez-vous/*/praticien").hasRole("SUPER_ADMIN")
                .requestMatchers("/api/notifications", "/api/notifications/**").hasAnyRole("SUPER_ADMIN", "MEDECIN", "SAGE_FEMME", "SECRETAIRE", "INFIRMIERE")
                .requestMatchers(HttpMethod.PUT, "/api/rdv/*/confirmer", "/api/rendez-vous/*/confirmer").hasRole("SUPER_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/rdv/*/accepter", "/api/rendez-vous/*/accepter").hasAnyRole("MEDECIN", "SAGE_FEMME", "INFIRMIERE")
                .requestMatchers(HttpMethod.PUT, "/api/rdv/*/terminer", "/api/rendez-vous/*/terminer").hasAnyRole("SUPER_ADMIN", "MEDECIN", "SAGE_FEMME", "INFIRMIERE")
                .requestMatchers("/api/rdv/**", "/api/rendez-vous/**").hasAnyRole("SUPER_ADMIN", "MEDECIN", "SAGE_FEMME", "SECRETAIRE", "INFIRMIERE")

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
