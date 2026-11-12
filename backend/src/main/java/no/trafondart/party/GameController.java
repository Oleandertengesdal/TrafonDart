package no.trafondart.party;

import jakarta.validation.Valid;
import no.trafondart.auth.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class GameController {

    private final GameService service;

    public GameController(GameService service) {
        this.service = service;
    }

    @PostMapping("/api/games")
    @ResponseStatus(HttpStatus.CREATED)
    public GameViews.GameView create(@Valid @RequestBody GameViews.CreateGameRequest req) {
        return service.create(AuthUser.current(), req);
    }

    @GetMapping("/api/games/live")
    public List<GameViews.GameSummary> live() {
        return service.live();
    }

    @GetMapping("/api/games/recent")
    public List<GameViews.GameSummary> recent(@RequestParam(required = false) String user, @RequestParam(defaultValue = "20") int limit) {
        return service.recent(user, limit);
    }

    @GetMapping("/api/games/{id}")
    public GameViews.GameView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/api/games/{id}/throws")
    public GameViews.GameView throwDart(@PathVariable UUID id, @Valid @RequestBody GameViews.ThrowRequest req) {
        return service.throwDart(AuthUser.current(), id, req);
    }

    @PostMapping("/api/games/{id}/undo")
    public GameViews.GameView undo(@PathVariable UUID id, @Valid @RequestBody GameViews.UndoRequest req) {
        return service.undo(AuthUser.current(), id, req);
    }

    @PostMapping("/api/games/{id}/abandon")
    public GameViews.GameView abandon(@PathVariable UUID id) {
        return service.abandon(AuthUser.current(), id);
    }

    @GetMapping("/api/users/{username}/game-stats")
    public GameViews.GameStats stats(@PathVariable String username) {
        return service.stats(username);
    }
}
