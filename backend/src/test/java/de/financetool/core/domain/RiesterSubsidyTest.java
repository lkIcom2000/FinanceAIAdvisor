package de.financetool.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** FR-20: the Riester allowances from the Rule Parameter values passed in. */
class RiesterSubsidyTest {

    static final Map<String, RuleParameterValue> RULES = Map.of(
            RiesterSubsidy.BASIC, rule(RiesterSubsidy.BASIC, "175", "2018"),
            RiesterSubsidy.CHILD_NEW, rule(RiesterSubsidy.CHILD_NEW, "300", "2008"),
            RiesterSubsidy.CHILD_OLD, rule(RiesterSubsidy.CHILD_OLD, "185", "2008"),
            RiesterSubsidy.RATE, rule(RiesterSubsidy.RATE, "0.04", "2008"),
            RiesterSubsidy.FLOOR, rule(RiesterSubsidy.FLOOR, "60", "2005"),
            RiesterSubsidy.CAP, rule(RiesterSubsidy.CAP, "2100", "2008"));

    static RuleParameterValue rule(String key, String value, String version) {
        return new RuleParameterValue(key, new BigDecimal(value), version, LocalDate.of(2005, 1, 1),
                LocalDate.of(2026, 12, 31));
    }

    static BigDecimal output(CoreComputation computation, String key) {
        return computation.outputs().stream().filter(f -> f.outputKey().equals(key)).findFirst().orElseThrow()
                .value();
    }

    @Test
    void twoChildrenBornAfter2008GiveTheFullAllowancesAndTheOwnContributionAboveTheFloor() {
        CoreComputation result = RiesterSubsidy.compute(new BigDecimal("24000"), List.of(2014, 2010), RULES);

        assertThat(output(result, RiesterSubsidy.BASIC_ALLOWANCE)).isEqualByComparingTo("175");
        assertThat(output(result, RiesterSubsidy.CHILD_ALLOWANCES)).isEqualByComparingTo("600");
        assertThat(output(result, RiesterSubsidy.TOTAL_ALLOWANCES)).isEqualByComparingTo("775");
        assertThat(output(result, RiesterSubsidy.MIN_OWN_CONTRIBUTION)).isEqualByComparingTo("185");
        assertThat(output(result, RiesterSubsidy.ELIGIBLE_CHILDREN)).isEqualByComparingTo("2");
        assertThat(result.ruleParamVersions()).containsEntry(RiesterSubsidy.BASIC, "2018")
                .containsEntry(RiesterSubsidy.FLOOR, "2005").hasSize(6);
        assertThat(result.keyInputs()).containsEntry("childrenBirthYears", List.of(2010, 2014));
    }

    @Test
    void aLowIncomeOwesTheFloorAndAnOlderChildGetsTheLowerAllowance() {
        CoreComputation result = RiesterSubsidy.compute(new BigDecimal("10000"), List.of(2005), RULES);

        assertThat(output(result, RiesterSubsidy.CHILD_ALLOWANCES)).isEqualByComparingTo("185");
        assertThat(output(result, RiesterSubsidy.MIN_OWN_CONTRIBUTION)).isEqualByComparingTo("60");
    }

    @Test
    void theOwnContributionIsCappedByTheSpecialExpenseMaximum() {
        CoreComputation result = RiesterSubsidy.compute(new BigDecimal("100000"), List.of(), RULES);

        assertThat(output(result, RiesterSubsidy.MIN_OWN_CONTRIBUTION)).isEqualByComparingTo("1925");
    }

    @Test
    void aMissingRuleOrANegativeIncomeIsRefused() {
        Map<String, RuleParameterValue> missing = new HashMap<>(RULES);
        missing.remove(RiesterSubsidy.CAP);

        assertThatThrownBy(() -> RiesterSubsidy.compute(BigDecimal.TEN, List.of(), missing))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RiesterSubsidy.compute(new BigDecimal("-1"), List.of(), RULES))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
