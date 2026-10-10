package com.cabinet.cabinet.repository;

import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByEmail(String email);

    Optional<Utilisateur> findByEmailIgnoreCase(String email);

    long countByRole(Role role);

    List<Utilisateur> findByRoleIn(Collection<Role> roles);
    
}
