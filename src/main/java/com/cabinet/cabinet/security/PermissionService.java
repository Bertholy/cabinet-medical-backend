package com.cabinet.cabinet.security;

import com.cabinet.cabinet.entity.PermissionRole;
import com.cabinet.cabinet.entity.PermissionUtilisateur;
import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.enums.Role;
import com.cabinet.cabinet.repository.PermissionRoleRepository;
import com.cabinet.cabinet.repository.PermissionUtilisateurRepository;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import com.cabinet.cabinet.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Qui a le droit de faire quoi. Les permissions d'une personne = celles de son rôle (modifiables par le super administrateur),
 * plus ou moins ses exceptions personnelles. Le super administrateur a toujours tout. Le résultat est gardé 30 secondes en mémoire
 * (et vidé dès qu'un droit est modifié), donc un changement s'applique tout de suite.
 */
@Service
@RequiredArgsConstructor
public class PermissionService implements ApplicationRunner {
    private static final long DUREE_CACHE_MS = 30_000;
    /** Rôles dont les droits se règlent (le super administrateur n'en fait pas partie : il a tout). */
    public static final List<Role> ROLES_REGLABLES = List.of(Role.MEDECIN, Role.SAGE_FEMME, Role.INFIRMIERE, Role.SECRETAIRE);

    private final PermissionRoleRepository roles;
    private final PermissionUtilisateurRepository surcharges;
    private final UtilisateurRepository utilisateurs;
    private final AuditService audit;
    private final Map<String, Entree> cache = new ConcurrentHashMap<>();

    private record Entree(Set<String> codes, long expireA) { }

    // ======================================================================== démarrage

    @Override
    public void run(ApplicationArguments args) {
        amorcer();
    }

    /** Crée les droits d'origine de chaque rôle (sans toucher à ceux déjà réglés) ; les nouvelles permissions reçoivent leur valeur par défaut. */
    @Transactional
    public void amorcer() {
        for (Role role : ROLES_REGLABLES) {
            for (Permissions.Definition d : Permissions.CATALOGUE) {
                if (roles.findByRoleAndCode(role, d.code()).isEmpty()) {
                    PermissionRole ligne = new PermissionRole();
                    ligne.setRole(role);
                    ligne.setCode(d.code());
                    ligne.setAccorde(d.parDefaut().contains(role));
                    roles.save(ligne);
                }
            }
        }
        invalider();
    }

    // ======================================================================== vérification

    public static Set<String> toutes() {
        return Permissions.CATALOGUE.stream().map(Permissions.Definition::code).collect(Collectors.toCollection(TreeSet::new));
    }

    /** Permissions effectives d'une personne. */
    @Transactional(readOnly = true)
    public Set<String> de(Utilisateur u) {
        if (u.getRole() == Role.SUPER_ADMIN) return toutes();
        Set<String> codes = new TreeSet<>();
        for (PermissionRole p : roles.findByRole(u.getRole())) if (p.isAccorde() && Permissions.existe(p.getCode())) codes.add(p.getCode());
        for (PermissionUtilisateur s : surcharges.findDeUtilisateur(u.getId())) {
            if (!Permissions.existe(s.getCode())) continue;
            if (s.isAccorde()) codes.add(s.getCode()); else codes.remove(s.getCode());
        }
        // une permission verrouillée n'est jamais accordée en dehors du super administrateur
        codes.removeIf(c -> Permissions.trouver(c).map(Permissions.Definition::verrouille).orElse(true));
        return codes;
    }

    /** Permissions de la personne connectée (gardées 30 s en mémoire). */
    public Set<String> de(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) return Set.of();
        String role = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).filter(a -> a.startsWith("ROLE_")).findFirst().orElse("");
        String cle = authentication.getName().toLowerCase() + "|" + role;
        Entree entree = cache.get(cle);
        long maintenant = System.currentTimeMillis();
        if (entree != null && entree.expireA() > maintenant) return entree.codes();
        Set<String> codes = utilisateurs.findByEmailIgnoreCase(authentication.getName()).map(this::de).orElse(Set.of());
        cache.put(cle, new Entree(codes, maintenant + DUREE_CACHE_MS));
        return codes;
    }

    public boolean autorise(Authentication authentication, String code) {
        return de(authentication).contains(code);
    }

    /** Gestionnaire d'accès à brancher dans SecurityConfig : « il faut la permission X ». Sans connexion : 401, sans droit : 403. */
    public AuthorizationManager<RequestAuthorizationContext> exige(String code) {
        return (authentication, contexte) -> new AuthorizationDecision(autorise(authentication.get(), code));
    }

    public void invalider() { cache.clear(); }

    // ======================================================================== réglages (super administrateur)

    @Transactional
    public void definirRole(Role role, Set<String> demandees) {
        verifierRole(role);
        for (String code : demandees) {
            Permissions.Definition d = Permissions.trouver(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Permission inconnue : " + code));
            if (d.verrouille()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "« " + d.libelle() + " » est réservée au super administrateur.");
        }
        List<String> ajouts = new java.util.ArrayList<>(), retraits = new java.util.ArrayList<>();
        for (Permissions.Definition d : Permissions.CATALOGUE) {
            boolean voulue = demandees.contains(d.code()) && !d.verrouille();
            PermissionRole ligne = roles.findByRoleAndCode(role, d.code()).orElseGet(() -> { PermissionRole n = new PermissionRole(); n.setRole(role); n.setCode(d.code()); return n; });
            if (ligne.getId() == null || ligne.isAccorde() != voulue) {
                if (ligne.getId() != null) (voulue ? ajouts : retraits).add(d.code());
                ligne.setAccorde(voulue);
                roles.save(ligne);
            }
        }
        invalider();
        audit.log("PERMISSIONS_ROLE", "Role", role.name(), "Rôle " + role + " — ajoutées : " + (ajouts.isEmpty() ? "aucune" : ajouts) + " — retirées : " + (retraits.isEmpty() ? "aucune" : retraits));
    }

    @Transactional
    public void retablirRole(Role role) {
        verifierRole(role);
        for (Permissions.Definition d : Permissions.CATALOGUE) {
            PermissionRole ligne = roles.findByRoleAndCode(role, d.code()).orElseGet(() -> { PermissionRole n = new PermissionRole(); n.setRole(role); n.setCode(d.code()); return n; });
            ligne.setAccorde(d.parDefaut().contains(role));
            roles.save(ligne);
        }
        invalider();
        audit.log("PERMISSIONS_ROLE", "Role", role.name(), "Rôle " + role + " — droits d'origine rétablis");
    }

    /** accorde : true = en plus du rôle, false = retiré malgré le rôle, null = selon le rôle (exception supprimée). */
    @Transactional
    public void definirException(Long utilisateurId, String code, Boolean accorde) {
        Utilisateur u = utilisateurs.findById(utilisateurId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable."));
        if (u.getRole() == Role.SUPER_ADMIN) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le super administrateur a toujours tous les droits.");
        Permissions.Definition d = Permissions.trouver(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Permission inconnue : " + code));
        if (d.verrouille() && Boolean.TRUE.equals(accorde)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "« " + d.libelle() + " » est réservée au super administrateur.");
        var existante = surcharges.findDeUtilisateurEtCode(utilisateurId, code);
        if (accorde == null) existante.ifPresent(surcharges::delete);
        else {
            PermissionUtilisateur ligne = existante.orElseGet(() -> { PermissionUtilisateur n = new PermissionUtilisateur(); n.setUtilisateur(u); n.setCode(code); return n; });
            ligne.setAccorde(accorde);
            surcharges.save(ligne);
        }
        invalider();
        audit.log("PERMISSIONS_PERSONNE", "Utilisateur", utilisateurId, u.getEmail() + " — " + code + " : " + (accorde == null ? "selon le rôle" : accorde ? "accordée" : "retirée"));
    }

    @Transactional
    public void retablirUtilisateur(Long utilisateurId) {
        Utilisateur u = utilisateurs.findById(utilisateurId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable."));
        surcharges.supprimerDe(utilisateurId);
        invalider();
        audit.log("PERMISSIONS_PERSONNE", "Utilisateur", utilisateurId, u.getEmail() + " — exceptions supprimées (droits du rôle)");
    }

    private static void verifierRole(Role role) {
        if (!ROLES_REGLABLES.contains(role)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Les droits de ce rôle ne se règlent pas (le super administrateur a toujours tous les droits).");
    }

    // ======================================================================== vue pour l'écran d'administration

    @Transactional(readOnly = true)
    public Map<String, Object> vue() {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("catalogue", Permissions.CATALOGUE.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", d.code()); m.put("groupe", d.groupe()); m.put("libelle", d.libelle()); m.put("description", d.description()); m.put("verrouille", d.verrouille());
            return m;
        }).toList());
        List<Map<String, Object>> parRole = new java.util.ArrayList<>();
        for (Role role : ROLES_REGLABLES) {
            Set<String> accordees = roles.findByRole(role).stream().filter(PermissionRole::isAccorde).map(PermissionRole::getCode).collect(Collectors.toCollection(TreeSet::new));
            Set<String> defaut = Permissions.CATALOGUE.stream().filter(d -> d.parDefaut().contains(role)).map(Permissions.Definition::code).collect(Collectors.toCollection(TreeSet::new));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", role.name()); m.put("permissions", accordees); m.put("modifie", !accordees.equals(defaut));
            m.put("personnes", utilisateurs.countByRole(role));
            parRole.add(m);
        }
        corps.put("roles", parRole);
        List<Map<String, Object>> personnes = new java.util.ArrayList<>();
        for (Utilisateur u : utilisateurs.findAll().stream().sorted(Comparator.comparing(x -> x.getNom() == null ? "" : x.getNom().toLowerCase())).toList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            Map<String, Boolean> exceptions = new LinkedHashMap<>();
            surcharges.findDeUtilisateur(u.getId()).forEach(s -> exceptions.put(s.getCode(), s.isAccorde()));
            m.put("id", u.getId()); m.put("nom", u.getNom()); m.put("email", u.getEmail()); m.put("role", u.getRole().name());
            m.put("exceptions", exceptions); m.put("effectives", de(u));
            personnes.add(m);
        }
        corps.put("personnes", personnes);
        return corps;
    }
}
