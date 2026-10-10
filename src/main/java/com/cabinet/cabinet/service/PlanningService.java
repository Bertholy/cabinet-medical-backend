package com.cabinet.cabinet.service;

import com.cabinet.cabinet.dto.PlanningDTO;
import com.cabinet.cabinet.dto.PlanningDTO.HoraireDTO;
import com.cabinet.cabinet.dto.PlanningDTO.JourFermeDTO;
import com.cabinet.cabinet.entity.HoraireJour;
import com.cabinet.cabinet.entity.JourFerme;
import com.cabinet.cabinet.entity.ParametresCabinet;
import com.cabinet.cabinet.repository.HoraireJourRepository;
import com.cabinet.cabinet.repository.JourFermeRepository;
import com.cabinet.cabinet.repository.ParametresCabinetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Horaires d'ouverture, durée des créneaux, capacité et jours de fermeture.
 * Remplace la grille de créneaux auparavant codée en dur (8h–11h et 14h–17h, du lundi au vendredi) :
 * Par défaut le cabinet reçoit les 7 jours de la semaine, week-end compris (8h–11h et 14h–17h) ;
 * le super administrateur peut fermer certains jours depuis « Horaires et congés ».
 */
@SuppressWarnings("null") // avertissements de l'éditeur sur des références de méthode : aucun risque réel
@Service
@RequiredArgsConstructor
public class PlanningService {
    private final HoraireJourRepository horaires;
    private final ParametresCabinetRepository parametres;
    private final JourFermeRepository joursFermes;

    @Transactional
    public void initialiserParDefaut() {
        if (parametres.count() == 0) parametres.save(new ParametresCabinet());
        if (horaires.count() == 0) {
            for (int jour = 1; jour <= 7; jour++) horaires.save(horaireParDefaut(jour));
        }
    }

    private static HoraireJour horaireParDefaut(int jour) {
        return new HoraireJour(jour, true, LocalTime.of(8, 0), LocalTime.of(11, 0), LocalTime.of(14, 0), LocalTime.of(17, 0));
    }

    /**
     * Les premières versions créaient le planning « lundi–vendredi seulement ». Si le planning enregistré est EXACTEMENT
     * cette ancienne valeur par défaut (donc jamais personnalisé), le samedi et le dimanche sont ouverts comme les autres jours.
     * Un planning modifié par le super administrateur n'est jamais touché.
     * @return true si le planning a été mis à jour.
     */
    @Transactional
    public boolean migrerAncienPlanningParDefaut() {
        List<HoraireJour> liste = horaires();
        if (liste.size() != 7) return false;
        boolean ancienParDefaut = liste.stream().allMatch(h -> h.getJourSemaine() <= 5
                ? h.isOuvert() && LocalTime.of(8, 0).equals(h.getMatinDebut()) && LocalTime.of(11, 0).equals(h.getMatinFin())
                        && LocalTime.of(14, 0).equals(h.getApresMidiDebut()) && LocalTime.of(17, 0).equals(h.getApresMidiFin())
                : !h.isOuvert() && h.getMatinDebut() == null && h.getMatinFin() == null && h.getApresMidiDebut() == null && h.getApresMidiFin() == null);
        if (!ancienParDefaut) return false;
        horaires.save(horaireParDefaut(6));
        horaires.save(horaireParDefaut(7));
        return true;
    }

    @Transactional(readOnly = true)
    public ParametresCabinet parametres() {
        return parametres.findById(1L).orElseGet(ParametresCabinet::new);
    }

    @Transactional(readOnly = true)
    public List<HoraireJour> horaires() {
        List<HoraireJour> liste = new ArrayList<>(horaires.findAll());
        liste.sort(Comparator.comparing(HoraireJour::getJourSemaine));
        return liste;
    }

    @Transactional(readOnly = true)
    public Optional<JourFerme> jourFerme(LocalDate date) {
        return joursFermes.findByDate(date);
    }

    /** Tous les créneaux de départ d'une journée (libres ou non). Liste vide si le cabinet est fermé ce jour-là. */
    @Transactional(readOnly = true)
    public List<LocalTime> creneaux(LocalDate date) {
        if (date == null || joursFermes.findByDate(date).isPresent()) return List.of();
        HoraireJour horaire = horaires.findById(date.getDayOfWeek().getValue()).orElse(null);
        if (horaire == null || !horaire.isOuvert()) return List.of();
        int duree = parametres().getDureeCreneauMinutes();
        List<LocalTime> slots = new ArrayList<>();
        ajouterPlage(slots, horaire.getMatinDebut(), horaire.getMatinFin(), duree);
        ajouterPlage(slots, horaire.getApresMidiDebut(), horaire.getApresMidiFin(), duree);
        return slots;
    }

