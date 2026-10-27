package de.financetool.monitoring.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.PostgresIntegrationTest;
import de.financetool.actions.Actions;
import de.financetool.actions.domain.AutonomyGate;
import de.financetool.audit.AuditWriter;
import de.financetool.audit.EvidenceOrigin;
import de.financetool.core.CoreGateway;
import de.financetool.escalation.EscalationCases;
import de.financetool.knowledge.NoValidRuleParameterException;
import de.financetool.knowledge.RuleAnnouncement;
import de.financetool.knowledge.RuleParamPort;
import de.financetool.knowledge.RuleParameter;
import de.financetool.monitoring.Monitoring;
import de.financetool.monitoring.adapter.NotificationRepository;
import de.financetool.monitoring.adapter.TriggerRepository;
import de.financetool.monitoring.domain.NotificationOutcome;
import de.financetool.personas.PersonaDirectory;
import de.financetool.research.application.ResetPersonaUseCase;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.RequestContextFactory;
import de.financetool.shared.domain.PersonaId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Story 8.2: monitoring runs at the personas' simulated date (15 Oct 2026) on the fixtures, with planted rule changes
 * and announcements only from a test {@link RuleParamPort}.
 */
@ActiveProfiles("research")
@Import(MonitoringServiceTest.PlantedRulesConfiguration.class)
class MonitoringServiceTest extends PostgresIntegrationTest {

    static final PersonaId LENA = new PersonaId("p-lena");
    static final PersonaId MARCO = new PersonaId("p-marco");
    static final PersonaId JONAS = new PersonaId("p-jonas");

    /** The real Rule Parameters plus versions and announcements a test plants. */
    static class PlantedRules implements RuleParamPort {

        final RuleParamPort real;
        final List<RuleParameter> versions = new CopyOnWriteArrayList<>();
        final List<RuleAnnouncement> announcements = new CopyOnWriteArrayList<>();

        PlantedRules(RuleParamPort real) {
            this.real = real;
        }

        @Override
        public RuleParameter valid(String key, LocalDate asOf) {
            return versions.stream().filter(v -> v.key().equals(key) && v.isValidAt(asOf)).findFirst()
                    .orElseGet(() -> real.valid(key, asOf));
        }

        @Override
        public List<RuleParameter> allValid(LocalDate asOf) {
            return real.allValid(asOf);
        }

        @Override
        public List<RuleParameter> startingBetween(LocalDate after, LocalDate until) {
            return Stream.concat(real.startingBetween(after, until).stream(), versions.stream()
                    .filter(v -> v.validFrom().isAfter(after) && !v.validFrom().isAfter(until))).toList();
        }

        @Override
        public Optional<RuleParameter> previous(RuleParameter version) {
            if (!versions.contains(version)) {
                return real.previous(version);
            }
            try {
                return Optional.of(real.valid(version.key(), version.validFrom().minusDays(1)));
            } catch (NoValidRuleParameterException e) {
                return Optional.empty();
            }
        }

        @Override
        public List<RuleAnnouncement> announcements() {
            return List.copyOf(announcements);
        }
    }

    @TestConfiguration
    static class PlantedRulesConfiguration {

        @Bean
        @Primary
        PlantedRules plantedRules(@Qualifier("yamlRuleParameters") RuleParamPort real) {
            return new PlantedRules(real);
        }
    }

    @Autowired
    Monitoring monitoring;

    @Autowired
    PlantedRules planted;

    @Autowired
    RequestContextFactory contexts;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ResetPersonaUseCase resetPersona;

    @Autowired
    TriggerRepository triggers;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    CoreGateway core;

    @Autowired
    Actions actions;

    @Autowired
    EscalationCases escalation;

    @Autowired
    PersonaDirectory personas;

    /** The test database is shared by every test class: leave the personas as the fixtures define them. */
    @AfterEach
    void restoreFixtureState() {
        planted.versions.clear();
        planted.announcements.clear();
        Stream.of(LENA, MARCO, JONAS).forEach(resetPersona::reset);
    }

    RequestContext context(PersonaId persona) {
        return contexts.forPersona(new PersonaScope(persona), "run-" + UUID.randomUUID());
    }

    Monitoring.Run run(PersonaId persona) {
        return run(monitoring, persona);
    }

    Monitoring.Run run(Monitoring service, PersonaId persona) {
        resetIfFresh(persona);
        return transaction.execute(tx -> service.run(context(persona), new PersonaScope(persona)));
    }

    /** Each test starts from the fixtures; a second run in the same test keeps the first one's rows. */
    final Set<PersonaId> started = new HashSet<>();

    void resetIfFresh(PersonaId persona) {
        if (started.add(persona)) {
            resetPersona.reset(persona);
        }
    }

