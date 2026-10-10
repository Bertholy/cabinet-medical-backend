package com.cabinet.cabinet.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class RendezVousCorbeilleSuperAdminDTO extends RendezVousCorbeilleDTO {
    private UtilisateurResumeDTO deletedBy;

    public RendezVousCorbeilleSuperAdminDTO(RendezVousCorbeilleDTO base, UtilisateurResumeDTO deletedBy) {
        super(base.getId(), base.getNomClient(), base.getTelephoneClient(), base.getDateRdv(), base.getHeureRdv(), base.getDeletedAt());
        this.deletedBy = deletedBy;
    }
}
