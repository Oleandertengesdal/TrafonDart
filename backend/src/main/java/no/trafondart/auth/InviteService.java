package no.trafondart.auth;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import no.trafondart.common.ApiException;
import no.trafondart.common.ErrorCode;
import no.trafondart.user.User;
import no.trafondart.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Invite codes let admins decide who can join. A code has a maximum number of uses and an expiry time,
 * and can be revoked. Codes look like {@code K7QM-2XRP-9WDF} (60 bits of randomness, no look-alike characters).
 */
@Service
public class InviteService {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 12;
    public static final Duration MAX_VALIDITY = Duration.ofDays(90);

    private final SecureRandom random = new SecureRandom();
    private final InviteCodeRepository codes;
    private final InviteRedemptionRepository redemptions;
    private final UserRepository users;

    public InviteService(InviteCodeRepository codes, InviteRedemptionRepository redemptions, UserRepository users) {
        this.codes = codes;
        this.redemptions = redemptions;
        this.users = users;
    }

    public record CreateInviteRequest(
            @NotNull @Min(1) @Max(100) Integer maxUses,
            @NotNull @Min(1) @Max(90 * 24) Integer validHours,
            @Size(max = 100) String note) {
    }

    public record InviteView(Long id, String hint, int maxUses, int uses, Instant expiresAt, String status,
                             String note, String createdBy, Instant createdAt, List<String> usedBy) {
    }

    /** Returned once, at creation. The plain code is never retrievable again. */
    public record CreatedInvite(String code, InviteView invite) {
    }

    public record InviteCheck(boolean valid, String reason, Instant expiresAt) {
    }

    @Transactional
    public CreatedInvite create(Long adminId, CreateInviteRequest req) {
        String code = generate();
        String normalized = normalize(code);
        Instant expires = Instant.now().plus(Duration.ofHours(req.validHours()));
        String note = req.note() == null || req.note().isBlank() ? null : req.note().trim();
        InviteCode saved = codes.save(new InviteCode(hash(normalized), normalized.substring(0, 4),
                req.maxUses(), expires, note, adminId));
        return new CreatedInvite(code, toView(saved, Map.of(), List.of()));
    }

    @Transactional(readOnly = true)
    public List<InviteView> list() {
        List<InviteCode> all = codes.findAllByOrderByCreatedAtDesc();
        Map<Long, User> userById = users.findAll().stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, List<InviteRedemption>> byCode = redemptions
                .findAllByInviteCodeIdIn(all.stream().map(InviteCode::getId).toList()).stream()
                .collect(Collectors.groupingBy(InviteRedemption::getInviteCodeId));
        return all.stream().map(c -> toView(c, userById, byCode.getOrDefault(c.getId(), List.of()))).toList();
    }

    @Transactional
    public void revoke(Long id) {
        InviteCode code = codes.findById(id).orElseThrow(ApiException::notFound);
        code.revoke();
    }

    @Transactional(readOnly = true)
    public InviteCheck check(String plainCode) {
        return codes.findByCodeHash(hash(normalize(plainCode)))
                .map(c -> {
                    InviteCode.Status s = c.status(Instant.now());
                    return new InviteCheck(s == InviteCode.Status.ACTIVE,
                            s == InviteCode.Status.ACTIVE ? null : errorFor(s).name(),
                            s == InviteCode.Status.ACTIVE ? c.getExpiresAt() : null);
                })
                .orElse(new InviteCheck(false, ErrorCode.INVITE_INVALID.name(), null));
    }

    /** Validates and atomically claims one use of the code for {@code userId}. Must run inside the registration transaction. */
    @Transactional
    public void redeem(String plainCode, Long userId) {
        InviteCode code = findUsable(plainCode);
        if (codes.tryClaimUse(code.getId(), Instant.now()) != 1) {
            throw new ApiException(ErrorCode.INVITE_USED_UP);
        }
        redemptions.save(new InviteRedemption(code.getId(), userId));
    }

    /** Throws the precise reason if the code cannot be used right now. */
    @Transactional(readOnly = true)
    public InviteCode findUsable(String plainCode) {
        InviteCode code = codes.findByCodeHash(hash(normalize(plainCode)))
                .orElseThrow(() -> new ApiException(ErrorCode.INVITE_INVALID));
        InviteCode.Status status = code.status(Instant.now());
        if (status != InviteCode.Status.ACTIVE) throw new ApiException(errorFor(status));
        return code;
    }

    private static ErrorCode errorFor(InviteCode.Status status) {
        return switch (status) {
            case EXPIRED -> ErrorCode.INVITE_EXPIRED;
            case USED_UP -> ErrorCode.INVITE_USED_UP;
            case REVOKED -> ErrorCode.INVITE_REVOKED;
            case ACTIVE -> throw new IllegalStateException();
        };
    }

    private InviteView toView(InviteCode c, Map<Long, User> userById, List<InviteRedemption> used) {
        User creator = userById.get(c.getCreatedBy());
        return new InviteView(c.getId(), c.getCodeHint() + "-…", c.getMaxUses(), c.getUses(), c.getExpiresAt(),
                c.status(Instant.now()).name(), c.getNote(), creator == null ? null : creator.getUsername(),
                c.getCreatedAt(),
                used.stream().map(r -> userById.get(r.getUserId())).filter(java.util.Objects::nonNull)
                        .map(User::getUsername).toList());
    }

    String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH + 2);
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    /** Case-insensitive, ignores dashes and spaces so codes can be typed sloppily. */
    static String normalize(String code) {
        if (code == null) return "";
        return code.replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.ROOT);
    }

    static String hash(String normalized) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
