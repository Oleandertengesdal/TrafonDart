package no.trafondart.social;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommentLikeRepository extends JpaRepository<SocialEntities.CommentLike, SocialEntities.LikeId> {

    @Query("select l from CommentLike l where l.id.commentId in :ids")
    List<SocialEntities.CommentLike> findByCommentIds(@Param("ids") Collection<Long> ids);
}
