package de.financetool.advisory.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FR-52 block routing, the only implementation ({@code _route} of {@code experiments/finlab/pipeline.py} plus the
 * invalid-Rule-Parameter row), pure. Rows in order: invalid Rule Parameter, scope refusal, profile question, only
 * no valid source, Number Check, any other failure; without a failure the draft is released.
 */
public final class BlockRouting {

    public static final String RELEASED = "released";

    /**
     * Where a turn goes.
     *
     * @param outcome the routed outcome; {@code null} when the draft is released (the outcome is then the class)
     * @param route the route's name, e.g. {@code released}, {@code block_scope}, {@code no_valid_source}
     * @param text the template released instead of the draft; {@code null} when the draft is released
     * @param fired the checks whose failure decided the route, in {@link CheckId} order
     * @param noValidSource whether the text is the no-valid-source template (it comes with a suggestion chip)
     */
    public record Route(ResponseOutcome outcome, String route, String text, List<CheckId> fired,
            boolean noValidSource) {

        public Route {
            Objects.requireNonNull(route, "route");
            fired = List.copyOf(fired);
        }

        /** Whether the draft is released as it is. */
        public boolean released() {
            return outcome == null;
        }
    }

    private BlockRouting() {
    }

    /**
     * @param failed the applicable checks that did not pass on the final text
     * @param ruleParameterError whether a Rule Parameter needed by the turn is not valid (false until Story 3.7)
     */
    public static Route route(List<CheckResult> failed, Classification classification, boolean ruleParameterError) {
        return route(failed, classification, ruleParameterError, false);
    }

    /**
     * @param ruleForLaterYear whether the invalid Rule Parameter is one for a later year the message asks about
     */
    public static Route route(List<CheckResult> failed, Classification classification, boolean ruleParameterError,
            boolean ruleForLaterYear) {
        return route(failed, classification, ruleParameterError, ruleForLaterYear, Language.EN);
    }

    /**
     * @param language the turn's answer language: the template released is in it (Story 13.1)
     */
    public static Route route(List<CheckResult> failed, Classification classification, boolean ruleParameterError,
            boolean ruleForLaterYear, Language language) {
        List<CheckId> fired = failed.stream().map(CheckResult::checkId).distinct().sorted().toList();
        if (ruleParameterError) {
            return new Route(ResponseOutcome.ESCALATE,
                    ruleForLaterYear ? "rule_not_confirmed_next_year" : "invalid_rule_parameter",
                    ResponseTemplates.escalate(language), fired, false);
        }
        if (failed.isEmpty()) {
            return new Route(null, RELEASED, null, List.of(), false);
        }
        Set<CheckId> ids = new LinkedHashSet<>(fired);
        if (classification.messageClass() == MessageClass.REFUSE_OUT_OF_SCOPE && ids.contains(CheckId.SCOPE)) {
            return new Route(ResponseOutcome.REFUSE_OUT_OF_SCOPE, "block_scope",
                    ResponseTemplates.refuseOutOfScope(classification.outOfScope(), language), fired, false);
        }
        if (ids.contains(CheckId.PROFILE_COMPLETE)) {
            return new Route(ResponseOutcome.ASK_PROFILE, "block_profile",
                    ResponseTemplates.askProfile(classification.missingFields(), language), fired, false);
        }
        if (ids.equals(Set.of(CheckId.SOURCE_VALIDITY)) && failed.getFirst().reasonCode().equals("no_valid_source")) {
            return new Route(ResponseOutcome.INFORM, "no_valid_source", ResponseTemplates.noValidSource(language), fired,
                    true);
        }
        if (ids.contains(CheckId.NUMBER_MATCH)) {
            return new Route(ResponseOutcome.ESCALATE, "number_mismatch_after_regeneration",
                    ResponseTemplates.escalate(language), fired, false);
        }
        // sorted by name, as Python's sorted() over the id strings
        String names = fired.stream().map(CheckId::name).sorted().map(n -> n.toLowerCase(java.util.Locale.ROOT))
                .collect(Collectors.joining("_"));
        return new Route(ResponseOutcome.ESCALATE, "block_" + names, ResponseTemplates.escalate(language), fired, false);
    }
}
