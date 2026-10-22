package de.financetool.advisory.adapter;

import de.financetool.advisory.domain.ChatMessage;
import de.financetool.advisory.domain.PromptPayload;
import de.financetool.advisory.domain.ResponseOutcome;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.PersonaId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The advisory tables {@code conversations}, {@code messages} and {@code dialog_states}. Every read and write takes a
 * {@link PersonaScope} (AD-11); only persona reset deletes by persona id.
 */
@Repository
public class ChatRepository {

    private static final String MESSAGE_COLUMNS = "id, author, text, outcome, reply_to";

    private final JdbcClient jdbc;

    ChatRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The persona's conversation, created on first use. */
    public UUID conversation(PersonaScope scope) {
        jdbc.sql("INSERT INTO conversations (id, persona_id) VALUES (?, ?) ON CONFLICT (persona_id) DO NOTHING")
                .params(UUID.randomUUID(), scope.id())
                .update();
        return jdbc.sql("SELECT id FROM conversations WHERE persona_id = ?")
                .param(scope.id())
                .query(UUID.class)
                .single();
    }

    public void insert(PersonaScope scope, UUID conversationId, ChatMessage message) {
        jdbc.sql("""
                INSERT INTO messages (id, conversation_id, persona_id, author, text, outcome, reply_to)
                VALUES (?, ?, ?, ?, ?, ?, ?)""")
                .params(message.id(), conversationId, scope.id(), message.author().name(), message.text(),
                        message.outcome() == null ? null : message.outcome().name(), message.replyTo())
                .update();
    }

    /** The message, only if it belongs to the scope's persona. */
    public Optional<ChatMessage> find(PersonaScope scope, UUID id) {
        return jdbc.sql("SELECT " + MESSAGE_COLUMNS + " FROM messages WHERE id = ? AND persona_id = ?")
                .params(id, scope.id())
                .query(ChatRepository::message)
                .optional();
    }

    /** The agent message answering {@code messageId}, if the turn was released. */
    public Optional<ChatMessage> findReply(PersonaScope scope, UUID messageId) {
        return jdbc.sql("SELECT " + MESSAGE_COLUMNS + " FROM messages WHERE reply_to = ? AND persona_id = ?")
                .params(messageId, scope.id())
                .query(ChatRepository::message)
                .optional();
    }

    /** The persona's messages in the order they were written. */
    public List<ChatMessage> messages(PersonaScope scope) {
        return jdbc.sql("SELECT " + MESSAGE_COLUMNS + " FROM messages WHERE persona_id = ? ORDER BY seq")
                .param(scope.id())
                .query(ChatRepository::message)
                .list();
    }

    /**
     * The last {@code limit} released turns before the user message {@code messageId}, oldest first: each user
     * message with the agent message answering it. Unanswered questions and advisor messages are not turns.
     */
    public List<PromptPayload.HistoryTurn> releasedTurnsBefore(PersonaScope scope, UUID messageId, int limit) {
        List<PromptPayload.HistoryTurn> turns = new ArrayList<>(jdbc.sql("""
                SELECT q.text AS user_text, a.text AS agent_text
                FROM messages q
                JOIN messages a ON a.reply_to = q.id AND a.persona_id = q.persona_id AND a.author = 'AGENT'
                WHERE q.persona_id = ? AND q.author = 'USER'
                  AND q.seq < (SELECT c.seq FROM messages c WHERE c.id = ? AND c.persona_id = ?)
                ORDER BY q.seq DESC
                LIMIT ?""")
                .params(scope.id(), messageId, scope.id(), limit)
                .query((rs, row) -> new PromptPayload.HistoryTurn(rs.getString("user_text"),
                        rs.getString("agent_text")))
                .list());
        return turns.reversed();
    }

    /** The persona's dialog state, if one is stored. */
    public Optional<String> dialogState(PersonaScope scope) {
        return jdbc.sql("SELECT state FROM dialog_states WHERE persona_id = ?")
                .param(scope.id())
                .query(String.class)
                .optional();
    }

    /** Persona reset: deletes the persona's dialog states, messages and conversation; returns the deleted rows. */
    public int deletePersonaRows(PersonaId personaId) {
        int deleted = 0;
        for (String table : List.of("dialog_states", "messages", "conversations")) {
            deleted += jdbc.sql("DELETE FROM " + table + " WHERE persona_id = ?").param(personaId.value()).update();
        }
        return deleted;
    }

    private static ChatMessage message(ResultSet rs, int row) throws SQLException {
        String outcome = rs.getString("outcome");
        return new ChatMessage(rs.getObject("id", UUID.class), ChatMessage.Author.valueOf(rs.getString("author")),
                rs.getString("text"), outcome == null ? null : ResponseOutcome.valueOf(outcome),
                rs.getObject("reply_to", UUID.class));
    }
}
