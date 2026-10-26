package de.financetool.advisory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.financetool.advisory.domain.ChatMessage;
import de.financetool.core.CoreGateway;
import de.financetool.core.domain.CoreResult;
import de.financetool.core.domain.ProfileFigures;
import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import de.financetool.testing.TestContexts;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Story 6.2: which fields the assessment card lists and what it pre-fills from account data. */
class AssessmentCardsTest {

    final CoreGateway core = mock(CoreGateway.class);
    final AssessmentCards cards = new AssessmentCards(core);

    @Test
    void theCardListsEachStrictFieldAndOneFieldPerUncoveredGroupInCatalogOrder() {
        assertThat(AssessmentCards.fields(List.of("risk_tolerance or loss_aversion", "pension_situation",
                "sustainability_preference", "goal", "risk_tolerance", "monthly_surplus or net_income")))
                .containsExactly("goal", "monthly_surplus", "risk_tolerance", "sustainability_preference",
                        "pension_situation");
        assertThat(AssessmentCards.fields(List.of("monthly_surplus or net_income", "net_income")))
                .containsExactly("net_income");
        assertThat(AssessmentCards.fields(List.of())).isEmpty();
    }

    @Test
    void accountFieldsArePrefilledWithTheirCoreFigures() {
        stubFigures("3100.00");
        ChatMessage.Assessment card = cards.open(TestContexts.context(),
                List.of("net_income", "emergency_fund", "goal")).orElseThrow();

        assertThat(card.status()).isEqualTo(ChatMessage.Assessment.Status.OPEN);
        assertThat(card.fields()).containsExactly(
                new ChatMessage.Assessment.Field("goal", null, null),
                new ChatMessage.Assessment.Field("net_income", "3100.00", "aaaaaaaaaaaaaaa1"),
                new ChatMessage.Assessment.Field("emergency_fund", "9000.00", "aaaaaaaaaaaaaaa3"));
    }

    @Test
    void aNetIncomeOfZeroIsNotPrefilled() {
        stubFigures("0.00");
        ChatMessage.Assessment card = cards.open(TestContexts.context(), List.of("net_income")).orElseThrow();

        assertThat(card.fields()).containsExactly(new ChatMessage.Assessment.Field("net_income", null, null));
    }

    @Test
    void withoutAccountFieldsOrAskableFieldsTheCoreIsNotCalled() {
        assertThat(cards.open(TestContexts.context(), List.of("goal")).orElseThrow().keys()).containsExactly("goal");
        assertThat(cards.open(TestContexts.context(), List.of())).isEmpty();
        verifyNoInteractions(core);
    }

    private void stubFigures(String income) {
        CoreResult netIncome = mock(CoreResult.class);
        when(netIncome.number(CoreGateway.NET_INCOME)).thenReturn(number("aaaaaaaaaaaaaaa1", income));
        CoreResult fund = mock(CoreResult.class);
        when(fund.number(CoreGateway.MONTHLY_EXPENSES)).thenReturn(number("aaaaaaaaaaaaaaa2", "2400.00"));
        when(fund.number(CoreGateway.EMERGENCY_FUND)).thenReturn(number("aaaaaaaaaaaaaaa3", "9000.00"));
        when(core.profileFigures(any(), any())).thenReturn(new ProfileFigures(netIncome, fund));
    }

    private static SourcedNumber number(String provId, String value) {
        return new SourcedNumber(provId, new BigDecimal(value), Unit.EUR, "label");
    }
}
