# Mises à jour — CASAFAMI (serveur)

## Version du 11 octobre 2026

### Rendez-vous : attribution en deux confirmations
- Le patient ne choisit plus « avec qui » : le **super administrateur attribue** le rendez-vous à une personne du personnel (liste d'occupation par créneau, refus si la personne est déjà prise).
- La personne attribuée **accepte** (`PUT /api/rdv/{id}/accepter`), puis le super administrateur **confirme** (`PUT /api/rdv/{id}/confirmer`, refusé tant que la personne n'a pas accepté). Le patient reçoit alors son SMS (simulé tant que Twilio n'est pas activé).
- `GET /api/rdv/mes-patients` : les rendez-vous confiés à la personne connectée ; `GET /api/rdv/{id}` : détail d'un rendez-vous (un praticien ne voit que les siens ou les rendez-vous libres).
- Page « Occupation du personnel » (`/api/admin/occupation`).

### Notifications (cloche)
- Le **super administrateur est prévenu de tout** : création (patient, secrétaire, médecin, sage-femme), modification, annulation, mise à la corbeille, acceptation par le personnel. Chaque notification indique qui a agi et un clic ouvre la page où traiter la demande.
- Le personnel est prévenu quand un rendez-vous lui est confié, retiré, déplacé ou annulé.
- Notifications système : mot de passe oublié, échec de sauvegarde.

### Comptes et sécurité
- **Plus aucun secret dans le code** : mot de passe de la base et clé de connexion dans `config/application.properties` (ignoré par Git ; modèle `config/application.example.properties`). Le serveur refuse de démarrer avec une clé absente, trop courte ou publique, et retrouve ce fichier quel que soit le dossier de lancement.
- **Mot de passe oublié** : lien par e-mail valable 30 minutes, à usage unique (e-mail simulé dans les journaux tant que `CASAFAMI_MAIL_HOST` n'est pas défini).
- **Sauvegardes automatiques** de la base et des photos (dès que la dernière a plus de 24 h, 14 conservées), page de gestion et téléchargement pour le super administrateur.
- **Messagerie interne** entre membres du personnel.

### Maternité
- **Suivi de grossesse** : terme calculé à partir de la date des dernières règles, calendrier des consultations prénatales (repères OMS), mesures et alertes (tension, cœur du bébé, albuminurie), grossesses précédentes.
- **Carnet de vaccination des enfants** et rappels (consultations en retard, terme proche, vaccins à faire).

### Qualité
- 86 tests automatiques (rendez-vous, notifications, droits par rôle, sauvegardes, mot de passe oublié, messagerie, maternité, sécurité de la configuration).
- Avertissements de l'éditeur corrigés (variables inutilisées, API dépréciées, accès possibles à une valeur nulle, description des réglages personnalisés).

### À savoir
- Les calendriers médicaux (consultations prénatales, vaccins) sont des repères à valider par le médecin.
- Le mot de passe PostgreSQL utilisé avant cette version reste dans l'historique Git : à changer si le dépôt est public.
