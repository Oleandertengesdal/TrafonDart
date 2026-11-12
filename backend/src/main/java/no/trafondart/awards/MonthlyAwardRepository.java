package no.trafondart.awards;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MonthlyAwardRepository extends JpaRepository<AwardEntities.MonthlyAward, Long> {

    @Query("select a from MonthlyAward a where a.period in :periods")
    List<AwardEntities.MonthlyAward> findByPeriods(@Param("periods") Collection<String> periods);

    @Query("select a from MonthlyAward a where a.userId = :userId order by a.period desc, a.award")
    List<AwardEntities.MonthlyAward> findByUser(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from MonthlyAward a where a.period >= :period")
    int deleteFrom(@Param("period") String period);
}
