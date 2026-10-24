package de.financetool.advisory.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One stored chat message.
 *
 * @param outcome the turn's outcome; only agent messages have one
 * @param replyTo the user message an agent message answers; {@code null} for user messages
 * @param suggestions the suggestion chips of an agent message (0–3 question texts); empty for other messages
 * @param checks the applicable final checks of an agent message, shown under "Checks"; empty for other messages and
 *     for an answer that is a fixed template
 * @param handover the case an {@code ESCALATE} answer opened for a human advisor; {@code null} for other messages
 */
public record ChatMessage(UUID id, Author author, String text, ResponseOutcome outcome, UUID replyTo,
        List<String> suggestions, List<Check> checks, Handover handover) {

    /** At most three chips per card (UX). */
    public static final int MAX_SUGGESTIONS = 3;

    public enum Author {
        USER, AGENT, ADVISOR
    }

    /** One check result as the card shows it: tick or cross, the plain description, the id as a caption. */
    public record Check(CheckId id, boolean passed, String description) {

        public Check {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(description, "description");
        }

        /** The card's view of an applicable check result. */
        public static Check of(CheckResult result) {
            return new Check(result.checkId(), result.passed(), result.checkId().description());
        }
    }

    /**
     * What the card shows of an {@code ESCALATE} answer's case: never the draft or the technical block reasons.
     *
     * @param reason the plain reason class, e.g. "because I couldn't confirm every figure in my answer"; {@code null}
     *     for none
     * @param status the case's status
     */
    public record Handover(String reason, Status status) {

        public enum Status {
            OPEN, CLOSED
        }

        public Handover {
            Objects.requireNonNull(status, "status");
        }
    }

    public ChatMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(text, "text");
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        checks = checks == null ? List.of() : List.copyOf(checks);
        if (suggestions.size() > MAX_SUGGESTIONS) {
            throw new IllegalArgumentException("At most " + MAX_SUGGESTIONS + " suggestions per message");
        }
    }

    /** A message without a handover. */
    public ChatMessage(UUID id, Author author, String text, ResponseOutcome outcome, UUID replyTo,
            List<String> suggestions, List<Check> checks) {
        this(id, author, text, outcome, replyTo, suggestions, checks, null);
    }

    /** This message with the handover of its case. */
    public ChatMessage withHandover(Handover value) {
        return new ChatMessage(id, author, text, outcome, replyTo, suggestions, checks, value);
    }

    /** A message without checks. */
    public ChatMessage(UUID id, Author author, String text, ResponseOutcome outcome, UUID replyTo,
            List<String> suggestions) {
        this(id, author, text, outcome, replyTo, suggestions, List.of(), null);
    }

    /** A message without suggestion chips and checks. */
    public ChatMessage(UUID id, Author author, String text, ResponseOutcome outcome, UUID replyTo) {
        this(id, author, text, outcome, replyTo, List.of(), List.of(), null);
    }
}
