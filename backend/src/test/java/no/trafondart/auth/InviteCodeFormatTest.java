package no.trafondart.auth;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InviteCodeFormatTest {

    private final InviteService service = new InviteService(null, null, null);

    @Test
    void codesAreTwelveUnambiguousCharactersInGroupsOfFour() {
        for (int i = 0; i < 2000; i++) {
            String code = service.generate();
            assertThat(code).matches("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}");
            assertThat(code).doesNotContain("0", "O", "1", "I");
        }
    }

    @Test
    void codesDoNotRepeat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 20_000; i++) assertThat(seen.add(service.generate())).isTrue();
    }

    @Test
    void normalisingMakesTypingSloppyHarmless() {
        assertThat(InviteService.normalize(" k7qm-2xrp 9wdf ")).isEqualTo("K7QM2XRP9WDF");
        assertThat(InviteService.normalize("K7QM\t2XRP\n9WDF")).isEqualTo("K7QM2XRP9WDF");
        assertThat(InviteService.normalize(null)).isEmpty();
        assertThat(InviteService.hash(InviteService.normalize("k7qm-2xrp-9wdf")))
                .isEqualTo(InviteService.hash(InviteService.normalize("K7QM2XRP9WDF")));
    }

    @Test
    void normalisingUsesARootLocaleSoTurkishIDoesNotBreakCodes() {
        java.util.Locale before = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertThat(InviteService.normalize("abcd-efgh-jkmn")).isEqualTo("ABCDEFGHJKMN");
        } finally {
            java.util.Locale.setDefault(before);
        }
    }

    @Test
    void onlyAHashIsEverStored() {
        String hash = InviteService.hash("K7QM2XRP9WDF");
        assertThat(hash).hasSize(64).matches("[0-9a-f]+").doesNotContain("K7QM");
    }
}
