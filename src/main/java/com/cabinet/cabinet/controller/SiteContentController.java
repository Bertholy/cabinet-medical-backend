package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.entity.SiteContent;
import com.cabinet.cabinet.repository.SiteContentRepository;
import com.cabinet.cabinet.service.AuditService;
import com.cabinet.cabinet.util.StyleTexte;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Éditeur du site public : textes modifiables et éléments déplaçables.
 * La lecture est publique (le site charge ces valeurs au démarrage) ; l'écriture est réservée au super administrateur.
 * Les textes sont affichés comme du texte brut par le site : aucun HTML n'est interprété.
 */
@RestController
@RequestMapping("/api/site-content")
@RequiredArgsConstructor
public class SiteContentController {
    private static final Pattern CLE = Pattern.compile("^[a-zA-Z0-9._-]{1,100}$");
    private static final int DECALAGE_MAX = 3000;
    private final SiteContentRepository repository;
    private final AuditService audit;

    /** Nombre maximal de textes ajoutés par le super administrateur (clé « ajout.… ») : évite de remplir la base par erreur. */
    private static final int AJOUTS_MAX = 200;

    /** style : absent = inchangé ; objet vide = mise en forme retirée. */
    public record Modification(@Size(max = 2000, message = "Le texte ne peut pas dépasser 2000 caractères") String texte,
                               Integer offsetX, Integer offsetY, Map<String, Object> style) { }

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Map<String, Object>> lire() {
        Map<String, Map<String, Object>> body = new LinkedHashMap<>();
        for (SiteContent c : repository.findAll()) {
            Map<String, Object> valeur = new LinkedHashMap<>();
            valeur.put("texte", c.getTexte());
            valeur.put("offsetX", c.getOffsetX());
            valeur.put("offsetY", c.getOffsetY());
            valeur.put("style", StyleTexte.lire(c.getStyle()));
            body.put(c.getCle(), valeur);
        }
        return body;
    }

    /** Enregistre le texte et/ou la position d'un élément (les champs absents ne sont pas modifiés). */
    @PutMapping("/{cle}")
    @Transactional
    public Map<String, Object> modifier(@PathVariable String cle, @Valid @RequestBody Modification modification) {
        verifierCle(cle);
        SiteContent contenu = repository.findById(cle).orElseGet(() -> {
            SiteContent neuf = new SiteContent();
            neuf.setCle(cle);
            return neuf;
        });
        if (contenu.getModifieLe() == null && cle.startsWith("ajout.") && repository.count() >= AJOUTS_MAX) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Trop de textes ajoutés : supprimez-en avant d'en créer de nouveaux.");
        }
        if (modification.style() != null) contenu.setStyle(StyleTexte.valider(modification.style()));
        if (modification.texte() != null) contenu.setTexte(modification.texte().isBlank() ? null : modification.texte().strip());
        if (modification.offsetX() != null) contenu.setOffsetX(borner(modification.offsetX()));
        if (modification.offsetY() != null) contenu.setOffsetY(borner(modification.offsetY()));
        // Un décalage nul = l'élément est à sa place d'origine : on ne conserve rien.
        if (Integer.valueOf(0).equals(contenu.getOffsetX()) && Integer.valueOf(0).equals(contenu.getOffsetY())) {
            contenu.setOffsetX(null);
            contenu.setOffsetY(null);
        }
        contenu.setModifieLe(LocalDateTime.now(ZoneId.of("Indian/Antananarivo")));
        if (contenu.getTexte() == null && contenu.getOffsetX() == null && contenu.getOffsetY() == null && contenu.getStyle() == null) {
            repository.deleteById(cle);
        } else {
            repository.save(contenu);
        }
        audit.log("SITE_MODIFIE", "SiteContent", cle, modification.texte() != null ? "Texte modifié" : (modification.style() != null ? "Mise en forme modifiée" : "Position modifiée"));
        Map<String, Object> valeur = new LinkedHashMap<>();
        valeur.put("texte", contenu.getTexte());
        valeur.put("offsetX", contenu.getOffsetX());
        valeur.put("offsetY", contenu.getOffsetY());
        valeur.put("style", StyleTexte.lire(contenu.getStyle()));
        return valeur;
    }

    /** Remet un élément à son texte et à sa place d'origine. */
    @DeleteMapping("/{cle}")
    @Transactional
    public ResponseEntity<Void> reinitialiser(@PathVariable String cle) {
        verifierCle(cle);
        if (repository.existsById(cle)) {
            repository.deleteById(cle);
            audit.log("SITE_REINITIALISE", "SiteContent", cle, "Élément remis à l'origine");
        }
        return ResponseEntity.noContent().build();
    }

    /** Remet tout le site à son apparence d'origine, ou seulement les éléments dont la clé commence par « prefixe » (une page). */
    @DeleteMapping
    @Transactional
    public ResponseEntity<Void> toutReinitialiser(@RequestParam(required = false) String prefixe) {
        if (prefixe != null && !prefixe.isBlank()) {
            verifierCle(prefixe);
            repository.deleteAll(repository.findAll().stream().filter(c -> c.getCle().startsWith(prefixe)).toList());
            audit.log("SITE_REINITIALISE", "SiteContent", prefixe, "Page remise à l'origine");
        } else {
            repository.deleteAll();
            audit.log("SITE_REINITIALISE", "SiteContent", null, "Tout le site remis à l'origine");
        }
        return ResponseEntity.noContent().build();
    }

    private static void verifierCle(String cle) {
        if (!CLE.matcher(cle).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifiant d'élément invalide.");
    }

    private static int borner(int valeur) {
        return Math.max(-DECALAGE_MAX, Math.min(DECALAGE_MAX, valeur));
    }
}
