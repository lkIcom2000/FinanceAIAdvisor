package de.financetool.shared.web;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Session login with SPA CSRF and role separation (AD-11). Every security error is rendered by
 * {@link ProblemDetailsAdvice} as JSON; nothing redirects.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    public static final String RESEARCH_PROFILE = "research";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment,
            SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        boolean research = environment.acceptsProfiles(Profiles.of(RESEARCH_PROFILE));
        http
                .csrf(csrf -> {
                    // The cookie repository spa() would create, shared with AuthController to rotate it on login.
                    csrf.spa().csrfTokenRepository(csrfTokenRepository);
                    if (!research) {
                        // Unknown paths answer 404 for every method, not 403 for a missing token.
                        csrf.ignoringRequestMatchers("/api/research/**");
                    }
                })
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) ->
                                exceptionResolver.resolveException(request, response, null, ex))
                        .accessDeniedHandler((request, response, ex) ->
                                exceptionResolver.resolveException(request, response, null, ex)))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                            .requestMatchers("/api/auth/**").authenticated()
                            .requestMatchers("/api/advisor/**", "/v3/api-docs/advisor").hasRole("ADVISOR");
                    if (research) {
                        auth.requestMatchers("/api/research/**", "/v3/api-docs/research").hasRole("RESEARCHER");
                    } else {
                        // Not registered without the research profile: unknown paths answer 404 for everyone.
                        auth.requestMatchers("/api/research/**").permitAll();
                    }
                    auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/swagger-config")
                            .hasAnyRole("ADVISOR", "RESEARCHER")
                            .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                                    "/v3/api-docs.yaml/**").denyAll()
                            .requestMatchers("/api/**").hasRole("PERSONA")
                            .anyRequest().permitAll();
                });
        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(PasswordEncoderFactories.createDelegatingPasswordEncoder());
        return new ProviderManager(provider);
    }

    @Bean
    WebMvcConfigurer personaScopeArgument() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new PersonaScopeResolver());
            }
        };
    }
}
