package no.trafondart.social;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommentLikeRepository extends JpaRepository<SocialEntities.CommentLike, SocialEntities.LikeId> {

    @Query("select l from CommentLike l where l.id.commentId in :ids")
    List<SocialEntities.CommentLike> findByCommentIds(@Param("ids") Collection<Long> ids);

    @Query("select l from CommentLike l where l.id.userId = :userId order by l.createdAt")
    List<SocialEntities.CommentLike> findByUser(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from CommentLike l where l.id.userId = :userId")
    int deleteByUser(@Param("userId") Long userId);
}
