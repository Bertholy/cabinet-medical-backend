# Mise en place des rôles CASAFAMI

## Créer le premier super administrateur

Le premier compte super administrateur est provisionné au démarrage du backend à l'aide de variables d'environnement. Aucun compte ou mot de passe par défaut n'est ajouté au code.

Dans la configuration d'exécution Spring Boot de l'IDE, ajouter :

- `CASAFAMI_SUPER_ADMIN_EMAIL` : adresse e-mail du compte à promouvoir ou créer.
- `CASAFAMI_SUPER_ADMIN_PASSWORD` : mot de passe initial, obligatoire seulement si cette adresse n'existe pas encore.

Si l'adresse existe déjà, le compte est promu `SUPER_ADMIN` et conserve son mot de passe actuel. Sinon, un compte `Super administrateur` est créé avec le mot de passe haché. Redémarrer le backend après avoir défini ces variables. Une fois le premier super administrateur créé, retirer les variables de démarrage.

## Gérer les comptes

1. Se connecter sur `/medecin/login` avec le compte super administrateur.
2. La connexion ouvre `/admin/dashboard` ; le menu de gauche donne accès à « Comptes et accès ».
3. Dans « Comptes existants », modifier le rôle d'un compte, son nom, son e-mail ou son téléphone, réinitialiser son mot de passe ou le supprimer. Pour un nouveau compte, utiliser le formulaire « Créer un compte du personnel ».
4. Les espaces personnels sont `/espace/sage-femme` et `/espace/secretariat`. Les médecins gardent `/medecin/dashboard`.

Les comptes hérités du rôle `INFIRMIERE` restent compatibles et sont traités comme des comptes sage-femme jusqu'à leur changement dans l'administration.

## Configuration et tests

- Installation, mise en ligne, variables d'environnement, sauvegardes et activation des SMS : voir **DEPLOY.md** à la racine du projet.
- Tests backend : ils utilisent une base séparée `casafami_test` (jamais la base réelle). La créer une fois : `CREATE DATABASE casafami_test ENCODING 'UTF8';` puis lancer `./mvnw test`.
- Au démarrage, l'application (re)crée l'index qui empêche un praticien d'avoir deux rendez-vous actifs au même créneau et les horaires par défaut. Un rendez-vous annulé ou dans la corbeille libère son créneau.

## Fonctions réservées au super administrateur

- **Comptes et accès** : création, modification (nom, e-mail, téléphone), changement de rôle, réinitialisation de mot de passe, suppression. Chacun peut aussi changer son propre mot de passe (icône clé en bas du menu).
- **Horaires et congés** : jours et plages d'ouverture, durée des créneaux, nombre de rendez-vous simultanés, jours de fermeture, envoi manuel des rappels de la veille.
- **Journal d'audit** : qui a fait quoi et quand (connexions, rendez-vous, comptes, dossiers, exports…).
- **Modifier le site** : en mode édition, cliquer sur un texte du site public pour le modifier ; faire glisser la poignée ✥ pour déplacer un bouton. « Cette page », « Menu et pied de page » et « Tout le site » remettent l'apparence d'origine.
- **Couleurs du site** : palette globale.

## Droits par rôle (résumé)

| | Super admin | Médecin | Sage-femme | Secrétaire |
|---|---|---|---|---|
| Rendez-vous (voir, créer, modifier, corbeille) | oui | oui (les siens + non attribués) | oui (idem) | oui (tous) |
| Confirmer / terminer un rendez-vous | oui | oui | oui | non |
| Dossiers patients, assistant d'ordonnance | oui | oui | oui | non |
| Maladies et traitements | lire + modifier | lire + modifier | lire | non |
| Statistiques | oui | oui | non | oui |
| Export des rendez-vous (CSV, PDF) | oui | oui | oui | oui |
| Comptes, planning, journal, site, couleurs | oui | non | non | non |
