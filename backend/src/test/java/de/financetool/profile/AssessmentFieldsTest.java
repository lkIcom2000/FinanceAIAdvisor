package de.financetool.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Story 6.2: the assessment card's fields and how an answer becomes a stored value. */
class AssessmentFieldsTest {

    @Test
    void everyStrictFieldHasTheInputThatFitsIt() {
        assertThat(List.of("goal", "loss_bearing_capacity", "knowledge_experience", "sustainability_preference"))
                .allSatisfy(f -> assertThat(AssessmentFields.of(f).orElseThrow().input())
                        .isEqualTo(AssessmentFields.Input.CHOICE));
        assertThat(List.of("net_income", "monthly_expenses", "emergency_fund"))
                .allSatisfy(f -> assertThat(AssessmentFields.of(f).orElseThrow().input())
                        .isEqualTo(AssessmentFields.Input.EUR));
        assertThat(AssessmentFields.of("risk_tolerance").orElseThrow().input())
                .isEqualTo(AssessmentFields.Input.SLIDER);
        assertThat(AssessmentFields.of("investment_horizon_years").orElseThrow().unit()).isEqualTo("years");
    }

    @Test
    void answersBecomeTypedValues() {
        assertThat(AssessmentFields.parse("net_income", " 3200.5 ")).isEqualTo(new BigDecimal("3200.50"));
        assertThat(AssessmentFields.parse("investment_horizon_years", "20")).isEqualTo(20);
        assertThat(AssessmentFields.parse("risk_tolerance", "very low")).isEqualTo("very low");
        assertThat(AssessmentFields.parse("goal", "retirement")).isEqualTo("retirement");
        assertThat(AssessmentFields.parse("pension_situation", " statutory only ")).isEqualTo("statutory only");
    }

    @Test
    void answersThatDoNotFitAreRefusedWithAPlainSentence() {
        assertThatThrownBy(() -> AssessmentFields.parse("goal", "lottery"))
                .hasMessage("Please pick one of the choices for: What are you saving or investing for?.");
        assertThatThrownBy(() -> AssessmentFields.parse("net_income", "-1"))
                .hasMessageStartingWith("Please enter an amount between 0 and");
        assertThatThrownBy(() -> AssessmentFields.parse("net_income", "1e5"))
                .hasMessageStartingWith("Please enter a number");
        assertThatThrownBy(() -> AssessmentFields.parse("investment_horizon_years", "2.5"))
                .hasMessageStartingWith("Please enter a whole number from 0 to 60");
        assertThatThrownBy(() -> AssessmentFields.parse("investment_horizon_years", "61"))
                .hasMessageStartingWith("Please enter a whole number");
        assertThatThrownBy(() -> AssessmentFields.parse("pension_situation", "a\0b"))
                .hasMessageStartingWith("Please check your answer");
        assertThatThrownBy(() -> AssessmentFields.parse("pension_situation", "x".repeat(201)))
                .hasMessageContaining("under 200 characters");
        assertThatThrownBy(() -> AssessmentFields.parse("goal", " ")).hasMessageStartingWith("Please answer");
        assertThatThrownBy(() -> AssessmentFields.parse("salary_wish", "1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeStoresCardFieldsInParseFormAndKeepsOtherValues() {
        assertThat(AssessmentFields.normalize("net_income", 3200)).isEqualTo(new BigDecimal("3200.00"));
        assertThat(AssessmentFields.normalize("investment_horizon_years", 20L)).isEqualTo(20);
        assertThat(AssessmentFields.normalize("gross_income", new BigDecimal("40000"))).isEqualTo(
                new BigDecimal("40000.00"));
        assertThatThrownBy(() -> AssessmentFields.normalize("goal", List.of("retirement")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AssessmentFields.normalize("net_income", 3200.5d))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
