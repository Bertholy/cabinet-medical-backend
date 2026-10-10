package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.entity.Maladie;
import com.cabinet.cabinet.entity.Traitement;
import com.cabinet.cabinet.repository.MaladieRepository;
import com.cabinet.cabinet.repository.TraitementRepository;
import com.cabinet.cabinet.service.AuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Fiches maladies / traitements. Accès contrôlé dans SecurityConfig :
 * lecture pour le personnel soignant, création / modification / suppression pour le médecin et le super administrateur.
 * Ces fiches alimentent l'assistant d'ordonnance : c'est le médecin qui décide des protocoles.
 */
@RestController
@RequestMapping("/api/maladies")
@RequiredArgsConstructor
public class MaladieController {

    private final MaladieRepository maladieRepository;
    private final TraitementRepository traitementRepository;
    private final AuditService audit;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<Maladie>> getAll() {
        List<Maladie> maladies = maladieRepository.findAll();
        maladies.forEach(maladie -> maladie.getTraitements().size()); // charge les traitements avant la sérialisation
        return ResponseEntity.ok(maladies);
    }

    // Détails d'une maladie (avec ses traitements)
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<Maladie> getById(@PathVariable Long id) {
        return maladieRepository.findById(id)
                .map(maladie -> {
                    maladie.getTraitements().size();
                    return ResponseEntity.ok(maladie);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Maladie> create(@Valid @RequestBody Maladie maladie) {
        maladie.setId(null);
        // Le corps JSON peut omettre « traitements » : le constructeur utilisé par Jackson laisse alors la liste à null.
        if (maladie.getTraitements() == null) maladie.setTraitements(new java.util.ArrayList<>());
        // Chaque traitement doit pointer vers sa maladie, sinon l'enregistrement échoue (maladie_id obligatoire).
        maladie.getTraitements().forEach(traitement -> {
            traitement.setId(null);
            traitement.setMaladie(maladie);
        });
        Maladie saved = maladieRepository.save(maladie);
        audit.log("MALADIE_CREEE", "Maladie", saved.getId(), saved.getNom());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public Maladie update(@PathVariable Long id, @Valid @RequestBody Maladie request) {
        Maladie maladie = trouver(id);
        maladie.setNom(request.getNom());
        maladie.setDescription(request.getDescription());
        maladie.setSymptomes(request.getSymptomes());
        audit.log("MALADIE_MODIFIEE", "Maladie", id, maladie.getNom());
        maladie.getTraitements().size();
        return maladie;
    }

    @PostMapping("/{id}/traitements")
    @Transactional
    public ResponseEntity<Traitement> ajouterTraitement(@PathVariable Long id, @Valid @RequestBody Traitement traitement) {
        Maladie maladie = trouver(id);
        traitement.setId(null);
        traitement.setMaladie(maladie);
        // Enregistré directement : passer par maladieRepository.save(maladie) renverrait une copie et laisserait l'id de cet objet à null.
        Traitement saved = traitementRepository.saveAndFlush(traitement);
        maladie.getTraitements().add(saved);
        audit.log("TRAITEMENT_AJOUTE", "Maladie", id, maladie.getNom() + " — " + saved.getMedicament());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}/traitements/{traitementId}")
    @Transactional
    public Traitement modifierTraitement(@PathVariable Long id, @PathVariable Long traitementId, @Valid @RequestBody Traitement request) {
        Traitement traitement = trouverTraitement(trouver(id), traitementId);
        traitement.setMedicament(request.getMedicament());
        traitement.setPosologie(request.getPosologie());
        traitement.setDuree(request.getDuree());
        traitement.setRemarques(request.getRemarques());
        audit.log("TRAITEMENT_MODIFIE", "Maladie", id, traitement.getMedicament());
        return traitement;
    }

    @DeleteMapping("/{id}/traitements/{traitementId}")
    @Transactional
    public ResponseEntity<Void> supprimerTraitement(@PathVariable Long id, @PathVariable Long traitementId) {
        Maladie maladie = trouver(id);
        Traitement traitement = trouverTraitement(maladie, traitementId);
        maladie.getTraitements().remove(traitement); // orphanRemoval supprime la ligne
        audit.log("TRAITEMENT_SUPPRIME", "Maladie", id, traitement.getMedicament());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Maladie maladie = maladieRepository.findById(id).orElse(null);
        if (maladie == null) return ResponseEntity.notFound().build();
        maladieRepository.delete(maladie);
        audit.log("MALADIE_SUPPRIMEE", "Maladie", id, maladie.getNom());
        return ResponseEntity.noContent().build();
    }

    private Maladie trouver(Long id) {
        return maladieRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Maladie introuvable."));
    }

    private Traitement trouverTraitement(Maladie maladie, Long traitementId) {
        return maladie.getTraitements().stream().filter(t -> t.getId().equals(traitementId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Traitement introuvable."));
    }
}
