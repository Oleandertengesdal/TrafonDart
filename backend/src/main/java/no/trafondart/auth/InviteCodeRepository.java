package no.trafondart.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

    Optional<InviteCode> findByCodeHash(String codeHash);

    List<InviteCode> findAllByOrderByCreatedAtDesc();

    /**
     * Atomically claims one use of a code. Returns 1 if a use was claimed, 0 if the code was used up, expired or
     * revoked in the meantime. The single UPDATE makes concurrent registrations unable to exceed {@code max_uses}.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update InviteCode i set i.uses = i.uses + 1
            where i.id = :id and i.uses < i.maxUses and i.revoked = false and i.expiresAt > :now
            """)
    int tryClaimUse(@Param("id") Long id, @Param("now") Instant now);
}
