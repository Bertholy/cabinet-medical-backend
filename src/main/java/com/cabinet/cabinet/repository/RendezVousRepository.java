package com.cabinet.cabinet.repository;
import com.cabinet.cabinet.entity.RendezVous;
import com.cabinet.cabinet.enums.StatutRDV;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

/**
 * Règle métier : un rendez-vous ANNULE ou placé dans la corbeille ne bloque plus son créneau.
 * Un médecin / une sage-femme voit les rendez-vous qui lui sont attribués ET ceux pas encore attribués
 * (praticien null), sinon son tableau de bord resterait vide tant que personne n'attribue de praticien.
 */
public interface RendezVousRepository extends JpaRepository<RendezVous, Long> {

    /** Rendez-vous qui occupent réellement un créneau (ni supprimés, ni annulés). */
    @Query("select r from RendezVous r where (r.supprime = false or r.supprime is null) and r.statut <> com.cabinet.cabinet.enums.StatutRDV.ANNULE and r.dateRdv = :dateRdv")
    List<RendezVous> findByDateRdvAndSupprimeFalse(@Param("dateRdv") LocalDate dateRdv);

    @Query("select r from RendezVous r where (r.supprime = false or r.supprime is null) and r.statut = :statut")
    List<RendezVous> findByStatut(@Param("statut") StatutRDV statut);

    List<RendezVous> findByTelephoneClient(String telephoneClient);

    /** Le paramètre est une clé de recherche (voir PhoneUtils.searchKey) : 261… pour Madagascar, sinon les chiffres. */
    @Query(value = "SELECT * FROM rendez_vous WHERE COALESCE(supprime, false) = false AND " +
            "CASE WHEN regexp_replace(telephone_client, '[^0-9]', '', 'g') LIKE '0%' " +
            "THEN '261' || substring(regexp_replace(telephone_client, '[^0-9]', '', 'g') from 2) " +
            "ELSE regexp_replace(telephone_client, '[^0-9]', '', 'g') END = :telephone " +
            "ORDER BY date_rdv DESC, heure_rdv DESC", nativeQuery = true)
    List<RendezVous> findActiveByNormalizedTelephone(@Param("telephone") String telephone);

    /** Nombre de rendez-vous actifs sur un créneau (la capacité autorisée est un paramètre du planning). */
    @Query("select count(r) from RendezVous r where r.dateRdv = :dateRdv and r.heureRdv = :heureRdv and (r.supprime = false or r.supprime is null) and r.statut <> com.cabinet.cabinet.enums.StatutRDV.ANNULE")
    long countActiveAtSlot(@Param("dateRdv") LocalDate dateRdv, @Param("heureRdv") LocalTime heureRdv);

    @Query("select count(r) from RendezVous r where r.id <> :id and r.dateRdv = :dateRdv and r.heureRdv = :heureRdv and (r.supprime = false or r.supprime is null) and r.statut <> com.cabinet.cabinet.enums.StatutRDV.ANNULE")
    long countOtherActiveAtSlot(@Param("id") Long id, @Param("dateRdv") LocalDate dateRdv, @Param("heureRdv") LocalTime heureRdv);

    /** Un praticien ne peut avoir qu'un seul rendez-vous actif à la fois (excludeId = rendez-vous en cours de modification, 0 sinon). */
    @Query("select count(r) > 0 from RendezVous r where r.praticien.id = :praticienId and r.id <> :excludeId and r.dateRdv = :dateRdv and r.heureRdv = :heureRdv and (r.supprime = false or r.supprime is null) and r.statut <> com.cabinet.cabinet.enums.StatutRDV.ANNULE")
    boolean praticienOccupe(@Param("praticienId") Long praticienId, @Param("excludeId") Long excludeId, @Param("dateRdv") LocalDate dateRdv, @Param("heureRdv") LocalTime heureRdv);

    @Query("select count(r) from RendezVous r where r.dateRdv = :date and (r.supprime = false or r.supprime is null) and r.statut <> com.cabinet.cabinet.enums.StatutRDV.ANNULE")
    long countActifsLe(@Param("date") LocalDate date);

    /** Rendez-vous à rappeler : actifs, en attente ou confirmés, rappel pas encore envoyé. */
    @Query("select r from RendezVous r where r.dateRdv = :date and (r.supprime = false or r.supprime is null) and r.statut in (com.cabinet.cabinet.enums.StatutRDV.EN_ATTENTE, com.cabinet.cabinet.enums.StatutRDV.CONFIRME) and (r.rappelEnvoye = false or r.rappelEnvoye is null) order by r.heureRdv")
    List<RendezVous> findARappeler(@Param("date") LocalDate date);

    long countByStatut(StatutRDV statut);

    @Query("select r from RendezVous r where r.supprime = false or r.supprime is null")
    List<RendezVous> findBySupprimeFalse();

