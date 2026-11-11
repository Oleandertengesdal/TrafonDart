package no.trafondart.social;

import jakarta.validation.Valid;
import no.trafondart.auth.AuthUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Every mutation returns the fresh {@link SocialService.SocialView} so the client can render it directly. */
@RestController
public class SocialController {

    private final SocialService service;

    public SocialController(SocialService service) {
        this.service = service;
    }

    @GetMapping("/api/matches/{id}/social")
    public SocialService.SocialView get(@PathVariable UUID id) {
        return service.view(id, AuthUser.current());
    }

    @PutMapping("/api/matches/{id}/kudos")
    public SocialService.SocialView giveKudos(@PathVariable UUID id) {
        return service.giveKudos(id, AuthUser.current());
    }

    @DeleteMapping("/api/matches/{id}/kudos")
    public SocialService.SocialView removeKudos(@PathVariable UUID id) {
        return service.removeKudos(id, AuthUser.current());
    }

    @PostMapping("/api/matches/{id}/comments")
    public SocialService.SocialView comment(@PathVariable UUID id, @Valid @RequestBody SocialService.CommentRequest req) {
        return service.addComment(id, AuthUser.current(), req);
    }

    @PatchMapping("/api/comments/{commentId}")
    public SocialService.SocialView edit(@PathVariable Long commentId, @Valid @RequestBody SocialService.EditCommentRequest req) {
        return service.editComment(commentId, AuthUser.current(), req);
    }

    @DeleteMapping("/api/comments/{commentId}")
    public SocialService.SocialView delete(@PathVariable Long commentId) {
        return service.deleteComment(commentId, AuthUser.current());
    }

    @PutMapping("/api/comments/{commentId}/like")
    public SocialService.SocialView like(@PathVariable Long commentId) {
        return service.like(commentId, AuthUser.current());
    }

    @DeleteMapping("/api/comments/{commentId}/like")
    public SocialService.SocialView unlike(@PathVariable Long commentId) {
        return service.unlike(commentId, AuthUser.current());
    }
}
