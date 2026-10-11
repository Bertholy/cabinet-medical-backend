package com.cabinet.cabinet.security;

import com.cabinet.cabinet.enums.Role;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Catalogue des permissions du cabinet. Chaque permission correspond à une action précise appliquée par le serveur.
 * Les droits de départ de chaque rôle reproduisent exactement le fonctionnement d'origine : tant que le super administrateur
 * ne change rien dans « Rôles et permissions », rien ne change. Le super administrateur a toujours toutes les permissions.
 * Une permission « verrouillée » reste réservée au super administrateur (elle permettrait de s'octroyer plus de droits ou
 * d'emporter toutes les données).
 */
public final class Permissions {
    private Permissions() { }

    public record Definition(String code, String groupe, String libelle, String description, boolean verrouille, Set<Role> parDefaut) { }

    // ---- rendez-vous
    public static final String RDV_VOIR = "RDV_VOIR";
    public static final String RDV_CREER = "RDV_CREER";
    public static final String RDV_MODIFIER = "RDV_MODIFIER";
    public static final String RDV_CORBEILLE = "RDV_CORBEILLE";
    public static final String RDV_SUPPRIMER_DEFINITIVEMENT = "RDV_SUPPRIMER_DEFINITIVEMENT";
    public static final String RDV_ATTRIBUER = "RDV_ATTRIBUER";
    public static final String RDV_ACCEPTER = "RDV_ACCEPTER";
    public static final String RDV_CONFIRMER = "RDV_CONFIRMER";
    public static final String RDV_TERMINER = "RDV_TERMINER";
    public static final String OCCUPATION_VOIR = "OCCUPATION_VOIR";
    // ---- patients
    public static final String DOSSIERS_LIRE = "DOSSIERS_LIRE";
    public static final String DOSSIERS_MODIFIER = "DOSSIERS_MODIFIER";
    public static final String DOSSIERS_SUPPRIMER = "DOSSIERS_SUPPRIMER";
    public static final String PATIENTS_RECHERCHER = "PATIENTS_RECHERCHER";
    public static final String MALADIES_LIRE = "MALADIES_LIRE";
    public static final String MALADIES_MODIFIER = "MALADIES_MODIFIER";
    // ---- cabinet
    public static final String STATS_VOIR = "STATS_VOIR";
    public static final String EXPORT_RDV = "EXPORT_RDV";
    public static final String SERVICES_GERER = "SERVICES_GERER";
    public static final String PLANNING_GERER = "PLANNING_GERER";
    public static final String SITE_MODIFIER = "SITE_MODIFIER";
    public static final String JOURNAL_VOIR = "JOURNAL_VOIR";
    // ---- administration (verrouillées)
    public static final String COMPTES_GERER = "COMPTES_GERER";
    public static final String PERMISSIONS_GERER = "PERMISSIONS_GERER";
    public static final String SAUVEGARDES_GERER = "SAUVEGARDES_GERER";

    private static Set<Role> roles(Role... r) { return r.length == 0 ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(List.of(r)); }

    private static final Role MED = Role.MEDECIN, SF = Role.SAGE_FEMME, SEC = Role.SECRETAIRE, INF = Role.INFIRMIERE;

    public static final List<Definition> CATALOGUE = List.of(
            new Definition(RDV_VOIR, "Rendez-vous", "Voir les rendez-vous", "Liste, tableau de bord et calendrier des rendez-vous.", false, roles(MED, SF, SEC, INF)),
            new Definition(RDV_CREER, "Rendez-vous", "Créer un rendez-vous", "Saisir un rendez-vous pour un patient.", false, roles(MED, SF, SEC, INF)),
            new Definition(RDV_MODIFIER, "Rendez-vous", "Modifier ou mettre à la corbeille un rendez-vous", "Changer la date, l'heure ou les informations ; mettre un rendez-vous à la corbeille.", false, roles(MED, SF, SEC, INF)),
            new Definition(RDV_CORBEILLE, "Rendez-vous", "Voir et restaurer la corbeille", "Consulter les rendez-vous supprimés et les restaurer.", false, roles(SEC)),
            new Definition(RDV_SUPPRIMER_DEFINITIVEMENT, "Rendez-vous", "Supprimer définitivement", "Vider la corbeille (action irréversible).", false, roles()),
            new Definition(RDV_ATTRIBUER, "Rendez-vous", "Attribuer un rendez-vous à une personne", "Choisir qui prend chaque rendez-vous.", false, roles()),
            new Definition(RDV_ACCEPTER, "Rendez-vous", "Accepter un rendez-vous attribué", "Accepter les rendez-vous confiés à soi (1re confirmation).", false, roles(MED, SF, INF)),
            new Definition(RDV_CONFIRMER, "Rendez-vous", "Confirmer définitivement un rendez-vous", "2e confirmation : le patient reçoit son SMS.", false, roles()),
            new Definition(RDV_TERMINER, "Rendez-vous", "Marquer une consultation comme terminée", "Clôturer les consultations de ses patients.", false, roles(MED, SF, INF)),
            new Definition(OCCUPATION_VOIR, "Rendez-vous", "Voir l'occupation du personnel", "Qui est libre ou pris, créneau par créneau.", false, roles()),

            new Definition(DOSSIERS_LIRE, "Patients", "Lire les dossiers patients", "Dossiers, comptes rendus, suivi de grossesse et carnets de vaccination.", false, roles(MED, SF, INF)),
            new Definition(DOSSIERS_MODIFIER, "Patients", "Modifier les dossiers patients", "Écrire dans les dossiers, ouvrir une grossesse, enregistrer un vaccin.", false, roles(MED, SF, INF)),
            new Definition(DOSSIERS_SUPPRIMER, "Patients", "Supprimer dans les dossiers", "Supprimer un compte rendu, une grossesse ou un enfant.", false, roles(MED)),
            new Definition(PATIENTS_RECHERCHER, "Patients", "Rechercher un patient", "Barre de recherche des patients.", false, roles(MED, SF, SEC, INF)),
            new Definition(MALADIES_LIRE, "Patients", "Consulter les fiches maladies et traitements", "Fiches utilisées par l'assistant d'ordonnance.", false, roles(MED, SF, INF)),
            new Definition(MALADIES_MODIFIER, "Patients", "Modifier les fiches maladies et traitements", "Créer et changer les fiches médicales.", false, roles(MED)),

            new Definition(STATS_VOIR, "Cabinet", "Voir les statistiques", "Chiffres d'activité du cabinet.", false, roles(MED, SEC)),
            new Definition(EXPORT_RDV, "Cabinet", "Exporter les rendez-vous", "Fichiers Excel (CSV) et PDF.", false, roles(MED, SF, SEC, INF)),
            new Definition(SERVICES_GERER, "Cabinet", "Gérer les services", "Ajouter, modifier, supprimer les services et leurs photos.", false, roles()),
            new Definition(PLANNING_GERER, "Cabinet", "Gérer les horaires et les congés", "Horaires, durée des créneaux, jours de fermeture, rappels.", false, roles()),
            new Definition(SITE_MODIFIER, "Cabinet", "Modifier le site public", "Textes, boutons, couleurs du site.", false, roles()),
            new Definition(JOURNAL_VOIR, "Cabinet", "Consulter le journal d'audit", "Qui a fait quoi et quand.", false, roles()),

            new Definition(COMPTES_GERER, "Administration", "Gérer les comptes du personnel", "Créer, modifier, supprimer des comptes et changer les rôles.", true, roles()),
            new Definition(PERMISSIONS_GERER, "Administration", "Gérer les rôles et permissions", "Cette page.", true, roles()),
            new Definition(SAUVEGARDES_GERER, "Administration", "Gérer et télécharger les sauvegardes", "Les sauvegardes contiennent toutes les données des patients.", true, roles()));

    public static Optional<Definition> trouver(String code) {
        return CATALOGUE.stream().filter(d -> d.code().equals(code)).findFirst();
    }

    public static boolean existe(String code) { return trouver(code).isPresent(); }
}
