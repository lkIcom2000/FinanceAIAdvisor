package de.financetool.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.financetool.core.domain.InsuranceContract.Cover;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The demands-and-needs statement (FR-18): needs level only, from the Needs Profile and the banking data. */
class DemandsAndNeedsTest {

    static final InsuranceContract OWN = new InsuranceContract("own", Cover.LIABILITY, "Jonas", false,
            new BigDecimal("48.00"), 12, LocalDate.of(2027, 2, 1));
    static final InsuranceContract FAMILY = new InsuranceContract("family", Cover.LIABILITY, "Parents of Jonas",
            true, null, null, null);
    static final InsuranceContract DISABILITY = new InsuranceContract("bu", Cover.OCCUPATIONAL_DISABILITY, "Jonas",
            false, new BigDecimal("60.00"), 1, LocalDate.of(2026, 11, 1));
    static final Map<String, Object> ALONE = Map.of("household", "single", "dependants", 0,
            "occupation", "Junior developer");

    static DemandsAndNeeds.Result of(Map<String, ?> profile, List<InsuranceContract> contracts) {
        return DemandsAndNeeds.of(profile, contracts, "p-jonas");
    }

    static String output(DemandsAndNeeds.Result result, String key) {
        return result.computation().outputs().stream().filter(f -> f.outputKey().equals(key)).findFirst()
                .orElseThrow().value().toPlainString();
    }

    @Test
    void jonasWithHisOwnHouseholdShouldCheckTheFamilyPolicyWithHisParents() {
        DemandsAndNeeds.Result result = of(ALONE, List.of(OWN, FAMILY));
        List<DemandsAndNeeds.Area> areas = result.statement().areas();

        assertThat(areas).extracting(DemandsAndNeeds.Area::area)
                .containsExactly("Liability", "Occupational disability", "Cover held twice");
        assertThat(areas.get(0).have())
                .isEqualTo("Your own policy, and you're co-insured on the policy of Parents of Jonas.");
        assertThat(areas.get(0).check()).startsWith("It's likely the family policy no longer covers you")
                .contains("Check with Parents of Jonas").contains("cancellation period of your own policy");
        assertThat(areas.get(1).have()).isEqualTo("No occupational disability contract in your banking data.");
        assertThat(areas.get(2).have()).isEqualTo(
                "Liability is covered by 2 contracts: your own policy and the policy of Parents of Jonas.");
        assertThat(output(result, DemandsAndNeeds.OUTPUT_LIABILITY)).isEqualTo("2");
        assertThat(output(result, DemandsAndNeeds.OUTPUT_DISABILITY)).isEqualTo("0");
        assertThat(output(result, DemandsAndNeeds.OUTPUT_DUPLICATES)).isEqualTo("1");
        assertThat(result.statement().profileFields()).containsExactly("household", "dependants", "occupation");
        assertThat(result.statement().factsCollected()).isTrue();
        assertThat(result.computation().function()).isEqualTo(DemandsAndNeeds.FUNCTION);
    }

    @Test
    void livingWithTheParentsTheFamilyPolicyMayStillCover() {
        DemandsAndNeeds.Area liability = of(Map.of("household", "parents", "dependants", 0, "occupation", "trainee"),
                List.of(OWN, FAMILY)).statement().areas().getFirst();
        assertThat(liability.check()).startsWith("While you live with Parents of Jonas, their family policy may "
                + "cover you too.").contains("before cancelling anything");
    }

    @Test
    void withoutContractsEveryAreaSaysSoAndNothingIsHeldTwice() {
        DemandsAndNeeds.Result result = of(ALONE, List.of());
        List<DemandsAndNeeds.Area> areas = result.statement().areas();
        assertThat(areas.get(0).have()).isEqualTo("No liability contract in your banking data.");
        assertThat(areas.get(0).check()).contains("first cover to look into");
        assertThat(areas.get(2).have()).isEqualTo("No risk is covered by more than one of your contracts.");
        assertThat(output(result, DemandsAndNeeds.OUTPUT_DUPLICATES)).isEqualTo("0");
    }

    @Test
    void dependantsMakeOccupationalDisabilityCoverMatterMore() {
        DemandsAndNeeds.Area disability = of(Map.of("household", "family", "dependants", "2", "occupation", "nurse"),
                List.of(DISABILITY)).statement().areas().get(1);
        assertThat(disability.need()).endsWith("This matters even more because 2 people depend on your income.");
        assertThat(disability.have()).isEqualTo("One occupational disability contract.");
        assertThat(of(Map.of("dependants", 1), List.of()).statement().areas().get(1).need())
                .endsWith("because 1 person depends on your income.");
    }

    @Test
    void missingFactsAreNotCollectedAndAnUnknownHouseholdOnlyAsksToCheck() {
        DemandsAndNeeds.Result result = of(Map.of("occupation", "nurse"), List.of(OWN, FAMILY));
        assertThat(result.statement().factsCollected()).isFalse();
        assertThat(of(Map.of("household", "single", "dependants", "a few", "occupation", "nurse"), List.of())
                .statement().factsCollected()).isFalse();
        assertThat(of(Map.of("household", "single", "dependants", "1.5", "occupation", "nurse"), List.of())
                .statement().factsCollected()).isFalse();
        assertThat(of(Map.of("household", " Parents ", "dependants", 0, "occupation", "nurse"), List.of(OWN, FAMILY))
                .statement().areas().getFirst().check()).startsWith("While you live with");
        assertThat(result.statement().areas().getFirst().check()).startsWith("Whether the family policy still "
                + "covers you depends on your household");
    }

    @Test
    void contractsAreCitedByCoverPolicyholderAndRoleOnly() {
        DemandsAndNeeds.Result result = of(ALONE, List.of(OWN, FAMILY));
        assertThat(result.computation().inputs()).extracting(FigureInput::text).containsExactly("single", "0",
                "Junior developer", "Private liability insurance, policyholder Jonas",
                "Private liability insurance, co-insured on the policy of Parents of Jonas");
    }
}
