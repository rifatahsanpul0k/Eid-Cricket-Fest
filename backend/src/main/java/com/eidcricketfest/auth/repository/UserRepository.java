package com.eidcricketfest.auth.repository;

import com.eidcricketfest.auth.entity.User;
import com.eidcricketfest.auth.entity.RoleCode;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "roles")
    @Query("""
        SELECT DISTINCT u FROM User u
        WHERE :query = ''
           OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :query, '%'))
    """)
    Page<User> searchForAdministration(@Param("query") String query, Pageable pageable);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    boolean existsByRoles_Code(RoleCode role);

    @EntityGraph(attributePaths = "roles")
    @Query("""
        SELECT u
        FROM User u
        WHERE LOWER(u.email) = LOWER(:identifier)
           OR u.phone = :identifier
    """)
    Optional<User> findByIdentifier(
            @Param("identifier") String identifier
    );

    @EntityGraph(attributePaths = "roles")
    @Query("""
        SELECT DISTINCT u
        FROM User u
        JOIN u.roles r
        WHERE r.code = :role
          AND u.enabled = true
        ORDER BY u.displayName ASC
    """)
    List<User> findEnabledByRole(
            @Param("role") RoleCode role
    );
}
