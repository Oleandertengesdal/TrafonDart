package no.trafondart.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Free text from users (comments, dispute reasons, notes, tournament names). PostgreSQL rejects NUL bytes, which
 * used to surface as a 500; control characters and bidi overrides have no business in a darts app either.
 */
class TextInputTest {

    private static void assertRejected(Runnable r) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void trimsAndKeepsOrdinaryText() {
        assertThat(TextInput.singleLine("  Trafon Cup 2026  ", 60)).isEqualTo("Trafon Cup 2026");
        assertThat(TextInput.multiLine("Rått kast! 🎯\nRevansj?\n\tTakk", 1000)).isEqualTo("Rått kast! 🎯\nRevansj?\n\tTakk");
        assertThat(TextInput.multiLine("<script>alert(1)</script>", 1000)).isEqualTo("<script>alert(1)</script>"); // stored as text, React escapes it
        assertThat(TextInput.multiLine("👨‍👩‍👧 family emoji uses ZWJ", 100)).contains("👨‍👩‍👧");
    }

    @Test
    void nullBlankAndTooLongAreRejected() {
        assertRejected(() -> TextInput.singleLine(null, 10));
        assertRejected(() -> TextInput.singleLine("   ", 10));
        assertRejected(() -> TextInput.multiLine("\n\n", 10));
        assertRejected(() -> TextInput.singleLine("x".repeat(11), 10));
        assertThat(TextInput.singleLine("x".repeat(10), 10)).hasSize(10);
    }

    @Test
    void optionalReturnsNullForEmptyButStillValidatesContent() {
        assertThat(TextInput.optionalSingleLine(null, 10)).isNull();
        assertThat(TextInput.optionalSingleLine("  ", 10)).isNull();
        assertThat(TextInput.optionalSingleLine(" ok ", 10)).isEqualTo("ok");
        assertRejected(() -> TextInput.optionalSingleLine("a\u0000b", 10));
        assertThat(TextInput.optionalMultiLine(null, 10)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"nul\u0000byte", "bell\u0007", "esc\u001b[31mred", "del\u007f", "c1\u0085",
            "rtl‮override", "isolate⁦x⁩", "bom﻿inside"})
    void controlAndBidiCharactersAreRejected(String text) {
        assertRejected(() -> TextInput.multiLine(text, 100));
        assertRejected(() -> TextInput.singleLine(text, 100));
    }

    @Test
    void lineBreaksAreOnlyAllowedInMultiLineText() {
        assertRejected(() -> TextInput.singleLine("two\nlines", 100));
        assertRejected(() -> TextInput.singleLine("carriage\rreturn", 100));
        assertThat(TextInput.multiLine("windows\r\nline", 100)).isEqualTo("windows\r\nline");
    }

    @Test
    void loneSurrogatesAreRejected() {
        assertRejected(() -> TextInput.multiLine("broken \uD83C", 100));
    }
}
