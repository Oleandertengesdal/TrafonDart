package no.trafondart.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Browser errors, reported by the frontend's error boundary and global handlers. Open to everyone (errors happen on
 * the login page too) but CSRF-protected, size-limited and rate-limited per client.
 */
@RestController
public class ClientErrorController {

    private static final Logger log = LoggerFactory.getLogger(ClientErrorController.class);
    public static final RateLimiter.Rule CLIENT_ERRORS_PER_IP = new RateLimiter.Rule("client-errors-ip", 10, java.time.Duration.ofMinutes(1));

    public record ClientErrorRequest(@NotBlank @Pattern(regexp = "error|unhandledrejection|boundary") String kind,
                                     @NotBlank @Size(max = 500) String message,
                                     @Size(max = 8000) String stack,
                                     @Size(max = 300) String path,
                                     @Size(max = 40) String release) {
    }

    private final ErrorReporter reporter;
    private final RateLimiter rateLimiter;
    private final ClientIpResolver ipResolver;

    public ClientErrorController(ErrorReporter reporter, RateLimiter rateLimiter, ClientIpResolver ipResolver) {
        this.reporter = reporter;
        this.rateLimiter = rateLimiter;
        this.ipResolver = ipResolver;
    }

    @PostMapping("/api/client-errors")
    public ResponseEntity<Void> report(@Valid @RequestBody ClientErrorRequest req, HttpServletRequest request) {
        rateLimiter.check(CLIENT_ERRORS_PER_IP, ipResolver.resolveAddress(request));
        String agent = request.getHeader(HttpHeaders.USER_AGENT);
        ErrorReporter.ClientError error = new ErrorReporter.ClientError(req.kind(), req.message(), req.stack(), req.path(), req.release(),
                agent == null ? null : agent.substring(0, Math.min(agent.length(), 300)));
        if (reporter.enabled()) {
            reporter.reportClient(error);
        } else {
            log.warn("Browser error ({}) on {}: {}", req.kind(), ErrorReporter.scrub(req.path(), 300), ErrorReporter.scrub(req.message(), 500));
        }
        return ResponseEntity.accepted().build();
    }
}
