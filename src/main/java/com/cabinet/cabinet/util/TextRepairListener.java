package com.cabinet.cabinet.util;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;

/**
 * Filet de sécurité à l'écriture : avant tout enregistrement, les textes d'une entité qui portent la trace d'un double
 * encodage (« mÃ©dicale ») sont réparés. Un texte cassé envoyé par un script, un import ou un copier-coller n'atteint donc
 * jamais la base. Les champs techniques (mot de passe, adresses, identifiants, fichiers) ne sont jamais touchés.
 */
public class TextRepairListener {
    private static final Set<String> IGNORES = Set.of("motDePasse", "clientAccessTokenHash", "cleSuivi","email", "imageUrl", "photoUrl", "cle", "telephone", "telephoneClient");

    @PrePersist
    @PreUpdate
    public void reparer(Object entite) {
        for (Class<?> type = entite.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field champ : type.getDeclaredFields()) {
                if (champ.getType() != String.class || Modifier.isStatic(champ.getModifiers()) || IGNORES.contains(champ.getName())) continue;
                try {
                    champ.setAccessible(true);
                    String valeur = (String) champ.get(entite);
                    String reparee = TextRepair.repair(valeur);
                    if (reparee != valeur) champ.set(entite, reparee);
                } catch (IllegalAccessException | RuntimeException ignored) {
                    // un champ illisible n'empêche pas l'enregistrement
                }
            }
        }
    }
}
