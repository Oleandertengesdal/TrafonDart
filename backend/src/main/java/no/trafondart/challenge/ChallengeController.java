package no.trafondart.challenge;

import jakarta.validation.Valid;
import no.trafondart.auth.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/challenges")
public class ChallengeController {

    private final ChallengeService challenges;

    public ChallengeController(ChallengeService challenges) {
        this.challenges = challenges;
    }

    @GetMapping
    public ChallengeService.ChallengeList list() {
        return challenges.list(AuthUser.current());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChallengeService.ChallengeView create(@Valid @RequestBody ChallengeService.CreateChallengeRequest req) {
        return challenges.create(AuthUser.current(), req);
    }

    @PostMapping("/{id}/accept")
    public ChallengeService.ChallengeView accept(@PathVariable Long id) {
        return challenges.accept(id, AuthUser.current());
    }

    @PostMapping("/{id}/decline")
    public ChallengeService.ChallengeView decline(@PathVariable Long id) {
        return challenges.decline(id, AuthUser.current());
    }

    @PostMapping("/{id}/cancel")
    public ChallengeService.ChallengeView cancel(@PathVariable Long id) {
        return challenges.cancel(id, AuthUser.current());
    }

    @PostMapping("/{id}/start")
    public ChallengeService.ChallengeView start(@PathVariable Long id) {
        return challenges.start(id, AuthUser.current());
    }
}
