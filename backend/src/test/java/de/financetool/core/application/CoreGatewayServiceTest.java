package de.financetool.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.audit.EvidenceItem;
import de.financetool.audit.EvidenceOrigin;
import de.financetool.audit.Provenance;
import de.financetool.bank.AccountType;
import de.financetool.bank.AssetClass;
import de.financetool.bank.BankSnapshot;
import de.financetool.bank.TransactionCategory;
import de.financetool.bank.TransactionOrigin;
import de.financetool.core.domain.MonteCarloProjection;
import de.financetool.core.domain.WealthFigures;
import de.financetool.knowledge.RuleParamPort;
import de.financetool.knowledge.RuleParameter;
import de.financetool.shared.RequestContext;
import de.financetool.shared.config.AgentConfiguration;
import de.financetool.shared.config.AutonomyThresholds;
import de.financetool.testing.TestContexts;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The gateway refuses to compute with a Rule Parameter that has no version valid at {@code asOf}, and picks the
 * projection's return model from the index series.
 */
class CoreGatewayServiceTest {

    @Test
    void aRuleParameterNotValidAtAsOfIsRefusedAndNothingIsStored() {
        UUID current = UUID.randomUUID();
        BankSnapshot snapshot = new BankSnapshot(TestContexts.LENA, TestContexts.AS_OF,
                List.of(new BankSnapshot.Account(current, AccountType.CURRENT, "Current account", "DE00",
                        LocalDate.of(2025, 7, 1), new BigDecimal("100.00"), new BigDecimal("100.00"))),
                List.of(), List.of(), List.of(), List.of());
        List<EvidenceItem> stored = new ArrayList<>();
        RuleParamPort refusing = (key, asOf) -> {
            throw new IllegalStateException("No version of Rule Parameter " + key + " is valid on " + asOf);
        };
        CoreGatewayService gateway = new CoreGatewayService((persona, asOf) -> snapshot, stored::addAll, refusing,
                Optional::empty);

        assertThatThrownBy(() -> gateway.liquidity(TestContexts.context(), EvidenceOrigin.OVERVIEW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is valid on " + TestContexts.AS_OF);
        assertThat(stored).extracting(i -> ((Provenance) i.content()).function())
                .doesNotContain("cashFlowForecast", "emergencyFundTarget");
    }

    static final Map<String, String> PROJECTION_PARAMETERS = Map.of("projection_equity_mean_return", "0.06",
            "projection_equity_volatility", "0.15", "reference_ter_world_equity_etf", "0.002",
            "projection_inflation", "0.02", "projection_default_horizon_years", "30",
            "forecast_variable_spending_band", "0.15", "emergency_fund_months_min", "3",
            "emergency_fund_months_max", "6");
    static final RuleParamPort PARAMETERS = (key, asOf) -> new RuleParameter(key,
            new BigDecimal(PROJECTION_PARAMETERS.get(key)), "FRACTION", LocalDate.of(2020, 1, 1), null, "test", "1");

    static BankSnapshot withHolding(boolean holding) {
        UUID depot = UUID.randomUUID();
        return new BankSnapshot(TestContexts.LENA, TestContexts.AS_OF, List.of(), List.of(), List.of(),
                holding ? List.of(new BankSnapshot.Holding(UUID.randomUUID(), depot, "World equity ETF",
                        AssetClass.EQUITY_ETF, new BigDecimal("10.000000"), new BigDecimal("100.00"),
                        TestContexts.AS_OF, new BigDecimal("900.00"))) : List.of(),
                List.of());
    }

    @Test
    void anIndexSeriesWithMoreThan24MonthsIsBootstrapped() {
        List<BigDecimal> returns = Collections.nCopies(36, new BigDecimal("0.005"));
        CoreGatewayService gateway = new CoreGatewayService((persona, asOf) -> withHolding(true), items -> { },
                PARAMETERS, () -> Optional.of(new IndexReturns.Series("world.csv", returns)));

        WealthFigures figures = gateway.wealth(TestContexts.context(), EvidenceOrigin.OVERVIEW);

        assertThat(figures.assumptions().getFirst()).contains("36 historical monthly index returns (world.csv)");
        assertThat(figures.projection().ruleParamVersions()).doesNotContainKey("projection_equity_mean_return");
    }

    @Test
    void aShortSeriesFallsBackToTheNormalModel() {
        List<BigDecimal> returns = Collections.nCopies(24, new BigDecimal("0.005"));
        CoreGatewayService gateway = new CoreGatewayService((persona, asOf) -> withHolding(true), items -> { },
                PARAMETERS, () -> Optional.of(new IndexReturns.Series("world.csv", returns)));

        WealthFigures figures = gateway.wealth(TestContexts.context(), EvidenceOrigin.OVERVIEW);

        assertThat(figures.assumptions().getFirst()).startsWith("Returns: normally distributed");
        assertThat(figures.holdings()).singleElement().satisfies(h -> {
            assertThat(h.assetClass()).isEqualTo("Equity ETF");
            assertThat(h.value().numbers().getFirst().value()).isEqualTo("1000.00");
        });
    }

    /** A current account with a categorised booking every third day over the last 90 days. */
    static BankSnapshot everyThirdDay() {
        UUID current = UUID.randomUUID();
        List<BankSnapshot.Transaction> transactions = new ArrayList<>();
        for (LocalDate day = TestContexts.AS_OF; day.isAfter(TestContexts.AS_OF.minusDays(90));
                day = day.minusDays(3)) {
            transactions.add(new BankSnapshot.Transaction(UUID.randomUUID(), current, day, new BigDecimal("-1.00"),
                    TransactionCategory.GROCERIES, "Shop", null, null, null, TransactionOrigin.FIXTURE));
        }
        return new BankSnapshot(TestContexts.LENA, TestContexts.AS_OF,
                List.of(new BankSnapshot.Account(current, AccountType.CURRENT, "Current account", "DE00",
                        LocalDate.of(2025, 7, 1), new BigDecimal("100.00"), new BigDecimal("100.00"))),
                List.of(), transactions, List.of(), List.of());
    }

    static Provenance.Check forecastConfidence(RequestContext context) {
        List<EvidenceItem> stored = new ArrayList<>();
        CoreGatewayService gateway = new CoreGatewayService((persona, asOf) -> everyThirdDay(), stored::addAll,
                PARAMETERS, Optional::empty);
        gateway.liquidity(context, EvidenceOrigin.OVERVIEW);
        return stored.stream().map(i -> (Provenance) i.content())
                .filter(p -> p.function().equals("cashFlowForecast"))
                .findFirst().orElseThrow().checks().getFirst();
    }

    @Test
    void theConfiguredThresholdsDecideTheDataConfidenceCheck() {
        Provenance.Check passing = forecastConfidence(TestContexts.context());
        assertThat(passing.id()).isEqualTo("data-confidence");
        assertThat(passing.passed()).isTrue();

        AutonomyThresholds strict = new AutonomyThresholds(TestContexts.PRD,
                TestContexts.THRESHOLDS.investmentShareOfLiquidAssets(), new BigDecimal("0.95"), 2);
        Provenance.Check failing = forecastConfidence(TestContexts.context(strict, TestContexts.SEEDS));
        assertThat(failing.id()).isEqualTo("data-confidence");
        assertThat(failing.passed()).isFalse();
    }

    @Test
    void theProjectionUsesTheCoreSeedOfTheContext() {
        CoreGatewayService gateway = new CoreGatewayService((persona, asOf) -> withHolding(true), items -> { },
                PARAMETERS, Optional::empty);
        AgentConfiguration.Seeds otherCore = new AgentConfiguration.Seeds(TestContexts.SEEDS.core() + 1,
                TestContexts.SEEDS.llm());

        BigDecimal p50 = gateway.wealth(TestContexts.context(), EvidenceOrigin.OVERVIEW).projection()
                .number(MonteCarloProjection.OUTPUT_P50).value();
        BigDecimal again = gateway.wealth(TestContexts.context(), EvidenceOrigin.OVERVIEW).projection()
                .number(MonteCarloProjection.OUTPUT_P50).value();
        BigDecimal other = gateway.wealth(TestContexts.context(TestContexts.THRESHOLDS, otherCore),
                EvidenceOrigin.OVERVIEW).projection().number(MonteCarloProjection.OUTPUT_P50).value();

        assertThat(again).isEqualTo(p50);
        assertThat(other).isNotEqualTo(p50);
    }

    @Test
    void withoutHoldingsOrPlansThereIsNoProjection() {
        List<EvidenceItem> stored = new ArrayList<>();
        CoreGatewayService gateway = new CoreGatewayService((persona, asOf) -> withHolding(false), stored::addAll,
                PARAMETERS, Optional::empty);

        WealthFigures figures = gateway.wealth(TestContexts.context(), EvidenceOrigin.OVERVIEW);

        assertThat(figures.projection()).isNull();
        assertThat(figures.totals()).isNull();
        assertThat(stored).isEmpty();
    }
}
