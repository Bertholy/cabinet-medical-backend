package com.cabinet.cabinet.repository;

import com.cabinet.cabinet.entity.ReinitialisationMotDePasse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ReinitialisationMotDePasseRepository extends JpaRepository<ReinitialisationMotDePasse, Long> {

    @Query("select r from ReinitialisationMotDePasse r join fetch r.utilisateur where r.tokenHash = :hash")
    Optional<ReinitialisationMotDePasse> trouverParHash(@Param("hash") String hash);

    long countByUtilisateurIdAndCreeLeAfter(Long utilisateurId, LocalDateTime depuis);

    /** Un lien utilisé (ou remplacé par un plus récent) ne doit plus fonctionner. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ReinitialisationMotDePasse r set r.utiliseLe = :maintenant where r.utilisateur.id = :id and r.utiliseLe is null")
    int invaliderLiensDe(@Param("id") Long utilisateurId, @Param("maintenant") LocalDateTime maintenant);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ReinitialisationMotDePasse r where r.expireLe < :limite")
    int supprimerExpiresAvant(@Param("limite") LocalDateTime limite);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ReinitialisationMotDePasse r where r.utilisateur.id = :id")
    int supprimerDe(@Param("id") Long utilisateurId);
}
