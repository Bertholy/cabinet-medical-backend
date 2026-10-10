package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.service.SauvegardeService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sauvegardes de la base : réservé au super administrateur (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/sauvegardes")
@RequiredArgsConstructor
public class AdminSauvegardeController {
    private final SauvegardeService sauvegardes;

    @GetMapping
    public Map<String, Object> etat() {
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("automatique", sauvegardes.isAutomatique());
        reponse.put("conservees", sauvegardes.getGarder());
        reponse.put("dossier", sauvegardes.getDossier().toString());
        reponse.put("pgDumpDisponible", sauvegardes.pgDumpDisponible());
        reponse.put("enRetard", sauvegardes.enRetard());
        reponse.put("fichiers", sauvegardes.lister());
        return reponse;
    }

    @PostMapping
    public List<SauvegardeService.Fichier> sauvegarderMaintenant() {
        try {
            return sauvegardes.sauvegarder("manuel");
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/{nom}")
    public ResponseEntity<Resource> telecharger(@PathVariable String nom) {
        if (!SauvegardeService.NOM_VALIDE.matcher(nom).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nom de fichier invalide.");
        Path chemin = sauvegardes.cheminDe(nom);
        if (chemin == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sauvegarde introuvable.");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nom + "\"")
                .body(new FileSystemResource(chemin));
    }
}
