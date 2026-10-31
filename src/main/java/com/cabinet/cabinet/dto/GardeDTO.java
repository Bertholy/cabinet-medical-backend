package com.cabinet.cabinet.dto;

import java.time.LocalDate;

/** Une personne de garde : qui, quel jour, de jour ou de nuit. */
public record GardeDTO(Long id, LocalDate date, String periode, Long utilisateurId, String utilisateurNom, String role, String roleLibelle, String telephone, String note) { }
