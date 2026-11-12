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

public interface PlayerMatchStatsRepository extends JpaRepository<MatchRecords.PlayerMatchStats, MatchRecords.StatsId> {

    List<MatchRecords.PlayerMatchStats> findAllByIdMatchIdIn(Collection<UUID> matchIds);

    List<MatchRecords.PlayerMatchStats> findAllByIdMatchId(UUID matchId);

    /** [userId, points, darts] summed over all completed matches finished before {@code before}. */
    @Query("""
            select s.id.userId, sum(s.pointsScored), sum(s.dartsThrown)
            from PlayerMatchStats s, Match m
            where s.id.matchId = m.id and m.status = no.trafondart.match.Match.Status.COMPLETED
              and m.completedAt < :before
            group by s.id.userId
            """)
    List<Object[]> totalsBefore(@Param("before") java.time.Instant before);
}
