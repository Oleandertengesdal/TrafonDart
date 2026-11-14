package no.trafondart.bot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BotDartRepository extends JpaRepository<BotDart, BotDart.Key> {

    List<BotDart> findAllByMatchIdOrderBySeqAsc(UUID matchId);

    List<BotDart> findAllByMatchIdInOrderByMatchIdAscSeqAsc(Collection<UUID> matchIds);

    @Modifying
    @Query("delete from BotDart d where d.matchId = :matchId and d.seq > :keep")
    void deleteAfter(@Param("matchId") UUID matchId, @Param("keep") int keep);
}
