package no.trafondart.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Case-insensitive lookup that uses the {@code lower(username)} unique index. */
    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findByUsernameIgnoreCase(@Param("username") String username);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username)")
    boolean existsByUsernameIgnoreCase(@Param("username") String username);

    /** Row lock used when updating ratings so two matches finishing at once cannot lose an update. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id in :ids order by u.id")
    List<User> findAllByIdForUpdate(@Param("ids") java.util.Collection<Long> ids);

    long countByRoleAndEnabledTrue(User.Role role);

    List<User> findAllByOrderByRatingDesc();

    List<User> findAllByEnabledTrueOrderByUsernameAsc();
}
