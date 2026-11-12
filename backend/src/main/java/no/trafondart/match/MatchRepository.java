package no.trafondart.match;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    /** Row lock so two tablets scoring the same match are serialised. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Match m where m.id = :id")
    Optional<Match> findByIdForUpdate(@Param("id") UUID id);

    List<Match> findAllByStatusOrderByCreatedAtDesc(Match.Status status);

    @Query("""
            select m from Match m
            where (:userId is null or m.player1Id = :userId or m.player2Id = :userId)
              and m.status in :statuses
            order by coalesce(m.completedAt, m.createdAt) desc
            """)
    List<Match> findRecent(@Param("userId") Long userId, @Param("statuses") Collection<Match.Status> statuses,
                           Pageable page);

    @Query("""
            select m from Match m
            where m.status = no.trafondart.match.Match.Status.COMPLETED
              and ((m.player1Id = :a and m.player2Id = :b) or (m.player1Id = :b and m.player2Id = :a))
            order by m.completedAt desc
            """)
    List<Match> findCompletedBetween(@Param("a") Long a, @Param("b") Long b);

    @Query("""
            select m from Match m
            where m.status = no.trafondart.match.Match.Status.COMPLETED and m.rated = true
            order by m.completedAt asc, m.createdAt asc
            """)
    List<Match> findRatedCompletedInOrder();

    @Query("""
            select m from Match m
            where m.status = no.trafondart.match.Match.Status.COMPLETED
              and (m.player1Id = :userId or m.player2Id = :userId)
            """)
    List<Match> findCompletedFor(@Param("userId") Long userId);

    @Query("""
            select m from Match m
            where m.status = no.trafondart.match.Match.Status.COMPLETED
              and m.completedAt >= :start and m.completedAt < :end
            """)
    List<Match> findCompletedBetween(@Param("start") java.time.Instant start, @Param("end") java.time.Instant end);

    /**
     * Rated matches finished in a period. Monthly awards only count these: a rated match always involves the person
     * who created it (or an admin), so nobody can invent results for others to win awards with.
     */
    @Query("""
            select m from Match m
            where m.status = no.trafondart.match.Match.Status.COMPLETED and m.rated = true
              and m.completedAt >= :start and m.completedAt < :end
            """)
    List<Match> findRatedCompletedBetween(@Param("start") java.time.Instant start, @Param("end") java.time.Instant end);

    @Query("select min(m.completedAt) from Match m where m.status = no.trafondart.match.Match.Status.COMPLETED")
    java.util.Optional<java.time.Instant> firstCompletedAt();

    /** Every match the user plays in, whatever its status, oldest first (for the data export). */
    @Query("select m from Match m where m.player1Id = :userId or m.player2Id = :userId order by m.createdAt, m.id")
    List<Match> findAllForPlayer(@Param("userId") Long userId);
}
