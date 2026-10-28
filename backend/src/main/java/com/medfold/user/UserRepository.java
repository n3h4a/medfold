package com.medfold.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);

    default Optional<User> findByEmailNormalized(String email) {
        return findByEmail(User.normalizeEmail(email));
    }

    default boolean existsByEmailNormalized(String email) {
        return existsByEmail(User.normalizeEmail(email));
    }
}
