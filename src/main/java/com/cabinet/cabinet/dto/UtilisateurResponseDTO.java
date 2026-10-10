package com.cabinet.cabinet.dto;

import com.cabinet.cabinet.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UtilisateurResponseDTO {
    private Long id;
    private String nom;
    private String email;
    private String telephone;
    private Role role;
    private String photoUrl;
}
