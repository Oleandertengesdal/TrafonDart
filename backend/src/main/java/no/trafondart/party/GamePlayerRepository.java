package no.trafondart.party;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GamePlayerRepository extends JpaRepository<GameEntities.GamePlayer, GameEntities.SlotId> {

    @Query("select p from GamePlayer p where p.id.gameId = :gameId order by p.id.slot")
    List<GameEntities.GamePlayer> findByGame(@Param("gameId") UUID gameId);

    @Query("select p from GamePlayer p where p.id.gameId in :gameIds order by p.id.gameId, p.id.slot")
    List<GameEntities.GamePlayer> findByGames(@Param("gameIds") Collection<UUID> gameIds);
}
