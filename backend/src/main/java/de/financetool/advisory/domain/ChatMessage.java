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
 */
public record ChatMessage(UUID id, Author author, String text, ResponseOutcome outcome, UUID replyTo,
        List<String> suggestions) {

    /** At most three chips per card (UX). */
    public static final int MAX_SUGGESTIONS = 3;

    public enum Author {
        USER, AGENT, ADVISOR
    }

    public ChatMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(text, "text");
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        if (suggestions.size() > MAX_SUGGESTIONS) {
            throw new IllegalArgumentException("At most " + MAX_SUGGESTIONS + " suggestions per message");
        }
    }

    /** A message without suggestion chips. */
    public ChatMessage(UUID id, Author author, String text, ResponseOutcome outcome, UUID replyTo) {
        this(id, author, text, outcome, replyTo, List.of());
    }
}
