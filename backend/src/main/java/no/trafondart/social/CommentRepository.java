package no.trafondart.social;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<SocialEntities.Comment, Long> {

    @Query("select c from MatchComment c where c.matchId = :matchId order by c.createdAt, c.id")
    List<SocialEntities.Comment> findByMatch(@Param("matchId") UUID matchId);

    @Query("select count(c) > 0 from MatchComment c where c.parentId = :parentId and c.deleted = false")
    boolean hasLiveReplies(@Param("parentId") Long parentId);

    /** [matchId, count] of visible comments for list views. */
    @Query("select c.matchId, count(c) from MatchComment c where c.matchId in :ids and c.deleted = false group by c.matchId")
    List<Object[]> countByMatchIds(@Param("ids") Collection<UUID> ids);
}
