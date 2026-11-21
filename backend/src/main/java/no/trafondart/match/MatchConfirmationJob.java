package no.trafondart.match;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Confirms finished matches nobody has confirmed or disputed after two days. Runs every ten minutes, and on start-up:
 * the free Render tier sleeps, so after a quiet weekend the first request wakes the server and this catches up.
 */
@Component
public class MatchConfirmationJob {

    private static final Logger log = LoggerFactory.getLogger(MatchConfirmationJob.class);

    private final MatchService matches;

    public MatchConfirmationJob(MatchService matches) {
        this.matches = matches;
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 10 * 60 * 1000)
    public void run() {
        confirmDue();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        confirmDue();
    }

    private void confirmDue() {
        try {
            int n = matches.autoConfirmDue();
            if (n > 0) log.info("Confirmed {} match(es) nobody had confirmed after two days", n);
        } catch (RuntimeException e) {
            log.warn("Could not confirm due matches: {}", e.getMessage());
        }
    }
}
