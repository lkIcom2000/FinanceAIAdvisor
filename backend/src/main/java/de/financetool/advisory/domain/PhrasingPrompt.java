package de.financetool.advisory.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The phrasing role's prompt (AD-5), mirroring {@code _system_prompt}, {@code _user_prompt} and {@code _fit_budget}
 * of {@code experiments/finlab/pipeline.py}: the system part holds the versioned prompt files, everything from the
 * user, the history, the Core and the Knowledge Base goes into the user part as data. The one exception is the class
 * instruction's premise claims (from the NLU or the message): {@link ClassInstruction} passes them through the
 * exposure filter at the turn's level and strips slot markers and braces before filling them in.
 *
 * @param system the system part: role prompt, compliance rules, then the class instruction
 * @param user the user part: tool results, sources, profile, earlier conversation, then the message slot
 * @param droppedHistoryTurns how many history turns the budget dropped (oldest first)
 * @param droppedPassages how many Knowledge Base passages the budget dropped (last first)
 * @param overBudget whether the prompt is still over budget after every allowed drop
 */
public record PhrasingPrompt(String system, String user, int droppedHistoryTurns, int droppedPassages,
        boolean overBudget) {

    public static final String SLOT_START = "<user_message>";
    public static final String SLOT_END = "</user_message>";

    /** Tokens kept free besides {@code numPredict}, as in {@code _fit_budget}. */
    static final int RESERVE_TOKENS = 200;
    /** The token estimate: characters / 3.2. */
    static final double CHARS_PER_TOKEN = 3.2;

    private static final Pattern SLOT_MARKER = Pattern.compile("(?i)<\\s*/?\\s*user_message\\s*>");
    private static final Pattern LINE_BREAKS = Pattern.compile("\\s*\\R\\s*");

    public PhrasingPrompt {
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(user, "user");
    }

    /** Whether the budget dropped anything or could not be kept. */
    public boolean truncated() {
        return droppedHistoryTurns > 0 || droppedPassages > 0 || overBudget;
    }

    /**
     * Builds the prompt within {@code numCtx − numPredict − 200} estimated tokens. Over budget, history turns are
     * dropped oldest first, then passages last first; the system part, the Core results and the message are never
     * dropped, even if the prompt stays over budget ({@code overBudget}).
     *
     * @param rolePrompt the role prompt file's text
     * @param complianceRules the compliance rules file's text (every compliance level includes it)
     * @throws IllegalArgumentException if {@code numCtx} leaves no budget ({@code numCtx <= numPredict + 200})
     */
    public static PhrasingPrompt build(String rolePrompt, String complianceRules, PromptPayload payload, int numCtx,
            int numPredict) {
        return build(rolePrompt, complianceRules, "", payload, numCtx, numPredict);
    }

    /**
     * Like {@link #build(String, String, PromptPayload, int, int)}, with the class instruction (layer A) appended to
     * the system part.
     *
     * @param classInstruction the filled class instruction, or blank for none
     */
    public static PhrasingPrompt build(String rolePrompt, String complianceRules, String classInstruction,
            PromptPayload payload, int numCtx, int numPredict) {
        String system = rolePrompt.strip() + "\n\n" + complianceRules.strip()
                + (classInstruction.isBlank() ? "" : "\n\n" + classInstruction.strip());
        double budget = numCtx - numPredict - RESERVE_TOKENS;
        if (budget <= 0) {
            throw new IllegalArgumentException("numCtx " + numCtx + " leaves no prompt budget beside numPredict "
                    + numPredict + " and " + RESERVE_TOKENS + " reserved tokens");
        }
        List<PromptPayload.HistoryTurn> history = new ArrayList<>(payload.history());
        List<PromptPayload.Passage> passages = new ArrayList<>(payload.passages());
        while (true) {
            String user = userPart(payload, history, passages);
            if (tokens(system, user) <= budget) {
                return result(system, user, payload, history, passages, false);
            }
            if (!history.isEmpty()) {
                history.removeFirst();
            } else if (!passages.isEmpty()) {
                passages.removeLast();
            } else {
                return result(system, user, payload, history, passages, true);
            }
        }
    }

    private static PhrasingPrompt result(String system, String user, PromptPayload payload,
            List<PromptPayload.HistoryTurn> history, List<PromptPayload.Passage> passages, boolean overBudget) {
        return new PhrasingPrompt(system, user, payload.history().size() - history.size(),
                payload.passages().size() - passages.size(), overBudget);
    }

    static double tokens(String system, String user) {
        return (system.codePointCount(0, system.length()) + user.codePointCount(0, user.length())) / CHARS_PER_TOKEN;
    }

    private static String userPart(PromptPayload payload, List<PromptPayload.HistoryTurn> history,
            List<PromptPayload.Passage> passages) {
        List<String> blocks = new ArrayList<>();
        if (!payload.toolResults().isBlank()) {
            blocks.add("TOOL RESULTS (deterministic calculations; use these numbers exactly):\n"
                    + data(payload.toolResults()));
        }
        if (!passages.isEmpty()) {
            StringBuilder sources = new StringBuilder("SOURCES:");
            for (int i = 0; i < passages.size(); i++) {
                PromptPayload.Passage passage = passages.get(i);
                sources.append("\n[S").append(i + 1).append("] ").append(data(passage.citation())).append(": ")
                        .append(data(passage.text()));
            }
            blocks.add(sources.toString());
        }
        if (!payload.profile().isEmpty()) {
            blocks.add("CUSTOMER PROFILE ON FILE:\n" + data(json(payload.profile())));
        }
        if (!history.isEmpty()) {
            StringBuilder earlier = new StringBuilder("EARLIER CONVERSATION (oldest first):");
            for (PromptPayload.HistoryTurn turn : history) {
                earlier.append("\nCustomer: ").append(oneLine(data(turn.userText())))
                        .append("\nAssistant: ").append(oneLine(data(turn.agentText())));
            }
            blocks.add(earlier.toString());
        }
        blocks.add(SLOT_START + "\n" + data(payload.message()) + "\n" + SLOT_END);
        return String.join("\n\n", blocks);
    }

    /** Text for the data part with every slot marker removed, so it cannot open or close the message slot. */
    public static String data(String text) {
        String out = text;
        String previous;
        do {
            previous = out;
            out = SLOT_MARKER.matcher(out).replaceAll("");
        } while (!out.equals(previous));
        return out;
    }

    /**
     * A history text on one line: the history block is line-oriented, so a line break followed by
     * {@code Assistant:} or a block header in an earlier message would fake the prompt's structure (Story 3.8).
     */
    static String oneLine(String text) {
        return LINE_BREAKS.matcher(text.strip()).replaceAll(" ");
    }

    /** The profile as compact JSON ({@code json.dumps} with {@code ensure_ascii=False}). */
    static String json(Map<String, Object> profile) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : profile.entrySet()) {
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append(quote(entry.getKey())).append(": ").append(value(entry.getValue()));
        }
        return out.append('}').toString();
    }

    private static String value(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof List<?> list) {
            List<String> items = list.stream().map(PhrasingPrompt::value).toList();
            return "[" + String.join(", ", items) + "]";
        }
        return quote(value.toString());
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
