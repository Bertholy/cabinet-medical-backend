package com.cabinet.cabinet.dto;

import com.cabinet.cabinet.enums.Role;

public record ProfilResponseDTO(Long id, String nom, String email, Role role, String photoUrl) { }
