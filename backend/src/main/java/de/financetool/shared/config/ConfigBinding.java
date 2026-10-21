package de.financetool.shared.config;

import de.financetool.shared.AppHome;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

/** Binding support for the versioned configuration files and the startup fingerprint (AD-14). */
@Configuration(proxyBeanMethods = false)
class ConfigBinding {

    @Bean
    @ConfigurationPropertiesBinding
    static Converter<String, ComplianceLevel> complianceLevelConverter() {
        return ComplianceLevel::fromValue;
    }

    @Bean
    ConfigFingerprint configFingerprint(AppHome appHome, AgentConfiguration agent) {
        Path rolePrompt = appHome.config().resolve("prompts").resolve("role-" + agent.rolePrompt() + ".txt");
        if (!Files.isRegularFile(rolePrompt)) {
            throw new IllegalStateException("agent.rolePrompt '" + agent.rolePrompt() + "' has no prompt file " + rolePrompt);
        }
        return ConfigFingerprints.compute(appHome.root(), agent.id());
    }
}
