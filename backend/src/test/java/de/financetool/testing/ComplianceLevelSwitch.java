package de.financetool.testing;

import de.financetool.shared.RequestContext;
import de.financetool.shared.RequestContextFactory;
import de.financetool.shared.config.AgentConfiguration;
import de.financetool.shared.config.ComplianceLevel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Lets integration tests run turns at another compliance level or retrieval setting than the configured one, without
 * a second Spring context: the {@link RequestContextFactory} bean is wrapped so that, while an override is set, the
 * contexts it builds carry it. Tests that set an override call {@link Switch#reset()} afterwards.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ComplianceLevelSwitch {

    /** The overrides; {@code null} means the configured value. */
    public static class Switch {

        private volatile ComplianceLevel level;
        private volatile Boolean retrieval;

        public void set(ComplianceLevel value) {
            level = value;
        }

        public void retrieval(boolean value) {
            retrieval = value;
        }

        public void reset() {
            level = null;
            retrieval = null;
        }

        RequestContext apply(RequestContext context) {
            ComplianceLevel levelOverride = level;
            Boolean retrievalOverride = retrieval;
            if (levelOverride == null && retrievalOverride == null) {
                return context;
            }
            AgentConfiguration a = context.agentConfig();
            AgentConfiguration changed = new AgentConfiguration(a.id(), a.derivedFrom(), a.llm(),
                    retrievalOverride == null ? a.retrieval() : retrievalOverride, a.rolePrompt(),
                    levelOverride == null ? a.compliance() : levelOverride, a.dataExposure(), a.decoding(),
                    a.seeds());
            return new RequestContext(changed, context.configFingerprint(), context.thresholds(),
                    context.kbSnapshot(), context.seeds(), context.asOf(), context.personaId(), context.runId());
        }
    }

    @Bean
    static Switch complianceLevelSwitch() {
        return new Switch();
    }

    @Bean
    static BeanPostProcessor complianceLevelOverride(ObjectProvider<Switch> overrides) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof RequestContextFactory factory) {
                    return (RequestContextFactory) (scope, runId) -> overrides.getObject()
                            .apply(factory.forPersona(scope, runId));
                }
                return bean;
            }
        };
    }
}
