package no.trafondart.party;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GameDartRepository extends JpaRepository<GameEntities.GameDart, GameEntities.DartId> {

    @Query("select d from GameDart d where d.id.gameId = :gameId order by d.id.seq")
    List<GameEntities.GameDart> findByGame(@Param("gameId") UUID gameId);

    @Modifying(flushAutomatically = true)
    @Query("delete from GameDart d where d.id.gameId = :gameId and d.id.seq = :seq")
    int deleteDart(@Param("gameId") UUID gameId, @Param("seq") int seq);
}
