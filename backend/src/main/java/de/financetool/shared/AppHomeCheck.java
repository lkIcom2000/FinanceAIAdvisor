package de.financetool.shared;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Validates {@link AppHome} before any bean is created, so a missing directory stops startup before Flyway or any
 * other startup work touches the database.
 */
@Configuration(proxyBeanMethods = false)
class AppHomeCheck {

    @Bean
    static BeanFactoryPostProcessor appHomeValidation(Environment environment) {
        return beanFactory -> Binder.get(environment).bind("app", AppHome.class)
                .orElseThrow(() -> new IllegalStateException("app.home (APP_HOME) must be set"));
    }
}
