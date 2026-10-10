package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.dto.RendezVousRequestDTO;
import com.cabinet.cabinet.dto.RendezVousResponseDTO;
import com.cabinet.cabinet.dto.DashboardRendezVousDTO;
import com.cabinet.cabinet.dto.RendezVousCorbeilleDTO;
import com.cabinet.cabinet.dto.RendezVousCorbeilleSuperAdminDTO;
import com.cabinet.cabinet.dto.UtilisateurResumeDTO;
import com.cabinet.cabinet.entity.RendezVous;
import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.enums.Role;
import com.cabinet.cabinet.repository.ServiceRepository;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import com.cabinet.cabinet.service.RendezVousPdfService;
import com.cabinet.cabinet.enums.StatutRDV;
import com.cabinet.cabinet.service.RendezVousService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping({"/api/rdv", "/api/rendez-vous"})
@RequiredArgsConstructor
public class RendezVousController {

    private final RendezVousService rendezVousService;
    private final RendezVousPdfService pdfService;
    private final ServiceRepository serviceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final com.cabinet.cabinet.service.PlanningService planningService;

    // ============ CRÉER UN RDV (client) ============
    @PostMapping
    public ResponseEntity<RendezVousResponseDTO> creer(@Valid @RequestBody RendezVousRequestDTO dto, Authentication authentication) {
        // Le patient ne choisit jamais qui le reçoit : seule la création faite par le super administrateur peut désigner une personne.
        if (!hasRole(authentication, "SUPER_ADMIN")) dto.setPraticienId(null);
        RendezVousResponseDTO response = rendezVousService.creerRendezVous(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ============ LISTER TOUS LES RDV (médecin) ============
    @GetMapping
    public ResponseEntity<List<RendezVousResponseDTO>> getAll() {
        return ResponseEntity.ok(rendezVousService.getAllRendezVous());
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardRendezVousDTO> dashboard(
            @RequestParam(defaultValue = "aujourd_hui") String periode, Authentication authentication) {
        LocalDate[] dates = datesPeriode(periode);
        Long praticienId = praticienFiltre(authentication);
        return ResponseEntity.ok(new DashboardRendezVousDTO(
                rendezVousService.getStatistiques(dates[0], dates[1], praticienId),
                rendezVousService.getRendezVousPeriode(dates[0], dates[1], praticienId)));
    }

    // ============ CALENDRIER MENSUEL (mois = AAAA-MM) ============
    @GetMapping("/calendrier")
    public ResponseEntity<Map<String, Object>> calendrier(@RequestParam String mois, Authentication authentication) {
        java.time.YearMonth ym = java.time.YearMonth.parse(mois);
        LocalDate debut = ym.atDay(1);
        LocalDate fin = ym.atEndOfMonth();
        return ResponseEntity.ok(Map.of(
                "rendezVous", rendezVousService.getRendezVousPeriode(debut, fin, praticienFiltre(authentication)),
                "joursFermes", planningService.joursFermesEntre(debut, fin)));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats(@RequestParam(defaultValue = "aujourd_hui") String periode, Authentication authentication) {
        LocalDate[] dates = datesPeriode(periode);
        return ResponseEntity.ok(rendezVousService.getStatistiques(dates[0], dates[1], praticienFiltre(authentication)));
    }

    private LocalDate[] datesPeriode(String periode) {
        LocalDate today = LocalDate.now(ZoneId.of("Indian/Antananarivo"));
        return switch (periode.toLowerCase()) {
            case "semaine", "cette_semaine" -> new LocalDate[]{today.with(java.time.DayOfWeek.MONDAY), today.with(java.time.DayOfWeek.SUNDAY)};
            case "a_venir", "avenir" -> new LocalDate[]{today, today.plusDays(30)};
            case "mois", "ce_mois" -> new LocalDate[]{today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth())};
            default -> new LocalDate[]{today, today};
        };
    }

    private Long praticienFiltre(Authentication authentication) {
        if (authentication == null || hasAnyRole(authentication, "SUPER_ADMIN", "SECRETAIRE")) return null;
        return utilisateurRepository.findByEmail(authentication.getName()).map(Utilisateur::getId)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Compte interne introuvable."));
    }

    @GetMapping("/corbeille")
    public ResponseEntity<List<? extends RendezVousCorbeilleDTO>> corbeille(Authentication authentication) {
        boolean superAdmin = hasRole(authentication, "SUPER_ADMIN");
        List<RendezVous> deleted = rendezVousService.getCorbeille();
        if (!superAdmin) return ResponseEntity.ok(deleted.stream().map(this::toTrashDto).toList());
        return ResponseEntity.ok(deleted.stream().map(rdv -> new RendezVousCorbeilleSuperAdminDTO(
                toTrashDto(rdv), toUserResume(rdv.getDeletedBy()))).toList());
    }

    @GetMapping("/form-options")
    public ResponseEntity<Map<String, Object>> formOptions(Authentication authentication) {
        var services = serviceRepository.findAll().stream().map(s -> Map.of("id", s.getId(), "nom", s.getNom())).toList();
        // La liste du personnel n'est donnée qu'aux utilisateurs connectés : le patient ne choisit plus son praticien.
        var practitioners = !isInternal(authentication) ? List.<Map<String, Object>>of()
                : utilisateurRepository.findByRoleIn(List.of(Role.MEDECIN, Role.SAGE_FEMME, Role.INFIRMIERE)).stream()
                        .map(u -> Map.<String, Object>of("id", u.getId(), "nom", u.getNom(), "role", u.getRole().name())).toList();
        return ResponseEntity.ok(Map.of("services", services, "praticiens", practitioners));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RendezVousResponseDTO> modifier(@PathVariable Long id, @Valid @RequestBody RendezVousRequestDTO dto, Authentication authentication) {
        return ResponseEntity.ok(rendezVousService.modifierRendezVous(id, dto, hasRole(authentication, "SUPER_ADMIN")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id, Authentication authentication) { rendezVousService.mettreEnCorbeille(id, authentication.getName()); return ResponseEntity.noContent().build(); }

    @PutMapping("/{id}/restaurer")
    public ResponseEntity<Void> restaurer(@PathVariable Long id) { rendezVousService.restaurer(id); return ResponseEntity.noContent().build(); }

    @PutMapping("/{id}/annuler-suppression")
    public ResponseEntity<Void> annulerSuppression(@PathVariable Long id, Authentication authentication) {
        rendezVousService.annulerMiseEnCorbeille(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/corbeille/{id}")
    public ResponseEntity<Void> supprimerDefinitivement(@PathVariable Long id) { rendezVousService.supprimerDefinitivement(id); return ResponseEntity.noContent().build(); }

    @DeleteMapping("/corbeille")
    public ResponseEntity<Void> viderCorbeille() { rendezVousService.viderCorbeille(); return ResponseEntity.noContent().build(); }

    // ============ RDV D'UN CLIENT PAR TÉLÉPHONE ============
    @GetMapping("/telephone/{telephone}")
    public ResponseEntity<List<RendezVousResponseDTO>> getParTelephone(@PathVariable String telephone) {
        return ResponseEntity.ok(rendezVousService.getRendezVousParTelephone(telephone));
    }

    // ============ RECHERCHE PAR NOM OU NUMÉRO (page « Mes RDV ») ============
    @GetMapping("/recherche")
    public ResponseEntity<List<RendezVousResponseDTO>> rechercher(@RequestParam String q, Authentication authentication) {
        return ResponseEntity.ok(rendezVousService.rechercherPourClient(q, isInternal(authentication)));
    }

    // ============ RDV PAR STATUT (médecin) ============
    @GetMapping("/statut/{statut}")
    public ResponseEntity<List<RendezVousResponseDTO>> getParStatut(@PathVariable StatutRDV statut) {
        return ResponseEntity.ok(rendezVousService.getRendezVousParStatut(statut));
    }

    // ============ CRÉNEAUX DISPONIBLES D'UNE JOURNÉE ============
    @GetMapping("/creneaux/{date}")
    public ResponseEntity<List<LocalTime>> getCreneauxDisponibles(@PathVariable String date) {
        LocalDate localDate = LocalDate.parse(date);
        return ResponseEntity.ok(rendezVousService.getCreneauxDisponibles(localDate));
    }

    @GetMapping("/creneaux")
    public ResponseEntity<List<LocalTime>> getCreneauxDisponiblesQuery(
            @RequestParam LocalDate date,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long praticienId) {
        return ResponseEntity.ok(rendezVousService.getCreneauxDisponibles(date, praticienId));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> telechargerPdf(@PathVariable Long id,
            @RequestHeader(value = "X-RDV-Access-Token", required = false) String accessToken,
            Authentication authentication) throws IOException {
        RendezVous rdv = rendezVousService.rendezVousPdfAutorise(id, accessToken, isInternal(authentication));
        String filename = "rdv-" + rdv.getId() + "-" + rdv.getDateRdv() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(pdfService.generate(rdv));
    }

    private boolean isInternal(Authentication authentication) {
        return hasAnyRole(authentication, "SUPER_ADMIN", "SECRETAIRE", "MEDECIN", "SAGE_FEMME", "INFIRMIERE");
    }

    private boolean hasRole(Authentication authentication, String role) {
        return hasAnyRole(authentication, role);
    }

    private boolean hasAnyRole(Authentication authentication, String... roles) {
        if (authentication == null) return false;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            for (String role : roles) if (authority.getAuthority().equals("ROLE_" + role)) return true;
        }
        return false;
    }

    private RendezVousCorbeilleDTO toTrashDto(RendezVous rdv) {
        return new RendezVousCorbeilleDTO(rdv.getId(), rdv.getNomClient(), rdv.getTelephoneClient(),
                rdv.getDateRdv(), rdv.getHeureRdv(), rdv.getDeletedAt());
    }

    private UtilisateurResumeDTO toUserResume(Utilisateur user) {
        return user == null ? null : new UtilisateurResumeDTO(user.getNom(), user.getRole());
    }

    // ============ CONFIRMER UN RDV (médecin) ============
    @PutMapping("/{id}/confirmer")
    public ResponseEntity<RendezVousResponseDTO> confirmer(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> corps, Authentication authentication) {
        return ResponseEntity.ok(rendezVousService.confirmerRendezVous(id, praticienIdDe(corps), authentication.getName()));
    }

    // ============ ATTRIBUER / CHANGER LA PERSONNE QUI PREND LE RDV (super administrateur) ============
    @PutMapping("/{id}/praticien")
    public ResponseEntity<RendezVousResponseDTO> attribuer(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> corps, Authentication authentication) {
        return ResponseEntity.ok(rendezVousService.attribuerRendezVous(id, praticienIdDe(corps), authentication.getName()));
    }

    private static Long praticienIdDe(Map<String, Object> corps) {
        Object valeur = corps == null ? null : corps.get("praticienId");
        if (valeur == null || String.valueOf(valeur).isBlank()) return null;
        try { return Long.valueOf(String.valueOf(valeur)); }
        catch (NumberFormatException e) { throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifiant de praticien invalide."); }
    }

    // ============ ANNULER UN RDV ============
    // ============ TERMINER UN RDV (consultation effectuée) ============
    @PutMapping("/{id}/terminer")
    public ResponseEntity<RendezVousResponseDTO> terminer(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(rendezVousService.terminerRendezVous(id, authentication.getName()));
    }

    // Route ouverte au client : il doit présenter le jeton reçu à la création (le personnel connecté en est dispensé).
    @PutMapping("/{id}/annuler")
    public ResponseEntity<RendezVousResponseDTO> annuler(@PathVariable Long id,
            @RequestHeader(value = "X-RDV-Access-Token", required = false) String accessToken,
            Authentication authentication) {
        return ResponseEntity.ok(rendezVousService.annulerRendezVous(id, accessToken, isInternal(authentication)));
    }
}
