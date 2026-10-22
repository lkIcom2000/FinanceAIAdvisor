package de.financetool.advisory.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * One stored chat message.
 *
 * @param outcome the turn's outcome; only agent messages have one
 * @param replyTo the user message an agent message answers; {@code null} for user messages
 */
public record ChatMessage(UUID id, Author author, String text, ResponseOutcome outcome, UUID replyTo) {

    public enum Author {
        USER, AGENT, ADVISOR
    }

    public ChatMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(text, "text");
    }
}
