package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.enums.Role;
import com.cabinet.cabinet.security.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rôles et permissions : réservé à la permission PERMISSIONS_GERER (le super administrateur) — voir SecurityConfig. */
@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
public class AdminPermissionController {
    private final PermissionService permissions;

    public record ReglageRole(List<String> permissions) { }

    public record ReglagePersonne(String code, Boolean accorde) { }

    @GetMapping
    public Map<String, Object> lire() {
        return permissions.vue();
    }

    /** Remplace la liste des droits d'un rôle. */
    @PutMapping("/roles/{role}")
    public Map<String, Object> definirRole(@PathVariable String role, @RequestBody ReglageRole corps) {
        permissions.definirRole(role(role), new LinkedHashSet<>(corps.permissions() == null ? List.of() : corps.permissions()));
        return permissions.vue();
    }

    @PostMapping("/roles/{role}/defauts")
    public Map<String, Object> retablirRole(@PathVariable String role) {
        permissions.retablirRole(role(role));
        return permissions.vue();
    }

    /** Exception personnelle : accorde = true / false, ou null pour revenir au rôle. */
    @PutMapping("/utilisateurs/{id}")
    public Map<String, Object> definirException(@PathVariable Long id, @RequestBody ReglagePersonne corps) {
        permissions.definirException(id, corps.code(), corps.accorde());
        return permissions.vue();
    }

    @PostMapping("/utilisateurs/{id}/defauts")
    public Map<String, Object> retablirPersonne(@PathVariable Long id) {
        permissions.retablirUtilisateur(id);
        return permissions.vue();
    }

    private static Role role(String nom) {
        try { return Role.valueOf(nom); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rôle inconnu."); }
    }
}
