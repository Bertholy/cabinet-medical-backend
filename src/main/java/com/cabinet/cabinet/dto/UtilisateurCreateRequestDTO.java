package com.cabinet.cabinet.dto;

import com.cabinet.cabinet.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UtilisateurCreateRequestDTO {
    @NotBlank @Size(max = 100)
    private String nom;
    @NotBlank @Email @Size(max = 150)
    private String email;
    @NotBlank @Size(min = 10, max = 72)
    private String motDePasse;
    @Size(max = 20)
    private String telephone;
    @NotNull
    private Role role;
}
