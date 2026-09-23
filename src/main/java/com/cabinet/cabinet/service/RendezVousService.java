package com.cabinet.cabinet.service;

import com.cabinet.cabinet.dto.RendezVousRequestDTO;
import com.cabinet.cabinet.dto.RendezVousResponseDTO;
import com.cabinet.cabinet.entity.RendezVous;
import com.cabinet.cabinet.enums.StatutRDV;
import com.cabinet.cabinet.exception.CreneauIndisponibleException;
import com.cabinet.cabinet.exception.RendezVousIntrouvableException;
import com.cabinet.cabinet.exception.StatutInvalideException;
import com.cabinet.cabinet.repository.RendezVousRepository;
import com.cabinet.cabinet.sms.SmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RendezVousService {

    private final RendezVousRepository rendezVousRepository;
    private final SmsService smsService;

    // ============ NUMÉRO DU MÉDECIN ============
    private static final String NUMERO_MEDECIN = "+261343498888";

    // ============ CRÉNEAUX HORAIRES ============
    private static final List<LocalTime> CRENEAUX = List.of(
            LocalTime.of(8, 0),
            LocalTime.of(8, 30),
            LocalTime.of(9, 0),
            LocalTime.of(9, 30),
            LocalTime.of(10, 0),
            LocalTime.of(10, 30),
            LocalTime.of(14, 0),
            LocalTime.of(14, 30),
            LocalTime.of(15, 0),
            LocalTime.of(15, 30),
            LocalTime.of(16, 0),
            LocalTime.of(16, 30)
    );

    // ============ CRÉER UN RDV ============
    @Transactional
    public RendezVousResponseDTO creerRendezVous(RendezVousRequestDTO dto) {
        // 1. Vérifier que le créneau est valide
        if (!CRENEAUX.contains(dto.getHeureRdv())) {
            throw new CreneauIndisponibleException("Créneau horaire invalide");
        }

        // 2. Vérifier que le créneau est libre
        if (rendezVousRepository.existsByDateRdvAndHeureRdv(dto.getDateRdv(), dto.getHeureRdv())) {
            throw new CreneauIndisponibleException("Ce créneau est déjà réservé");
        }

        // 3. Créer l'entité
        RendezVous rdv = new RendezVous();
        rdv.setNomClient(dto.getNomClient());
        rdv.setAgeClient(dto.getAgeClient());
        rdv.setTelephoneClient(dto.getTelephoneClient());
        rdv.setDateRdv(dto.getDateRdv());
        rdv.setHeureRdv(dto.getHeureRdv());
        // statut et dateCreation seront mis par @PrePersist

        // 4. Sauvegarder
        RendezVous saved = rendezVousRepository.save(rdv);

        // 5. Envoyer SMS au médecin (notification)
        String message = "Nouveau RDV : " + saved.getNomClient()
                + " le " + saved.getDateRdv()
                + " à " + saved.getHeureRdv();
        smsService.envoyerSms(NUMERO_MEDECIN, message);

        // 6. Convertir en DTO de réponse
        return toResponseDTO(saved);
    }

    // ============ LISTER TOUS LES RDV ============
    public List<RendezVousResponseDTO> getAllRendezVous() {
        return rendezVousRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    // ============ RDV PAR TÉLÉPHONE (pour le client) ============
    public List<RendezVousResponseDTO> getRendezVousParTelephone(String telephone) {
        return rendezVousRepository.findByTelephoneClient(telephone)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    // ============ RDV PAR STATUT (pour le médecin) ============
    public List<RendezVousResponseDTO> getRendezVousParStatut(StatutRDV statut) {
        return rendezVousRepository.findByStatut(statut)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    // ============ CONFIRMER UN RDV (médecin) ============
    @Transactional
    public RendezVousResponseDTO confirmerRendezVous(Long id) {
        RendezVous rdv = rendezVousRepository.findById(id)
                .orElseThrow(() -> new RendezVousIntrouvableException("Rendez-vous introuvable avec l'id : " + id));

        if (rdv.getStatut() != StatutRDV.EN_ATTENTE) {
            throw new StatutInvalideException("Seul un RDV en attente peut être confirmé");
        }

        rdv.setStatut(StatutRDV.CONFIRME);
        RendezVous updated = rendezVousRepository.save(rdv);

        // Envoyer SMS au client (confirmation)
        String message = "Votre RDV du " + updated.getDateRdv()
                + " à " + updated.getHeureRdv()
                + " est CONFIRMÉ. Cabinet médical.";
        smsService.envoyerSms(updated.getTelephoneClient(), message);

        return toResponseDTO(updated);
    }

    // ============ ANNULER UN RDV ============
    @Transactional
    public RendezVousResponseDTO annulerRendezVous(Long id) {
        RendezVous rdv = rendezVousRepository.findById(id)
                .orElseThrow(() -> new RendezVousIntrouvableException("Rendez-vous introuvable avec l'id : " + id));

        if (rdv.getStatut() == StatutRDV.TERMINE) {
            throw new StatutInvalideException("Impossible d'annuler un RDV terminé");
        }

        rdv.setStatut(StatutRDV.ANNULE);
        RendezVous updated = rendezVousRepository.save(rdv);

        // Envoyer SMS au client (annulation)
        String message = "Votre RDV du " + updated.getDateRdv()
                + " à " + updated.getHeureRdv()
                + " a été ANNULÉ. Cabinet médical.";
        smsService.envoyerSms(updated.getTelephoneClient(), message);

        return toResponseDTO(updated);
    }

    // ============ CRÉNEAUX DISPONIBLES D'UNE JOURNÉE ============
    public List<LocalTime> getCreneauxDisponibles(LocalDate date) {
        List<RendezVous> rdvDuJour = rendezVousRepository.findByDateRdv(date);

        List<LocalTime> heuresPrises = rdvDuJour.stream()
                .map(rdv -> rdv.getHeureRdv())
                .toList();

        List<LocalTime> disponibles = new ArrayList<>();
        for (LocalTime creneau : CRENEAUX) {
            if (!heuresPrises.contains(creneau)) {
                disponibles.add(creneau);
            }
        }
        return disponibles;
    }

    // ============ CONVERSION ENTITÉ → DTO ============
    private RendezVousResponseDTO toResponseDTO(RendezVous rdv) {
        RendezVousResponseDTO dto = new RendezVousResponseDTO();
        dto.setId(rdv.getId());
        dto.setNomClient(rdv.getNomClient());
        dto.setAgeClient(rdv.getAgeClient());
        dto.setTelephoneClient(rdv.getTelephoneClient());
        dto.setDateRdv(rdv.getDateRdv());
        dto.setHeureRdv(rdv.getHeureRdv());
        dto.setStatut(rdv.getStatut());
        dto.setDateCreation(rdv.getDateCreation());
        return dto;
    }
}