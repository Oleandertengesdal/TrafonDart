package no.trafondart.party;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GameResultRepository extends JpaRepository<GameEntities.GameResult, GameEntities.ResultId> {

    /** Rows of [GameType type, Boolean won, Integer placement, Integer darts, Integer score, Instant completedAt]. */
    @Query("""
            select g.type, r.won, r.placement, r.dartsThrown, r.score, g.completedAt
            from GameResult r, Game g
            where r.id.gameId = g.id and r.id.userId = :userId
              and g.status = no.trafondart.party.GameEntities.Status.COMPLETED
            order by g.completedAt
            """)
    List<Object[]> findForUser(@Param("userId") Long userId);

    /** Rows of [UUID gameId, GameType type, Boolean won, Integer placement, Integer darts, Integer score, Instant completedAt]. */
    @Query("""
            select g.id, g.type, r.won, r.placement, r.dartsThrown, r.score, g.completedAt
            from GameResult r, Game g
            where r.id.gameId = g.id and r.id.userId = :userId
              and g.status = no.trafondart.party.GameEntities.Status.COMPLETED
            order by g.completedAt
            """)
    List<Object[]> findEntriesForUser(@Param("userId") Long userId);
}
