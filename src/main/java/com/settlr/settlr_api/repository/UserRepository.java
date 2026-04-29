package com.settlr.settlr_api.repository;

import com.settlr.settlr_api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /** Used during login and registration to look up by email. */
    Optional<User> findByEmail(String email);

    /** Existence check before allowing a second registration. */
    boolean existsByEmail(String email);
}
