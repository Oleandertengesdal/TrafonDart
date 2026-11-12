package no.trafondart.party;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameRepository extends JpaRepository<GameEntities.Game, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Game g where g.id = :id")
    Optional<GameEntities.Game> findByIdForUpdate(@Param("id") UUID id);

    @Query("select g from Game g where g.status = no.trafondart.party.GameEntities.Status.IN_PROGRESS order by g.createdAt desc")
    List<GameEntities.Game> findLive();

    @Query("""
            select g from Game g where g.status = no.trafondart.party.GameEntities.Status.COMPLETED
            order by g.completedAt desc
            """)
    List<GameEntities.Game> findRecent(Pageable page);

    @Query("""
            select g from Game g where g.status = no.trafondart.party.GameEntities.Status.COMPLETED
              and exists (select 1 from GamePlayer p where p.id.gameId = g.id and p.userId = :userId)
            order by g.completedAt desc
            """)
    List<GameEntities.Game> findRecentFor(@Param("userId") Long userId, Pageable page);

    @Query("""
            select g from Game g where g.status = no.trafondart.party.GameEntities.Status.COMPLETED
              and g.completedAt >= :from and g.completedAt < :to
              and exists (select 1 from GamePlayer p where p.id.gameId = g.id and p.userId = :userId)
            order by g.completedAt
            """)
    List<GameEntities.Game> findCompletedFor(@Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);
}
