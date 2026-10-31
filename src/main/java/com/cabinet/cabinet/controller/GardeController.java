package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.dto.GardeDTO;
import com.cabinet.cabinet.dto.GardeRequestDTO;
import com.cabinet.cabinet.dto.TourGardeRequestDTO;
import com.cabinet.cabinet.service.GardeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Tour de garde du personnel. Le voir : permission GARDE_VOIR (tout le personnel) ; le modifier : GARDE_GERER (administration),
 * deux règles posées dans SecurityConfig.
 */
@RestController
@RequestMapping("/api/gardes")
@RequiredArgsConstructor
public class GardeController {
    private final GardeService gardes;

    /** Les gardes d'une période (par défaut, les 7 prochains jours). */
    @GetMapping
    public List<GardeDTO> lister(@RequestParam(required = false) LocalDate du, @RequestParam(required = false) LocalDate au) {
        LocalDate debut = du != null ? du : GardeService.aujourdhui();
        return gardes.lister(debut, au != null ? au : debut.plusDays(6));
    }

    /** Qui est de garde aujourd'hui (jour et nuit), et « moi » : les périodes où la personne connectée est de garde (vide si elle ne l'est pas). */
    @GetMapping("/aujourdhui")
    public Map<String, Object> aujourdhui(Authentication authentication) {
        LocalDate jour = GardeService.aujourdhui();
        return Map.of("date", jour, "gardes", gardes.lister(jour, jour), "moi", gardes.mesPeriodes(authentication.getName(), jour));
    }

    /** Le personnel qu'on peut mettre de garde (réservé à celui qui gère le tour de garde). */
    @GetMapping("/personnel")
    public List<Map<String, Object>> personnel() { return gardes.personnel(); }

    @PostMapping
    public ResponseEntity<GardeDTO> ajouter(@Valid @RequestBody GardeRequestDTO dto, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gardes.ajouter(dto, authentication.getName()));
    }

    /** Remise à zéro du tour de garde : toutes les gardes et le tour de référence sont effacés. */
    @DeleteMapping
    public Map<String, Object> reinitialiser() { return gardes.reinitialiser(); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        gardes.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    /** Le tour de référence proposé par l'administration (null s'il n'y en a pas encore). */
    @GetMapping("/modele")
    public Map<String, Object> modele() { return gardes.modele(); }

    /** Génération automatique : suit le tour proposé par l'administration (ou, à défaut, la rotation de toute l'équipe) sur la période demandée. */
    @PostMapping("/automatique")
    public Map<String, Object> automatique(@Valid @RequestBody com.cabinet.cabinet.dto.RestaurationGardeRequestDTO dto, Authentication authentication) {
        return gardes.genererAutomatiquement(dto, authentication.getName());
    }

    @PostMapping("/tour")
    public Map<String, Object> tour(@Valid @RequestBody TourGardeRequestDTO dto, Authentication authentication) {
        return gardes.genererTour(dto, authentication.getName());
    }
}
