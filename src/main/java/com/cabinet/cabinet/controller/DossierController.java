package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.dto.RendezVousResponseDTO;
import com.cabinet.cabinet.entity.Consultation;
import com.cabinet.cabinet.entity.DossierPatient;
import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.repository.ConsultationRepository;
import com.cabinet.cabinet.repository.DossierPatientRepository;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import com.cabinet.cabinet.service.AuditService;
import com.cabinet.cabinet.service.RendezVousService;
import com.cabinet.cabinet.util.PhoneUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dossier patient : informations médicales durables + comptes rendus de consultation.
 * Accès limité au personnel soignant (SecurityConfig : /api/dossiers/**) ; la secrétaire n'y a pas accès.
 */
@SuppressWarnings("null") // avertissements de l'éditeur sur des références de méthode : aucun risque réel
@RestController
@RequestMapping("/api/dossiers")
@RequiredArgsConstructor
public class DossierController {
    private static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");

    private final DossierPatientRepository dossiers;
    private final ConsultationRepository consultations;
    private final UtilisateurRepository utilisateurs;
    private final RendezVousService rendezVousService;
    private final AuditService audit;

    public record DossierRequest(@Size(max = 100) String nom, @Size(max = 1000) String allergies,
                                 @Size(max = 2000) String antecedents, @Size(max = 2000) String notes) { }

    public record ConsultationRequest(Long rendezVousId, LocalDate dateConsultation,
                                      @Size(max = 4000, message = "Les notes ne peuvent pas dépasser 4000 caractères") String notes,
                                      @Size(max = 500, message = "Le diagnostic ne peut pas dépasser 500 caractères") String diagnostic,
                                      @Size(max = 4000, message = "L'ordonnance ne peut pas dépasser 4000 caractères") String ordonnance) { }

    @GetMapping("/{telephone}")
    @Transactional(readOnly = true)
    public Map<String, Object> lire(@PathVariable String telephone) {
        String tel = canonique(telephone);
        List<RendezVousResponseDTO> rdvs = rendezVousService.getRendezVousPatient(tel);
        DossierPatient dossier = dossiers.findByTelephone(tel).orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("telephone", tel);
        body.put("nom", dossier != null && dossier.getNom() != null ? dossier.getNom() : rdvs.stream().findFirst().map(RendezVousResponseDTO::getNomClient).orElse(null));
        body.put("allergies", dossier == null ? null : dossier.getAllergies());
        body.put("antecedents", dossier == null ? null : dossier.getAntecedents());
        body.put("notes", dossier == null ? null : dossier.getNotes());
        body.put("modifieLe", dossier == null ? null : dossier.getModifieLe());
        body.put("modifiePar", dossier == null ? null : dossier.getModifiePar());
        body.put("ageDernierRdv", rdvs.stream().findFirst().map(RendezVousResponseDTO::getAgeClient).orElse(null));
        body.put("rendezVous", rdvs);
        body.put("consultations", consultations.findByTelephoneOrderByDateConsultationDescIdDesc(tel));
        return body;
    }

    @PutMapping("/{telephone}")
    @Transactional
    public Map<String, Object> modifier(@PathVariable String telephone, @Valid @RequestBody DossierRequest request, Authentication authentication) {
        String tel = canonique(telephone);
        DossierPatient dossier = dossiers.findByTelephone(tel).orElseGet(() -> {
            DossierPatient neuf = new DossierPatient();
            neuf.setTelephone(tel);
            return neuf;
        });
        dossier.setNom(vide(request.nom()));
        dossier.setAllergies(vide(request.allergies()));
        dossier.setAntecedents(vide(request.antecedents()));
        dossier.setNotes(vide(request.notes()));
        dossier.setModifieLe(LocalDateTime.now(ZONE));
        dossier.setModifiePar(authentication.getName());
        dossiers.save(dossier);
        audit.log("DOSSIER_MODIFIE", "DossierPatient", tel, "Dossier du patient " + tel);
        return lire(tel);
    }

    @PostMapping("/{telephone}/consultations")
    @Transactional
    public ResponseEntity<Consultation> creerConsultation(@PathVariable String telephone, @Valid @RequestBody ConsultationRequest request,
                                                          Authentication authentication) {
        String tel = canonique(telephone);
        if (request.rendezVousId() != null) {
            boolean appartient = rendezVousService.getRendezVousPatient(tel).stream().anyMatch(r -> r.getId().equals(request.rendezVousId()));
            if (!appartient) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce rendez-vous n'appartient pas à ce patient.");
        }
        // Un seul compte rendu par rendez-vous : un nouvel envoi complète le précédent.
        Consultation consultation = request.rendezVousId() == null ? new Consultation()
                : consultations.findFirstByRendezVousId(request.rendezVousId()).orElseGet(Consultation::new);
        boolean nouvelle = consultation.getId() == null;
        consultation.setTelephone(tel);
        consultation.setRendezVousId(request.rendezVousId());
        consultation.setDateConsultation(request.dateConsultation() != null ? request.dateConsultation() : LocalDate.now(ZONE));
        // Un nouvel envoi pour le même rendez-vous complète le compte rendu existant sans effacer ce qui n'est pas renvoyé.
        ConsultationRequest effective = nouvelle ? request : new ConsultationRequest(request.rendezVousId(), request.dateConsultation(),
                request.notes() == null || request.notes().isBlank() ? consultation.getNotes() : request.notes(),
                request.diagnostic() == null || request.diagnostic().isBlank() ? consultation.getDiagnostic() : request.diagnostic(),
                request.ordonnance() == null || request.ordonnance().isBlank() ? consultation.getOrdonnance() : request.ordonnance());
        remplir(consultation, effective, authentication, nouvelle);
        Consultation saved = consultations.save(consultation);
        audit.log(nouvelle ? "CONSULTATION_CREEE" : "CONSULTATION_MODIFIEE", "Consultation", saved.getId(), "Patient " + tel);
        return ResponseEntity.status(nouvelle ? HttpStatus.CREATED : HttpStatus.OK).body(saved);
    }

    @PutMapping("/consultations/{id}")
    @Transactional
    public Consultation modifierConsultation(@PathVariable Long id, @Valid @RequestBody ConsultationRequest request, Authentication authentication) {
        Consultation consultation = consultations.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte rendu introuvable."));
        if (request.dateConsultation() != null) consultation.setDateConsultation(request.dateConsultation());
        remplir(consultation, request, authentication, false);
        Consultation saved = consultations.save(consultation);
        audit.log("CONSULTATION_MODIFIEE", "Consultation", id, "Patient " + saved.getTelephone());
        return saved;
    }

    @DeleteMapping("/consultations/{id}")
    @Transactional
    public ResponseEntity<Void> supprimerConsultation(@PathVariable Long id) {
        Consultation consultation = consultations.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte rendu introuvable."));
        consultations.delete(consultation);
        audit.log("CONSULTATION_SUPPRIMEE", "Consultation", id, "Patient " + consultation.getTelephone());
        return ResponseEntity.noContent().build();
    }

    private void remplir(Consultation consultation, ConsultationRequest request, Authentication authentication, boolean nouvelle) {
        consultation.setNotes(vide(request.notes()));
        consultation.setDiagnostic(vide(request.diagnostic()));
        consultation.setOrdonnance(vide(request.ordonnance()));
        Utilisateur auteur = utilisateurs.findByEmail(authentication.getName()).orElse(null);
        consultation.setAuteurEmail(authentication.getName());
        consultation.setAuteurNom(auteur == null ? authentication.getName() : auteur.getNom());
        LocalDateTime maintenant = LocalDateTime.now(ZONE);
        if (nouvelle) consultation.setCreeLe(maintenant);
        consultation.setModifieLe(maintenant);
    }

    private String canonique(String telephone) {
        String tel = PhoneUtils.canonical(telephone);
        if (tel == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Numéro de téléphone invalide.");
        return tel;
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }
}
