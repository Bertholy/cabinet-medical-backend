package com.cabinet.cabinet.enums;

public enum Role {
    MEDECIN,
    SAGE_FEMME,
    SECRETAIRE,
    SUPER_ADMIN,
    // Kept to preserve compatibility with existing accounts in the database.
    INFIRMIERE
}
