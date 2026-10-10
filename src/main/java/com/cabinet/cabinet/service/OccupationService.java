package com.cabinet.cabinet.service;

import com.cabinet.cabinet.entity.RendezVous;
import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.enums.Role;
import com.cabinet.cabinet.repository.RendezVousRepository;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Occupation du personnel par créneau pour une journée : qui est libre, qui est pris et par quel patient.
 * Sert au super administrateur pour décider qui prend un rendez-vous au moment de le confirmer.
 */
@Service
@RequiredArgsConstructor
public class OccupationService {
    private final UtilisateurRepository utilisateurs;
    private final RendezVousRepository rendezVous;
    private final PlanningService planning;

    @Transactional(readOnly = true)
    public Map<String, Object> pourLeJour(LocalDate date) {
        List<RendezVous> actifs = rendezVous.findByDateRdvAndSupprimeFalse(date); // ni supprimés ni annulés
        // créneaux du planning + horaires de rendez-vous existants (au cas où le planning a changé depuis)
        TreeSet<LocalTime> heures = new TreeSet<>(planning.creneaux(date));
        actifs.forEach(r -> heures.add(r.getHeureRdv()));

        List<Utilisateur> personnel = new ArrayList<>(utilisateurs.findByRoleIn(List.of(Role.MEDECIN, Role.SAGE_FEMME, Role.INFIRMIERE)));
        personnel.sort(Comparator.comparing(Utilisateur::getNom, String.CASE_INSENSITIVE_ORDER));

        List<Map<String, Object>> lignes = new ArrayList<>();
        for (Utilisateur u : personnel) {
            Map<String, Object> occupations = new LinkedHashMap<>();
            for (RendezVous r : actifs) {
                if (r.getPraticien() != null && r.getPraticien().getId().equals(u.getId())) {
                    Map<String, Object> cellule = new LinkedHashMap<>();
                    cellule.put("rdvId", r.getId());
                    cellule.put("patient", r.getNomClient());
                    cellule.put("statut", r.getStatut());
                    occupations.put(hhmm(r.getHeureRdv()), cellule);
                }
            }
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("id", u.getId());
            ligne.put("nom", u.getNom());
            ligne.put("role", u.getRole());
            ligne.put("photoUrl", u.getPhotoUrl());
            ligne.put("total", occupations.size());
            ligne.put("occupations", occupations);
            lignes.add(ligne);
        }

        // rendez-vous encore sans personne attribuée, par créneau : la « demande » à répartir
        Map<String, List<Map<String, Object>>> sansPraticien = new LinkedHashMap<>();
        for (RendezVous r : actifs) {
            if (r.getPraticien() != null) continue;
            Map<String, Object> cellule = new LinkedHashMap<>();
            cellule.put("rdvId", r.getId());
            cellule.put("patient", r.getNomClient());
            cellule.put("statut", r.getStatut());
            sansPraticien.computeIfAbsent(hhmm(r.getHeureRdv()), k -> new ArrayList<>()).add(cellule);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date", date);
        body.put("creneaux", heures.stream().map(OccupationService::hhmm).toList());
        body.put("capaciteParCreneau", planning.parametres().getCapaciteParCreneau());
        body.put("personnel", lignes);
        body.put("sansPraticien", sansPraticien);
        return body;
    }

    private static String hhmm(LocalTime t) {
        return String.valueOf(t).substring(0, 5);
    }
}
