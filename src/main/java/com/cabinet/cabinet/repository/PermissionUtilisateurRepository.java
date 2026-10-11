package com.cabinet.cabinet.repository;

import com.cabinet.cabinet.entity.PermissionUtilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PermissionUtilisateurRepository extends JpaRepository<PermissionUtilisateur, Long> {
    @Query("select p from PermissionUtilisateur p where p.utilisateur.id = :id")
    List<PermissionUtilisateur> findDeUtilisateur(@Param("id") Long utilisateurId);

    @Query("select p from PermissionUtilisateur p where p.utilisateur.id = :id and p.code = :code")
    Optional<PermissionUtilisateur> findDeUtilisateurEtCode(@Param("id") Long utilisateurId, @Param("code") String code);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PermissionUtilisateur p where p.utilisateur.id = :id")
    int supprimerDe(@Param("id") Long utilisateurId);
}
