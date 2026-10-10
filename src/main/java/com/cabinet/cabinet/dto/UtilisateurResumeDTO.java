package com.cabinet.cabinet.dto;

import com.cabinet.cabinet.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UtilisateurResumeDTO {
    private String nom;
    private Role role;
}