    private void ajouterPlage(List<LocalTime> slots, LocalTime debut, LocalTime fin, int duree) {
        if (debut == null || fin == null) return;
        int minutes = debut.getHour() * 60 + debut.getMinute();
        int limite = fin.getHour() * 60 + fin.getMinute();
        for (; minutes + duree <= limite; minutes += duree) slots.add(LocalTime.of(minutes / 60, minutes % 60));
    }

    @Transactional
    public PlanningDTO enregistrer(PlanningDTO dto) {
        if (dto.horaires().size() != 7 || dto.horaires().stream().map(HoraireDTO::jourSemaine).distinct().count() != 7) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Il faut exactement un horaire pour chacun des 7 jours de la semaine.");
        }
        for (HoraireDTO h : dto.horaires()) {
            verifierPlage(h.matinDebut(), h.matinFin(), "du matin");
            verifierPlage(h.apresMidiDebut(), h.apresMidiFin(), "de l'après-midi");
            if (h.ouvert() && h.matinDebut() == null && h.apresMidiDebut() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un jour ouvert doit avoir au moins une plage horaire.");
            }
            if (h.matinFin() != null && h.apresMidiDebut() != null && h.apresMidiDebut().isBefore(h.matinFin())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'après-midi ne peut pas commencer avant la fin du matin.");
            }
        }
        ParametresCabinet p = parametres.findById(1L).orElseGet(ParametresCabinet::new);
        p.setId(1L);
        p.setDureeCreneauMinutes(dto.dureeCreneauMinutes());
        p.setCapaciteParCreneau(dto.capaciteParCreneau());
        parametres.save(p);
        for (HoraireDTO h : dto.horaires()) {
            horaires.save(new HoraireJour(h.jourSemaine(), h.ouvert(), h.matinDebut(), h.matinFin(), h.apresMidiDebut(), h.apresMidiFin()));
        }
        return lire();
    }

    private void verifierPlage(LocalTime debut, LocalTime fin, String nom) {
        if ((debut == null) != (fin == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La plage " + nom + " doit avoir une heure de début et une heure de fin.");
        }
        if (debut != null && fin != null && !fin.isAfter(debut)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fin de la plage " + nom + " doit être après son début.");
        }
    }

    @Transactional(readOnly = true)
    public PlanningDTO lire() {
        ParametresCabinet p = parametres();
        List<HoraireDTO> liste = horaires().stream()
                .map(h -> new HoraireDTO(h.getJourSemaine(), h.isOuvert(), h.getMatinDebut(), h.getMatinFin(), h.getApresMidiDebut(), h.getApresMidiFin()))
                .toList();
        return new PlanningDTO(p.getDureeCreneauMinutes(), p.getCapaciteParCreneau(), liste);
    }

    @Transactional(readOnly = true)
    public List<JourFermeDTO> joursFermesAVenir(LocalDate depuis) {
        return joursFermes.findByDateGreaterThanEqualOrderByDateAsc(depuis).stream()
                .map(j -> new JourFermeDTO(j.getId(), j.getDate(), j.getMotif())).toList();
    }

    @Transactional(readOnly = true)
    public List<JourFermeDTO> joursFermesEntre(LocalDate debut, LocalDate fin) {
        return joursFermes.findByDateBetweenOrderByDateAsc(debut, fin).stream()
                .map(j -> new JourFermeDTO(j.getId(), j.getDate(), j.getMotif())).toList();
    }

    @Transactional
    public JourFermeDTO fermer(LocalDate date, String motif) {
        if (joursFermes.findByDate(date).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce jour est déjà marqué comme fermé.");
        }
        String propre = motif == null || motif.isBlank() ? null : motif.trim();
        if (propre != null && propre.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le motif ne doit pas dépasser 200 caractères.");
        JourFerme saved = joursFermes.save(new JourFerme(null, date, propre));
        return new JourFermeDTO(saved.getId(), saved.getDate(), saved.getMotif());
    }

    @Transactional
    public LocalDate rouvrir(Long id) {
        JourFerme jour = joursFermes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jour de fermeture introuvable."));
        joursFermes.delete(jour);
        return jour.getDate();
    }
}