    long count(String table, PersonaId persona) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE persona_id = ?", Long.class,
                persona.value());
    }

    long notificationAudits(UUID notificationId) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_records WHERE kind = 'NOTIFICATION' "
                + "AND payload::text LIKE ?", Long.class, "%" + notificationId + "%");
    }

    @Test
    void uj1LenasShortfallTransferNeedsHerOkAndEveryNotificationIsAuditedOnce() {
        Monitoring.Run run = run(LENA);

        assertThat(run.triggers()).extracting(Monitoring.RunTrigger::type)
                .containsExactly("FORECAST_SHORTFALL", "EF_BELOW_TARGET");
        assertThat(run.actions()).singleElement().satisfies(a -> {
            assertThat(a.type()).isEqualTo("SHORTFALL_TRANSFER");
            assertThat(a.state()).isEqualTo("AWAITING_CONFIRMATION");
            assertThat(a.level()).isEqualTo("L2");
            assertThat(a.cappingCriteria()).containsExactly(AutonomyGate.NO_COVERING_MANDATE);
            assertThat(a.triggerId()).isEqualTo(run.triggers().getFirst().triggerId());
        });
        BigDecimal amount = jdbc.queryForObject("SELECT amount FROM actions WHERE id = ?", BigDecimal.class,
                run.actions().getFirst().actionId());
        assertThat(amount).isEqualByComparingTo("90.00");

        List<NotificationRepository.Row> rows = notifications.findAll(new PersonaScope(LENA));
        assertThat(rows).extracting(NotificationRepository.Row::outcome).containsExactly(NotificationOutcome.ACTION,
                NotificationOutcome.RECOMMENDED, NotificationOutcome.INFORMED);
        assertThat(rows.get(0).actionId()).isEqualTo(run.actions().getFirst().actionId());
        assertThat(rows.get(1).body()).contains("Paying the premium monthly would avoid this every year.");
        assertThat(rows).allSatisfy(n -> {
            assertThat(n.citedProvIds()).isNotEmpty();
            assertThat(n.title() + n.body()).doesNotContain("L2", "Mandate", "Trigger", "Rule Parameter");
        });
        assertThat(rows).extracting(NotificationRepository.Row::id).containsExactlyElementsOf(run.notificationIds());
        run.notificationIds().forEach(id -> assertThat(notificationAudits(id)).as("audit of " + id).isOne());

        List<String> cited = rows.stream().flatMap(n -> n.citedProvIds().stream()).toList();
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT prov_id) FROM evidence_items WHERE prov_id = ANY(?)",
                Long.class, (Object) cited.toArray(String[]::new)))
                .as("every cited figure is stored evidence").isEqualTo(cited.stream().distinct().count());
    }

    @Test
    void s4MarcosFundIsToppedUpByTheCoreFigureAndNeedsHisOk() {
        Monitoring.Run run = run(MARCO);

        assertThat(run.triggers()).extracting(Monitoring.RunTrigger::type).contains("EF_BELOW_TARGET")
                .doesNotContain("FORECAST_SHORTFALL");
        Monitoring.RunAction topUp = run.actions().stream().filter(a -> a.type().equals("EF_TOP_UP")).findFirst()
                .orElseThrow();
        assertThat(topUp.level()).isEqualTo("L2");
        BigDecimal expected = transaction.execute(tx -> core.emergencyFundTopUp(context(MARCO),
                EvidenceOrigin.MONITORING).number(CoreGateway.EMERGENCY_FUND_TOP_UP).value());
        assertThat(expected).isPositive();
        assertThat(jdbc.queryForObject("SELECT amount FROM actions WHERE id = ?", BigDecimal.class,
                topUp.actionId())).isEqualByComparingTo(expected);
    }

    @Test
    void s6JonasDuplicateLiabilityIsRecommendedWithoutAnAction() {
        Monitoring.Run run = run(JONAS);

        Monitoring.RunTrigger duplicate = run.triggers().stream()
                .filter(t -> t.type().equals("DUPLICATE_COVERAGE")).findFirst().orElseThrow();
        assertThat(duplicate.subjectId()).isEqualTo("LIABILITY");
        assertThat(duplicate.period()).isEqualTo("2026");
        assertThat(run.actions()).noneMatch(a -> a.triggerId().equals(duplicate.triggerId()));
        assertThat(notifications.findAll(new PersonaScope(JONAS)))
                .filteredOn(n -> n.triggerId().equals(duplicate.triggerId())).singleElement().satisfies(n -> {
                    assertThat(n.outcome()).isEqualTo(NotificationOutcome.RECOMMENDED);
                    assertThat(n.actionId()).isNull();
                });
    }

    @Test
    void s9WithoutARuleChangeThereIsNoRuleTrigger() {
        assertThat(run(LENA).triggers()).extracting(Monitoring.RunTrigger::type)
                .doesNotContain("RULE_CHANGE", "RULE_ANNOUNCEMENT");
    }

    @Test
    void aReRunAtTheSameDateAddsNothing() {
        run(LENA);
        Map<String, Long> before = counts(LENA);

        Monitoring.Run again = run(LENA);

        assertThat(again.triggers()).isEmpty();
        assertThat(again.actions()).isEmpty();
        assertThat(again.notificationIds()).isEmpty();
        assertThat(counts(LENA)).isEqualTo(before);
    }

    Map<String, Long> counts(PersonaId persona) {
        return Map.of("triggers", count("triggers", persona), "notifications", count("notifications", persona),
                "actions", count("actions", persona), "escalation_cases", count("escalation_cases", persona),
                "audit_records", count("audit_records", persona), "evidence_items", count("evidence_items", persona));
    }

    @Test
    void s12aS19S12bPlantedRuleChangesAndAnAnnouncementAtLena() {
        RuleParameter basic = new RuleParameter("riester_basic_allowance", new BigDecimal("200"), "EUR",
                LocalDate.of(2026, 10, 1), null, "test", "2026-10");
        RuleParameter deposit = new RuleParameter("ecb_deposit_facility_rate", new BigDecimal("0.0175"), "FRACTION",
                LocalDate.of(2026, 10, 8), null, "test", "2026-10-08");
        planted.versions.addAll(List.of(basic, deposit));
        planted.announcements.add(new RuleAnnouncement("reform-2027", "subsidy", "The retirement savings reform",
                List.of("riester_basic_allowance_2027"), LocalDate.of(2026, 10, 1), LocalDate.of(2027, 1, 1), null));

        Monitoring.Run run = run(LENA);

        assertThat(run.triggers()).extracting(t -> t.type() + "|" + t.subjectId() + "|" + t.period()).contains(
                "RULE_CHANGE|riester_basic_allowance|2026-10", "RULE_CHANGE|ecb_key_rates|2026-10-08",
                "RULE_ANNOUNCEMENT|reform-2027|2027-01-01");
        assertThat(run.actions()).extracting(Monitoring.RunAction::type).containsExactly("SHORTFALL_TRANSFER");
        List<NotificationRepository.Row> rows = notifications.findAll(new PersonaScope(LENA));
        assertThat(rows).filteredOn(n -> n.title().equals("A subsidy rule has changed")).singleElement()
                .satisfies(n -> {
                    assertThat(n.outcome()).isEqualTo(NotificationOutcome.RECOMMENDED);
                    assertThat(n.body()).contains("200.00 EUR from 1 Oct 2026 (before: 175.00 EUR)");
                });
        assertThat(rows).filteredOn(n -> n.title().startsWith("The ECB")).singleElement().satisfies(n -> {
            assertThat(n.outcome()).isEqualTo(NotificationOutcome.INFORMED);
            assertThat(n.body()).contains("ECB deposit facility rate 2 % to 1.75 %")
                    .doesNotContainIgnoringCase("buy").doesNotContainIgnoringCase("sell");
        });
        UUID announcement = run.triggers().stream().filter(t -> t.type().equals("RULE_ANNOUNCEMENT")).findFirst()
                .orElseThrow().triggerId();
        assertThat(rows).filteredOn(n -> n.triggerId().equals(announcement)).singleElement()
                .satisfies(n -> assertThat(n.outcome()).isEqualTo(NotificationOutcome.ESCALATED));
        assertThat(rows).allSatisfy(n -> assertThat(n.title() + n.body())
                .doesNotContain("L0", "L1", "L2", "Mandate", "Trigger", "Rule Parameter", "riester_", "ecb_"));
        run.notificationIds().forEach(id -> assertThat(notificationAudits(id)).as("audit of " + id).isOne());
        assertThat(jdbc.queryForList("SELECT origin, turn_id, block_reasons::text AS reasons FROM escalation_cases "
                + "WHERE persona_id = ?", LENA.value())).singleElement().satisfies(c -> {
                    assertThat(c).containsEntry("origin", "TRIGGER").containsEntry("turn_id", null);
                    assertThat((String) c.get("reasons")).contains("TRIGGER:" + announcement, "no_source",
                            "no_valid_rule_parameter");
                });
    }

    @Test
    void aFailingAuditLeavesNoTriggerActionNotificationOrCase() {
        AuditWriter failing = (context, entry) -> {
            throw new IllegalStateException("audit store down");
        };
        MonitoringService broken = new MonitoringService(core, actions, escalation, personas, triggers,
                notifications, failing);
        resetPersona.reset(LENA);
        Map<String, Long> before = counts(LENA);

        assertThatThrownBy(() -> transaction.execute(tx -> broken.run(context(LENA), new PersonaScope(LENA))))
                .hasMessageContaining("audit store down");

        assertThat(counts(LENA)).isEqualTo(before);
        assertThat(count("triggers", LENA)).isZero();
        assertThat(count("notifications", LENA)).isZero();
    }

    @Test
    void aResetRemovesThePersonasTriggersAndNotifications() {
        run(LENA);
        assertThat(count("triggers", LENA)).isPositive();

        resetPersona.reset(LENA);

        assertThat(count("triggers", LENA)).isZero();
        assertThat(count("notifications", LENA)).isZero();
    }

    @Test
    void anotherPersonasScopeIsRejected() {
        assertThatThrownBy(() -> transaction.execute(tx -> monitoring.run(context(LENA), new PersonaScope(MARCO))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
