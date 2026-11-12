package no.trafondart.awards;

import no.trafondart.awards.AwardEntities.Award;
import no.trafondart.awards.AwardService.AwardView;
import no.trafondart.common.ApiException;
import no.trafondart.common.ErrorCode;
import no.trafondart.config.AppProperties;
import no.trafondart.match.MatchEvents;
import no.trafondart.match.MatchRepository;
import no.trafondart.user.User;
import no.trafondart.user.UserDtos.PlayerSummary;
import no.trafondart.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Player of the Year: the sum of a player's month points over the calendar year (see docs/AWARDS.md).
 * Decided once a year has ended, lazily like the months, and re-decided when a match in it is voided.
 */
@Service
public class YearAwardService {

    private static final Logger log = LoggerFactory.getLogger(YearAwardService.class);

    public record YearStanding(int rank, PlayerSummary player, boolean eligible, int matches, double points, int monthsPlayed,
                               int monthsWon) {
    }

    public record YearRace(int year, int minMatches, List<YearStanding> standings, AwardView reigning) {
    }

    public record YearResult(int year, AwardView award) {
    }

    private final AwardService months;
    private final MatchRepository matches;
    private final UserRepository users;
    private final AwardYearRepository years;
    private final YearlyAwardRepository yearly;
    private final MonthlyAwardRepository monthly;
    private final int minMatches;

    public YearAwardService(AwardService months, MatchRepository matches, UserRepository users, AwardYearRepository years,
                            YearlyAwardRepository yearly, MonthlyAwardRepository monthly, AppProperties props) {
        this.months = months;
        this.matches = matches;
        this.users = users;
        this.years = years;
        this.yearly = yearly;
        this.monthly = monthly;
        this.minMatches = props.awards().minMatchesYear();
    }

    public int currentYear() {
        return months.currentMonth().getYear();
    }

    /** Everybody with a rated match in the year, best first; players below the minimum come after. */
    @Transactional(readOnly = true)
    public List<YearStanding> standings(int year) {
        YearMonth last = year == currentYear() ? months.currentMonth() : YearMonth.of(year, 12);
        Map<Long, double[]> totals = new HashMap<>(); // points, matches, monthsPlayed
        Map<Long, PlayerSummary> who = new HashMap<>();
        for (YearMonth m = YearMonth.of(year, 1); !m.isAfter(last); m = m.plusMonths(1)) {
            for (AwardService.Standing s : months.standings(m)) {
                double[] t = totals.computeIfAbsent(s.player().id(), k -> new double[3]);
                t[0] += s.points().total();
                t[1] += s.stats().matches();
                t[2]++;
                who.put(s.player().id(), s.player());
            }
        }
        Map<Long, Integer> monthsWon = new HashMap<>();
        List<String> periods = new ArrayList<>();
        for (int mo = 1; mo <= 12; mo++) periods.add(YearMonth.of(year, mo).toString());
        for (AwardEntities.MonthlyAward a : monthly.findByPeriods(periods)) {
            if (a.getAward() == Award.PLAYER_OF_MONTH) monthsWon.merge(a.getUserId(), 1, Integer::sum);
        }
        List<Map.Entry<Long, double[]>> rows = new ArrayList<>(totals.entrySet());
        rows.sort(Comparator.<Map.Entry<Long, double[]>, Boolean>comparing(e -> e.getValue()[1] < minMatches)
                .thenComparing(e -> -e.getValue()[0])
                .thenComparing(e -> -monthsWon.getOrDefault(e.getKey(), 0))
                .thenComparing(Map.Entry::getKey));
        List<YearStanding> out = new ArrayList<>();
        int rank = 0;
        for (Map.Entry<Long, double[]> e : rows) {
            double[] t = e.getValue();
            boolean eligible = t[1] >= minMatches;
            if (eligible) rank++;
            out.add(new YearStanding(eligible ? rank : 0, who.get(e.getKey()), eligible, (int) t[1], Math.round(t[0] * 10) / 10.0,
                    (int) t[2], monthsWon.getOrDefault(e.getKey(), 0)));
        }
        return out;
    }

    @Transactional
    public YearRace race() {
        ensureFinalized();
        int year = currentYear();
        AwardView reigning = result(year - 1).award();
        return new YearRace(year, minMatches, standings(year), reigning);
    }

    @Transactional
    public synchronized int ensureFinalized() {
        months.ensureFinalized();
        Optional<Instant> first = matches.firstCompletedAt();
        if (first.isEmpty()) return 0;
        int from = first.get().atZone(months.zone()).getYear();
        int decided = 0;
        for (int y = from; y < currentYear(); y++) {
            if (years.existsById(y)) continue;
            finalizeYear(y);
            decided++;
        }
        if (decided > 0) log.info("Decided Player of the Year for {} year(s)", decided);
        return decided;
    }

    /** Stores the winner of {@code year} (if anyone qualified). Idempotent. */
    @Transactional
    public void finalizeYear(int year) {
        if (years.existsById(year)) return;
        years.save(new YearEntities.AwardYear(year));
        standings(year).stream().filter(YearStanding::eligible).findFirst()
                .ifPresent(w -> yearly.save(new YearEntities.YearlyAward(year, w.player().id(), w.points())));
    }

    @Transactional
    public List<YearResult> hallOfFame() {
        ensureFinalized();
        Map<Long, User> userById = users.findAll().stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return yearly.findAllNewestFirst().stream().map(a -> new YearResult(a.getYear(), view(a, userById))).toList();
    }

    private YearResult result(int year) {
        return yearly.findById(new YearEntities.YearlyAwardId(year, Award.PLAYER_OF_YEAR.name()))
                .map(a -> new YearResult(year, view(a, users.findById(a.getUserId()).map(u -> Map.of(u.getId(), u)).orElse(Map.of()))))
                .orElse(new YearResult(year, null));
    }

    private static AwardView view(YearEntities.YearlyAward a, Map<Long, User> userById) {
        User u = userById.get(a.getUserId());
        return new AwardView(String.valueOf(a.getYear()), Award.PLAYER_OF_YEAR, u == null ? null : PlayerSummary.of(u), a.getValue());
    }

    /** Validates a year asked for by a client: from 2000 up to and including the current year. */
    public int checkYear(Integer year) {
        int y = year == null ? currentYear() : year;
        if (y < 2000 || y > currentYear()) throw new ApiException(ErrorCode.VALIDATION_FAILED);
        return y;
    }

    @Scheduled(cron = "0 10 0 1 1 *", zone = "${app.awards.zone:Europe/Oslo}")
    public void yearlyJob() {
        ensureFinalized();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            ensureFinalized();
        } catch (RuntimeException e) {
            log.warn("Could not decide Player of the Year on start-up: {}", e.getMessage());
        }
    }

    @EventListener
    public void onMatchVoided(MatchEvents.MatchVoided event) {
        // AwardService already forgot the affected months and years; nothing else to do here.
    }
}
