package no.trafondart.bot;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BotMatchRepository extends JpaRepository<BotMatch, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from BotMatch m where m.id = :id")
    Optional<BotMatch> findByIdForUpdate(@Param("id") UUID id);

    Optional<BotMatch> findFirstByUserIdAndStatus(Long userId, BotMatch.Status status);

    /** Finished matches (played out or given up), oldest first. */
    @Query("select m from BotMatch m where m.userId = :userId and m.status <> no.trafondart.bot.BotMatch.Status.IN_PROGRESS order by m.completedAt, m.createdAt")
    List<BotMatch> findFinishedFor(@Param("userId") Long userId);

    List<BotMatch> findAllByUserIdOrderByCreatedAt(Long userId);

    @Modifying
    @Query("delete from BotMatch m where m.userId = :userId")
    void deleteAllForUser(@Param("userId") Long userId);
}
