package no.trafondart.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** A new link replaces any older unused ones for the same person. */
    @Modifying(flushAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
    int invalidateOpenFor(@Param("userId") Long userId, @Param("now") Instant now);

    /** Claims the token for use. Returns 1 only for the single request that wins; 0 if already used or expired. */
    @Modifying(flushAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :now where t.id = :id and t.usedAt is null and t.expiresAt > :now")
    int claim(@Param("id") Long id, @Param("now") Instant now);
}
