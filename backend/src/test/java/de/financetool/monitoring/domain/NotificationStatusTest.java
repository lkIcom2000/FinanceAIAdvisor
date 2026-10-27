package de.financetool.monitoring.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.actions.domain.ActionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Story 8.3: a Notification's status chip from its outcome and its Action's state (AD-10). */
class NotificationStatusTest {

    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {
            "INFORMED,    -,                     FOR_YOUR_INFORMATION",
            "RECOMMENDED, -,                     RECOMMENDATION",
            "ESCALATED,   -,                     WITH_AN_ADVISOR",
            "ACTION,      INFORMED,              FOR_YOUR_INFORMATION",
            "ACTION,      RECOMMENDED,           RECOMMENDATION",
            "ACTION,      AWAITING_CONFIRMATION, NEEDS_YOUR_OK",
            "ACTION,      EXECUTED,              DONE_FOR_YOU",
            "ACTION,      ESCALATED,             WITH_AN_ADVISOR"})
    void statusFollowsTheOutcomeAndTheActionState(NotificationOutcome outcome, ActionState state,
            NotificationStatus expected) {
        assertThat(NotificationStatus.of(outcome, state)).isEqualTo(expected);
    }

    @Test
    void anActionNotificationNeedsItsActionState() {
        assertThatThrownBy(() -> NotificationStatus.of(NotificationOutcome.ACTION, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
