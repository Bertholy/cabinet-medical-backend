package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.dto.RendezVousResponseDTO;
import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import com.cabinet.cabinet.service.AuditService;
import com.cabinet.cabinet.service.RendezVousListePdfService;
import com.cabinet.cabinet.service.RendezVousService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Export de la liste des rendez-vous d'une période (CSV lisible par Excel, ou PDF).
 * Les médecins et sages-femmes n'exportent que leurs rendez-vous et ceux pas encore attribués, comme sur leur tableau de bord.
 */
@SuppressWarnings("null") // avertissements de l'éditeur sur des références de méthode : aucun risque réel
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {
    private static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");
    private static final Map<String, String> STATUTS = Map.of("EN_ATTENTE", "En cours", "CONFIRME", "Confirmé", "TERMINE", "Terminé", "ANNULE", "Annulé");
    private static final int MAX_JOURS = 400;
    private static final int MAX_LIGNES = 5000;

    private final RendezVousService rendezVousService;
    private final RendezVousListePdfService pdfService;
    private final UtilisateurRepository utilisateurs;
    private final AuditService audit;

    @GetMapping("/rendez-vous")
    public ResponseEntity<byte[]> exporter(@RequestParam(required = false) LocalDate debut, @RequestParam(required = false) LocalDate fin,
                                           @RequestParam(defaultValue = "csv") String format, Authentication authentication) throws IOException {
        LocalDate aujourdHui = LocalDate.now(ZONE);
        LocalDate d = debut != null ? debut : aujourdHui.withDayOfMonth(1);
        LocalDate f = fin != null ? fin : aujourdHui.withDayOfMonth(aujourdHui.lengthOfMonth());
        if (f.isBefore(d)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de fin doit être après la date de début.");
        if (ChronoUnit.DAYS.between(d, f) > MAX_JOURS) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La période ne peut pas dépasser " + MAX_JOURS + " jours.");

        List<RendezVousResponseDTO> lignes = rendezVousService.getRendezVousPeriode(d, f, praticienFiltre(authentication));
        if (lignes.size() > MAX_LIGNES) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trop de rendez-vous (" + lignes.size() + "). Réduisez la période.");

        String nom = "rendez-vous-" + d + "_" + f;
        audit.log("EXPORT", "RendezVous", null, format.toUpperCase() + " du " + d + " au " + f + " (" + lignes.size() + " lignes)");
        if ("pdf".equalsIgnoreCase(format)) {
            return fichier(pdfService.generer(lignes, d, f), MediaType.APPLICATION_PDF, nom + ".pdf");
        }
        if (!"csv".equalsIgnoreCase(format)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Format inconnu : utilisez csv ou pdf.");
        return fichier(csv(lignes).getBytes(StandardCharsets.UTF_8), MediaType.parseMediaType("text/csv;charset=UTF-8"), nom + ".csv");
    }

    private ResponseEntity<byte[]> fichier(byte[] contenu, MediaType type, String nom) {
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nom, StandardCharsets.UTF_8).build().toString())
                .body(contenu);
    }

    /** CSV avec BOM UTF-8 et séparateur « ; » : s'ouvre directement avec les accents corrects dans Excel (version française). */
    private String csv(List<RendezVousResponseDTO> lignes) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("N°;Date;Heure;Patient;Âge;Téléphone;Service;Praticien;Statut;Motif\r\n");
        for (RendezVousResponseDTO r : lignes) {
            sb.append(cellule(String.valueOf(r.getId()))).append(';')
                    .append(cellule(String.valueOf(r.getDateRdv()))).append(';')
                    .append(cellule(String.valueOf(r.getHeureRdv()).substring(0, 5))).append(';')
                    .append(cellule(r.getNomClient())).append(';')
                    .append(cellule(String.valueOf(r.getAgeClient()))).append(';')
                    .append(cellule(r.getTelephoneClient())).append(';')
                    .append(cellule(r.getServiceNom())).append(';')
                    .append(cellule(r.getPraticienNom())).append(';')
                    .append(cellule(STATUTS.getOrDefault(String.valueOf(r.getStatut()), String.valueOf(r.getStatut())))).append(';')
                    .append(cellule(r.getMotif())).append("\r\n");
        }
        return sb.toString();
    }

    /** Entoure de guillemets et neutralise les « formules » (=, +, -, @) qu'Excel exécuterait à l'ouverture. */
    static String cellule(String valeur) {
        String v = valeur == null ? "" : valeur;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v.replace("\"", "\"\"").replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ') + "\"";
    }

    private Long praticienFiltre(Authentication authentication) {
        Set<String> roles = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toSet());
        if (roles.contains("ROLE_SUPER_ADMIN") || roles.contains("ROLE_SECRETAIRE")) return null;
        return utilisateurs.findByEmail(authentication.getName()).map(Utilisateur::getId)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Compte interne introuvable."));
    }
}
