package de.financetool.advisory.domain;

import de.financetool.shared.domain.SimulationSpec;
import de.financetool.shared.domain.SourcedNumber;
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
 * @param proposal the parameter proposal a {@code COMPUTE} answer shows before calculating; {@code null} otherwise
 * @param figures the Core figures a calculated answer used, shown as figure chips; empty for other messages
 * @param premise the premise callout of a {@code DETECT_TRAP} answer: one sentence naming the unverified premise;
 *     {@code null} for other messages
 * @param assessment the assessment card an {@code ASK_PROFILE} answer starts (AD-24); {@code null} otherwise
 * @param recommendation the Suitability Statement of a {@code RECOMMEND_CATEGORY} answer (UX-DR43); {@code null}
 *     otherwise
 */
public record ChatMessage(UUID id, Author author, String text, ResponseOutcome outcome, UUID replyTo,
        List<String> suggestions, List<Check> checks, Handover handover, Proposal proposal,
        List<SourcedNumber> figures, String premise, Assessment assessment, Recommendation recommendation) {

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

    /**
     * "Here's what I understood": the parameters a calculation will run with (AD-24).
     *
     * @param status {@code OPEN} until the persona calculates it or asks something else
     */
    public record Proposal(SimulationSpec spec, Status status) {

        public enum Status {
            OPEN, CALCULATED, CANCELLED
        }

        public Proposal {
            Objects.requireNonNull(spec, "spec");
            Objects.requireNonNull(status, "status");
        }
    }

    /**
     * The assessment card: every profile field still missing, in one form (UX-DR14). Answers are stored only on Save
     * through the profile confirm control.
     *
     * @param fields the fields in card order
     * @param status {@code OPEN} until the persona saves it or asks something else
     */
    public record Assessment(List<Field> fields, Status status) {

        public enum Status {
            OPEN, SAVED, CANCELLED
        }

        /**
         * One field of the card.
         *
         * @param key the profile field
         * @param value the value pre-filled from account data as a decimal string; {@code null} for a field to answer
         * @param provId the Core figure a pre-filled value is; {@code null} without one
         */
        public record Field(String key, String value, String provId) {

            public Field {
                Objects.requireNonNull(key, "key");
                if ((value == null) != (provId == null)) {
                    throw new IllegalArgumentException("a pre-filled value is a Core figure");
                }
            }
        }

        public Assessment {
            fields = List.copyOf(fields);
            Objects.requireNonNull(status, "status");
            if (fields.isEmpty()) {
                throw new IllegalArgumentException("an assessment asks for at least one field");
            }
        }

        /** The card's field keys, in card order. */
        public List<String> keys() {
            return fields.stream().map(Field::key).toList();
        }
    }

    /**
     * The Suitability Statement of a Category Recommendation, built from Core results and the profile only; the card
     * shows it in the collapsed "Why this fits you" panel.
     *
     * @param category the category key of the Core selection
     * @param label the category in plain words
     * @param conflicts where the request conflicts with the profile; empty if none
     * @param facts the profile facts the selection used
     * @param figures the selection's figures (surplus, emergency fund and its target, requested amount)
     * @param risks the category's main risks
     * @param costs the ex-ante cost figures; empty for a topic without investment
     */
    public record Recommendation(String category, String label, List<String> conflicts, List<Fact> facts,
            List<SourcedNumber> figures, List<String> risks, List<SourcedNumber> costs) {

        /** One profile fact: the field in plain words and its value as shown. */
        public record Fact(String label, String value) {

            public Fact {
                Objects.requireNonNull(label, "label");
                Objects.requireNonNull(value, "value");
            }
        }

        public Recommendation {
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(label, "label");
            conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
            facts = facts == null ? List.of() : List.copyOf(facts);
            figures = figures == null ? List.of() : List.copyOf(figures);
            risks = risks == null ? List.of() : List.copyOf(risks);
            costs = costs == null ? List.of() : List.copyOf(costs);
        }
    }

    public ChatMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(text, "text");
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        checks = checks == null ? List.of() : List.copyOf(checks);
        figures = figures == null ? List.of() : List.copyOf(figures);
        if (suggestions.size() > MAX_SUGGESTIONS) {
            throw new IllegalArgumentException("At most " + MAX_SUGGESTIONS + " suggestions per message");
        }
    }

    /** A message without a Suitability Statement. */
    public ChatMessage(UUID id, Author author, String text, ResponseOutcome outcome, UUID replyTo,
            List<String> suggestions, List<Check> checks, Handover handover, Proposal proposal,
            List<SourcedNumber> figures, String premise, Assessment assessment) {
        this(id, author, text, outcome, replyTo, suggestions, checks, handover, proposal, figures, premise,
                assessment, null);
    }

    /** A message without an assessment card. */
    public ChatMessage(UUID id, Author author, String text, ResponseOutcome outcome, UUID replyTo,
            List<String> suggestions, List<Check> checks, Handover handover, Proposal proposal,
            List<SourcedNumber> figures, String premise) {
        this(id, author, text, outcome, replyTo, suggestions, checks, handover, proposal, figures, premise, null);
    }

    /** A message without a premise callout. */
    public ChatMessage(UUID id, Author author, String text, ResponseOutcome outcome, UUID replyTo,
            List<String> suggestions, List<Check> checks, Handover handover, Proposal proposal,
            List<SourcedNumber> figures) {
        this(id, author, text, outcome, replyTo, suggestions, checks, handover, proposal, figures, null);
    }

    /** A message without a proposal and figures. */
    public ChatMessage(UUID id, Author author, String text, ResponseOutcome outcome, UUID replyTo,
            List<String> suggestions, List<Check> checks, Handover handover) {
        this(id, author, text, outcome, replyTo, suggestions, checks, handover, null, List.of());
    }

    /** A message without a handover, a proposal and figures. */
    public ChatMessage(UUID id, Author author, String text, ResponseOutcome outcome, UUID replyTo,
            List<String> suggestions, List<Check> checks) {
        this(id, author, text, outcome, replyTo, suggestions, checks, null);
    }

    /** This message with the handover of its case. */
    public ChatMessage withHandover(Handover value) {
        return new ChatMessage(id, author, text, outcome, replyTo, suggestions, checks, value, proposal, figures,
                premise, assessment, recommendation);
    }

    /** This proposal message with a new status. */
    public ChatMessage withProposalStatus(Proposal.Status value) {
        Objects.requireNonNull(proposal, "proposal");
        return new ChatMessage(id, author, text, outcome, replyTo, suggestions, checks, handover,
                new Proposal(proposal.spec(), value), figures, premise, assessment, recommendation);
    }

    /** This assessment message with a new status. */
    public ChatMessage withAssessmentStatus(Assessment.Status value) {
        Objects.requireNonNull(assessment, "assessment");
        return new ChatMessage(id, author, text, outcome, replyTo, suggestions, checks, handover, proposal, figures,
                premise, new Assessment(assessment.fields(), value), recommendation);
    }

    /** A message without checks. */
    public ChatMessage(UUID id, Author author, String text, ResponseOutcome outcome, UUID replyTo,
            List<String> suggestions) {
        this(id, author, text, outcome, replyTo, suggestions, List.of());
    }

    /** A message without suggestion chips and checks. */
    public ChatMessage(UUID id, Author author, String text, ResponseOutcome outcome, UUID replyTo) {
        this(id, author, text, outcome, replyTo, List.of(), List.of());
    }
}
