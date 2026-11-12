package no.trafondart.awards;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AwardPeriodRepository extends JpaRepository<AwardEntities.Period, String> {

    @Query("select p from AwardPeriod p order by p.period desc")
    List<AwardEntities.Period> findAllNewestFirst();

    /** 'YYYY-MM' strings sort chronologically, so this removes the month and everything after it. */
    @Modifying(flushAutomatically = true)
    @Query("delete from AwardPeriod p where p.period >= :period")
    int deleteFrom(@Param("period") String period);
}
