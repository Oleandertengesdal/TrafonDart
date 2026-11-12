package no.trafondart.challenge;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Challenge c where c.id = :id")
    Optional<Challenge> findByIdForUpdate(@Param("id") Long id);

    /** Open challenges (pending or accepted) the user is part of, newest first. */
    @Query("""
            select c from Challenge c
            where (c.challengerId = :userId or c.opponentId = :userId)
              and c.status in (no.trafondart.challenge.Challenge.Status.PENDING, no.trafondart.challenge.Challenge.Status.ACCEPTED)
            order by c.createdAt desc, c.id desc""")
    List<Challenge> findOpenFor(@Param("userId") Long userId);

    @Query("""
            select count(c) from Challenge c
            where c.challengerId = :userId and c.expiresAt > :now
              and c.status in (no.trafondart.challenge.Challenge.Status.PENDING, no.trafondart.challenge.Challenge.Status.ACCEPTED)""")
    long countOpenFrom(@Param("userId") Long userId, @Param("now") Instant now);

    @Query("""
            select count(c) > 0 from Challenge c
            where ((c.challengerId = :a and c.opponentId = :b) or (c.challengerId = :b and c.opponentId = :a))
              and c.expiresAt > :now
              and c.status in (no.trafondart.challenge.Challenge.Status.PENDING, no.trafondart.challenge.Challenge.Status.ACCEPTED)""")
    boolean existsOpenBetween(@Param("a") Long a, @Param("b") Long b, @Param("now") Instant now);

    /** Marks every open challenge past its deadline as expired. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Challenge c set c.status = no.trafondart.challenge.Challenge.Status.EXPIRED
            where c.expiresAt <= :now
              and c.status in (no.trafondart.challenge.Challenge.Status.PENDING, no.trafondart.challenge.Challenge.Status.ACCEPTED)""")
    int expireDue(@Param("now") Instant now);

    /** Calls off every open challenge involving the user (used when an account is deleted). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Challenge c set c.status = no.trafondart.challenge.Challenge.Status.CANCELLED
            where (c.challengerId = :userId or c.opponentId = :userId)
              and c.status in (no.trafondart.challenge.Challenge.Status.PENDING, no.trafondart.challenge.Challenge.Status.ACCEPTED)""")
    int cancelAllFor(@Param("userId") Long userId);

    @Query("select c from Challenge c where c.challengerId = :userId or c.opponentId = :userId order by c.createdAt, c.id")
    List<Challenge> findAllFor(@Param("userId") Long userId);
}
