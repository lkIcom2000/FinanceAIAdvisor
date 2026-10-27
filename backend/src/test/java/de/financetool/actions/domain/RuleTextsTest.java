package de.financetool.actions.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Story 9.1: the customer texts and limit checks of a rule. */
class RuleTextsTest {

    static RuleBounds bounds(String perAction, String perMonth, String floor) {
        return new RuleBounds(new BigDecimal(perAction), new BigDecimal(perMonth), new BigDecimal(floor));
    }

    @Test
    void lenasCushionReadsAsThePlainLanguageSummary() {
        RuleBounds b = bounds("300", "600", "2000.00");

        assertThat(RuleTexts.summary(RuleTemplate.SAFETY_CUSHION, b)).isEqualTo("Keep my current account above"
                + " 0 EUR, using my overnight deposit, but never let the overnight deposit fall below 2,000 EUR");
        assertThat(RuleTexts.will(RuleTemplate.SAFETY_CUSHION, b))
                .contains("Move at most 300 EUR per transfer and 600 EUR per month.");
        assertThat(RuleTexts.wont(RuleTemplate.SAFETY_CUSHION, b)).first()
                .isEqualTo("Take your overnight deposit below 2,000 EUR.");
        assertThat(RuleTexts.wont(RuleTemplate.EMERGENCY_FUND_TOP_UP, bounds("250.50", "250.50", "0")))
                .first().isEqualTo("Take your current account below 0 EUR.");
        assertThat(RuleTexts.will(RuleTemplate.EMERGENCY_FUND_TOP_UP, bounds("250.50", "250.50", "0")))
                .contains("Move at most 250.50 EUR per transfer and 250.50 EUR per month.");
    }

    @Test
    void customerTextNeverSaysMandate() {
        RuleBounds b = bounds("300", "600", "2000");
        for (RuleTemplate t : RuleTemplate.values()) {
            List<String> texts = Stream.of(List.of(RuleTexts.summary(t, b), RuleTexts.source(t), t.title(),
                    RuleTexts.UNDO_AND_STOP), RuleTexts.will(t, b), RuleTexts.wont(t, b)).flatMap(List::stream).toList();
            assertThat(texts).noneMatch(s -> s.toLowerCase().contains("mandate"));
        }
    }

    @Test
    void limitsAreChecked() {
        assertThat(RuleTexts.problem(bounds("300", "600", "2000"))).isEmpty();
        assertThat(RuleTexts.problem(bounds("300", "300", "0"))).isEmpty();
        assertThat(RuleTexts.problem(bounds("0", "600", "0"))).contains("Each limit must be more than 0 EUR.");
        assertThat(RuleTexts.problem(bounds("300", "600", "-1")))
                .contains("The minimum balance can't be below 0 EUR.");
        assertThat(RuleTexts.problem(bounds("300", "200", "0")))
                .contains("The most per month can't be less than the most per transfer.");
        assertThat(RuleTexts.problem(bounds("300.001", "600", "0"))).contains("Please use whole cents at most.");
        assertThat(RuleTexts.problem(bounds("300", "1000000.01", "0")))
                .contains("Each amount must be at most 1,000,000 EUR.");
        assertThat(RuleTexts.problem(bounds("300", "600", "1000000.01")))
                .contains("Each amount must be at most 1,000,000 EUR.");
    }
}
