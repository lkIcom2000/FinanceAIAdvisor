package de.financetool.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.FinanceToolApplication;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

/** AD-14 / AD-25: the versioned configuration files bind, validate, and stop startup when wrong. */
class AgentConfigurationBindingTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({AgentConfiguration.class, AutonomyThresholds.class, ProfileMinimums.class})
    static class Binding {

        @Bean
        @ConfigurationPropertiesBinding
        static Converter<String, ComplianceLevel> complianceLevelConverter() {
            return ComplianceLevel::fromValue;
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(Binding.class)
            .withInitializer(context -> {
                for (String file : List.of("agent/reference.yaml", "autonomy.yaml", "profile-minimums.yaml")) {
                    for (PropertySource<?> source : load(file)) {
                        context.getEnvironment().getPropertySources().addLast(source);
                    }
                }
            });

    @Test
    void theCommittedFilesBindWithTheirStartingValues() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            AgentConfiguration agent = context.getBean(AgentConfiguration.class);
            assertThat(agent.id()).isEqualTo("reference");
            assertThat(agent.compliance()).isEqualTo(ComplianceLevel.A_B_C);
            assertThat(agent.dataExposure()).isEqualTo(DataExposureLevel.AGGREGATED);
            assertThat(agent.retrieval()).isTrue();
            assertThat(agent.llm().phrasing()).isEqualTo("llama3.1:8b-instruct-q4_K_M");
            assertThat(agent.derivedFrom().date()).isEqualTo(LocalDate.of(2026, 10, 20));

            AutonomyThresholds thresholds = context.getBean(AutonomyThresholds.class);
            assertThat(thresholds.investmentShareOfLiquidAssets()).isEqualByComparingTo(new BigDecimal("0.05"));
            assertThat(thresholds.dataConfidenceMinCoverage()).isEqualByComparingTo(new BigDecimal("0.95"));
            assertThat(thresholds.dataConfidenceMaxGapDays()).isEqualTo(5);
            assertThat(thresholds.derivedFrom().note()).contains("PRD starting value");

            ProfileMinimums minimums = context.getBean(ProfileMinimums.class);
            assertThat(minimums.topics()).containsKey("savings_plan");
            assertThat(minimums.topics().get("savings_plan"))
                    .containsExactly(List.of("monthly_surplus", "net_income"), List.of("risk_tolerance", "loss_aversion"));
            assertThat(minimums.derivedFrom()).isNotNull();
        });
    }

    @Test
    void anUnknownComplianceLevelStopsStartup() {
        runner.withPropertyValues("agent.compliance=B").run(context -> assertThat(context)
                .hasFailed().getFailure().rootCause().hasMessageContaining("compliance must be one of A, A+B, A+B+C"));
    }

    @Test
    void aBlankModelTagStopsStartupNamingTheProperty() {
        runner.withPropertyValues("agent.llm.phrasing= ").run(context -> assertThat(context)
                .hasFailed().getFailure().hasStackTraceContaining("llm.phrasing"));
    }

    @Test
    void aMissingDerivedFromStopsStartup() {
        runner.withPropertyValues("autonomy.derived-from.experiment=").run(context -> assertThat(context)
                .hasFailed().getFailure().hasStackTraceContaining("derivedFrom.experiment"));
    }

    @Test
    void aMissingAgentFileStopsStartupNamingTheFile() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(FinanceToolApplication.class)
                        .web(WebApplicationType.NONE)
                        .profiles("webtest")
                        .properties("APP_AGENT_CONFIG=nope")
                        .run())
                .hasStackTraceContaining("nope.yaml");
    }

    private static List<PropertySource<?>> load(String file) {
        try {
            Path path = Path.of("..", "config", file);
            return new YamlPropertySourceLoader().load(file, new FileSystemResource(path));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
