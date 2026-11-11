package no.trafondart.social;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface KudosRepository extends JpaRepository<SocialEntities.Kudos, SocialEntities.KudosId> {

    @Query("select k from MatchKudos k where k.id.matchId = :matchId order by k.createdAt")
    List<SocialEntities.Kudos> findByMatch(@Param("matchId") UUID matchId);

    /** [matchId, count] pairs for list views. */
    @Query("select k.id.matchId, count(k) from MatchKudos k where k.id.matchId in :ids group by k.id.matchId")
    List<Object[]> countByMatchIds(@Param("ids") Collection<UUID> ids);

    @Query("select count(k) from MatchKudos k, Match m where k.id.matchId = m.id and (m.player1Id = :userId or m.player2Id = :userId)")
    long countReceivedBy(@Param("userId") Long userId);
}
