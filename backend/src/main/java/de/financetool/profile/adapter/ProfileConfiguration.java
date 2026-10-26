package de.financetool.profile.adapter;

import de.financetool.profile.ProfileCompleteness;
import de.financetool.shared.config.ProfileMinimums;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The completeness evaluator from {@code config/profile-minimums.yaml}. */
@Configuration(proxyBeanMethods = false)
class ProfileConfiguration {

    @Bean
    ProfileCompleteness profileCompleteness(ProfileMinimums minimums) {
        return new ProfileCompleteness(minimums.topics(), minimums.strictSuitabilityFields());
    }
}
