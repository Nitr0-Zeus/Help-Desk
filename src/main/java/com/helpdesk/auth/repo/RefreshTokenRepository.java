package com.helpdesk.auth.repo;

import com.helpdesk.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findAllByUserIdAndRevokedFalse(Long userId);

    interface AuthState {
        boolean isRevoked();
        java.time.Instant getExpiresAt();
        Long getUserId();
        boolean isActive();
    }

    @org.springframework.data.jpa.repository.Query("""
            select r.revoked as revoked, r.expiresAt as expiresAt,
                   u.id as userId, u.active as active
            from RefreshToken r join r.user u
            where r.tokenHash = :tokenHash""")
    Optional<AuthState> findAuthStateByTokenHash(@org.springframework.data.repository.query.Param("tokenHash") String tokenHash);
}
