package no.trafondart.common;

/**
 * Cleans free text from users: comments, dispute reasons, notes, tournament names.
 *
 * <p>Strips surrounding whitespace and rejects (with {@code VALIDATION_FAILED}) anything blank, too long, or
 * containing characters that only cause trouble: NUL (PostgreSQL refuses it, which used to surface as a 500),
 * other control characters, bidirectional overrides (used to make text read differently than it is), byte-order
 * marks and broken surrogate pairs. Emoji, accents and zero-width joiners are fine. HTML is <em>not</em> escaped
 * here: it is stored as text and React renders it as text.
 */
public final class TextInput {

    private TextInput() {
    }

    /** One line of text, e.g. a name. Line breaks and tabs are rejected. */
    public static String singleLine(String raw, int maxLength) {
        return clean(raw, maxLength, false);
    }

    /** Text that may contain line breaks and tabs, e.g. a comment. */
    public static String multiLine(String raw, int maxLength) {
        return clean(raw, maxLength, true);
    }

    /** Like {@link #singleLine} but null or blank input gives null instead of an error. */
    public static String optionalSingleLine(String raw, int maxLength) {
        return raw == null || raw.isBlank() ? null : singleLine(raw, maxLength);
    }

    /** Like {@link #multiLine} but null or blank input gives null instead of an error. */
    public static String optionalMultiLine(String raw, int maxLength) {
        return raw == null || raw.isBlank() ? null : multiLine(raw, maxLength);
    }

    private static String clean(String raw, int maxLength, boolean lineBreaks) {
        if (raw == null) throw invalid();
        String text = raw.strip();
        if (text.isEmpty() || text.length() > maxLength) throw invalid();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1))) throw invalid();
                i++;
                continue;
            }
            if (Character.isLowSurrogate(c)) throw invalid();
            if (c == '\n' || c == '\r' || c == '\t') {
                if (!lineBreaks) throw invalid();
                continue;
            }
            if (Character.isISOControl(c) || isBidiControl(c) || c == '﻿') throw invalid();
        }
        return text;
    }

    private static boolean isBidiControl(char c) {
        return (c >= '‪' && c <= '‮') || (c >= '⁦' && c <= '⁩') || c == '‎' || c == '‏'
                || c == '؜';
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED);
    }
}