    @Query("select r from RendezVous r where r.supprime = true")
    List<RendezVous> findBySupprimeTrue();

    @Query("select r from RendezVous r where (r.supprime = false or r.supprime is null) and r.dateRdv between :debut and :fin order by r.dateRdv asc, r.heureRdv asc")
    List<RendezVous> findBySupprimeFalseAndDateRdvBetweenOrderByDateRdvAscHeureRdvAsc(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    @Query("select r from RendezVous r where (r.supprime = false or r.supprime is null) and (r.praticien.id = :praticienId or r.praticien is null) and r.dateRdv between :debut and :fin order by r.dateRdv asc, r.heureRdv asc")
    List<RendezVous> findBySupprimeFalseAndPraticienIdAndDateRdvBetweenOrderByDateRdvAscHeureRdvAsc(@Param("praticienId") Long praticienId, @Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    @Query("select count(r) from RendezVous r where (r.supprime = false or r.supprime is null) and r.dateRdv between :debut and :fin")
    long countBySupprimeFalseAndDateRdvBetween(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);
    @Query("select count(r) from RendezVous r where (r.supprime = false or r.supprime is null) and r.statut = :statut and r.dateRdv between :debut and :fin")
    long countBySupprimeFalseAndStatutAndDateRdvBetween(@Param("statut") StatutRDV statut, @Param("debut") LocalDate debut, @Param("fin") LocalDate fin);
    @Query("select count(r) from RendezVous r where (r.supprime = false or r.supprime is null) and (r.praticien.id = :praticienId or r.praticien is null) and r.dateRdv between :debut and :fin")
    long countBySupprimeFalseAndPraticienIdAndDateRdvBetween(@Param("praticienId") Long praticienId, @Param("debut") LocalDate debut, @Param("fin") LocalDate fin);
    @Query("select count(r) from RendezVous r where (r.supprime = false or r.supprime is null) and (r.praticien.id = :praticienId or r.praticien is null) and r.statut = :statut and r.dateRdv between :debut and :fin")
    long countBySupprimeFalseAndPraticienIdAndStatutAndDateRdvBetween(@Param("praticienId") Long praticienId, @Param("statut") StatutRDV statut, @Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    /**
     * Recherche de patients pour le personnel : nom (sans tenir compte des accents ni de la casse) ou numéro (chiffres).
     * « nom » doit être passé par TextUtils.fold + escapeLike ; « chiffres » = chiffres saisis ou « # » (ne correspond à rien).
     */
    @Query(value = "SELECT nom_client, telephone_client FROM rendez_vous WHERE COALESCE(supprime, false) = false AND (" +
            "lower(translate(nom_client, :from, :to)) LIKE '%' || :nom || '%' OR " +
            "regexp_replace(telephone_client, '[^0-9]', '', 'g') LIKE '%' || :chiffres || '%') " +
            "GROUP BY nom_client, telephone_client ORDER BY nom_client LIMIT :limite", nativeQuery = true)
    List<Object[]> rechercherPatients(@Param("from") String from, @Param("to") String to, @Param("nom") String nom,
                                      @Param("chiffres") String chiffres, @Param("limite") int limite);

    /**
     * Rendez-vous dont le nom contient le mot donné (sans tenir compte des accents ni de la casse), à partir d'une date.
     * Le service affine ensuite : tous les mots tapés doivent correspondre au début d'un mot du nom.
     */
    @Query(value = "SELECT * FROM rendez_vous WHERE COALESCE(supprime, false) = false AND date_rdv >= :depuis AND " +
            "lower(translate(nom_client, :from, :to)) LIKE '%' || :mot || '%' " +
            "ORDER BY date_rdv DESC, heure_rdv DESC LIMIT 300", nativeQuery = true)
    List<RendezVous> findCandidatsParNom(@Param("from") String from, @Param("to") String to, @Param("mot") String mot, @Param("depuis") LocalDate depuis);

    @Query("select r from RendezVous r left join fetch r.deletedBy where r.supprime = true order by r.deletedAt desc")
    List<RendezVous> findDeletedWithActor();

    @Query("select r from RendezVous r where r.id = :id and (r.supprime = false or r.supprime is null)")
    Optional<RendezVous> findByIdAndSupprimeFalse(@Param("id") Long id);

    Optional<RendezVous> findByIdAndSupprimeTrue(Long id);

    // Détache les références avant la suppression d'un service ou d'un compte (sinon contrainte de clé étrangère → erreur 500).
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RendezVous r set r.service = null where r.service.id = :serviceId")
    int detachService(@Param("serviceId") Long serviceId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RendezVous r set r.praticien = null where r.praticien.id = :userId")
    int detachPraticien(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RendezVous r set r.deletedBy = null where r.deletedBy.id = :userId")
    int detachDeletedBy(@Param("userId") Long userId);
}
