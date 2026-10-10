package com.cabinet.cabinet.config;

import com.cabinet.cabinet.service.PlanningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Initialisation faite à chaque démarrage, après la création des tables par Hibernate (ddl-auto=update) :
 *
 * 1. Index unique « un praticien ne peut pas avoir deux rendez-vous actifs au même créneau ». Hibernate ne sait pas
 *    créer d'index partiel, et les fichiers db/migration/*.sql ne sont pas exécutés (Flyway n'est pas installé).
 *    L'ancien index « un seul rendez-vous par créneau pour tout le cabinet » est supprimé : la capacité d'un créneau
 *    est désormais un paramètre du planning, vérifié par l'application sous verrou.
 * 2. Horaires d'ouverture par défaut (7 jours sur 7, 8h–11h et 14h–17h, créneaux de 30 min, un patient à la fois).
 *
 * Un rendez-vous annulé ou placé dans la corbeille ne bloque jamais son créneau.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class DatabaseSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PlanningService planning;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbc.execute("DROP INDEX IF EXISTS uq_rendez_vous_active_slot");
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_rendez_vous_praticien_slot "
                    + "ON rendez_vous (date_rdv, heure_rdv, praticien_id) "
                    + "WHERE supprime = false AND statut <> 'ANNULE' AND praticien_id IS NOT NULL");
        } catch (Exception e) {
            // Typiquement : un praticien a déjà deux rendez-vous actifs au même créneau. L'application reste utilisable,
            // la vérification applicative continue de refuser les doublons.
            log.warn("Impossible de créer l'index unique des créneaux par praticien : {}", e.getMessage());
        }
        planning.initialiserParDefaut();
        if (planning.migrerAncienPlanningParDefaut()) log.info("Planning mis à jour : le cabinet reçoit désormais les 7 jours de la semaine.");
    }
}
