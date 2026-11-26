package com.gearfirst.backend.api.auth.repository;

import com.gearfirst.backend.api.auth.entity.Auth;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthRepository extends JpaRepository<Auth, Long> {
    Optional<Auth> findByEmail(String email);

    Optional<Auth> findByUserId(Long userId);
}
