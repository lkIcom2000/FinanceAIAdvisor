package de.financetool.advisory.adapter;

import de.financetool.advisory.domain.ChatMessage;
import de.financetool.advisory.domain.PromptPayload;
import de.financetool.advisory.domain.ResponseOutcome;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.domain.SourcedNumber;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The advisory tables {@code conversations}, {@code messages} and {@code dialog_states}. Every read and write takes a
 * {@link PersonaScope} (AD-11); only persona reset deletes by persona id.
 */
@Repository
public class ChatRepository {

    private static final String MESSAGE_COLUMNS = "id, author, text, outcome, reply_to, suggestions, checks::text AS checks, "
            + "proposal::text AS proposal, figures::text AS figures, premise";

    /** The dialog state while a parameter proposal is open (AD-24). */
    public static final String PARAMETER_PROPOSAL = "PARAMETER_PROPOSAL";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<ChatMessage.Check>> CHECKS = new TypeReference<>() {
    };
    private static final TypeReference<List<SourcedNumber>> FIGURES = new TypeReference<>() {
    };

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
                INSERT INTO messages (id, conversation_id, persona_id, author, text, outcome, reply_to, suggestions,
                    checks, proposal, figures, premise)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?)""")
                .params(message.id(), conversationId, scope.id(), message.author().name(), message.text(),
                        message.outcome() == null ? null : message.outcome().name(), message.replyTo(),
                        message.suggestions().toArray(String[]::new), JSON.writeValueAsString(message.checks()),
                        message.proposal() == null ? null : JSON.writeValueAsString(message.proposal()),
                        JSON.writeValueAsString(message.figures()), message.premise())
                .update();
    }

    /** Sets the status of the scope's proposal message; returns whether it was {@code from} and is now changed. */
    public boolean updateProposalStatus(PersonaScope scope, UUID messageId, ChatMessage.Proposal.Status from,
            ChatMessage.Proposal.Status to) {
        return jdbc.sql("""
                UPDATE messages SET proposal = jsonb_set(proposal, '{status}', to_jsonb(?::text))
                WHERE id = ? AND persona_id = ? AND proposal ->> 'status' = ?""")
                .params(to.name(), messageId, scope.id(), from.name())
                .update() == 1;
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

    /** The proposal message the persona's {@code PARAMETER_PROPOSAL} dialog state holds, if it is in that state. */
    public Optional<UUID> openProposal(PersonaScope scope) {
        return jdbc.sql("SELECT payload ->> 'proposalId' FROM dialog_states WHERE persona_id = ? AND state = ?")
                .params(scope.id(), PARAMETER_PROPOSAL)
                .query(String.class)
                .optional()
                .map(UUID::fromString);
    }

    /** Stores the persona's dialog state: {@code PARAMETER_PROPOSAL} with the proposal's id, or {@code NONE}. */
    public void saveDialogState(PersonaScope scope, UUID conversationId, UUID proposalId) {
        jdbc.sql("""
                INSERT INTO dialog_states (persona_id, conversation_id, state, payload)
                VALUES (?, ?, ?, ?::jsonb)
                ON CONFLICT (persona_id) DO UPDATE SET state = EXCLUDED.state, payload = EXCLUDED.payload""")
                .params(scope.id(), conversationId, proposalId == null ? "NONE" : PARAMETER_PROPOSAL,
                        proposalId == null ? null : JSON.writeValueAsString(Map.of("proposalId", proposalId)))
                .update();
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
                rs.getObject("reply_to", UUID.class), suggestions(rs.getArray("suggestions")),
                JSON.readValue(rs.getString("checks"), CHECKS), null,
                rs.getString("proposal") == null ? null
                        : JSON.readValue(rs.getString("proposal"), ChatMessage.Proposal.class),
                JSON.readValue(rs.getString("figures"), FIGURES), rs.getString("premise"));
    }

    private static List<String> suggestions(Array array) throws SQLException {
        return array == null ? List.of() : List.of((String[]) array.getArray());
    }
}
