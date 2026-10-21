package de.financetool.shared.web;

import de.financetool.shared.ProblemCode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.Arrays;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Registers the explicit cross-cutting schemas of the API contract: {@code Money}, the closed {@code ProblemCode}
 * enum and the RFC 9457 {@code ProblemDetail}. The Angular client is generated from this document.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiSchemas {

    static final String REF_PREFIX = "#/components/schemas/";

    @Bean
    OpenAPI fineOpenApi() {
        return new OpenAPI().info(new Info().title("Fine API").version("0.1.0"));
    }

    /** The advisor's Swagger UI landing group (AD-11: no advisor UI). */
    @Bean
    GroupedOpenApi advisorApi() {
        return GroupedOpenApi.builder().group("advisor").pathsToMatch("/api/advisor/**", "/api/auth/**")
                .addOpenApiCustomizer(sharedSchemas()).build();
    }

    /** The researcher's Swagger UI landing group; registered only under the research profile. */
    @Bean
    @Profile(SecurityConfig.RESEARCH_PROFILE)
    GroupedOpenApi researchApi() {
        return GroupedOpenApi.builder().group("research").pathsToMatch("/api/research/**", "/api/auth/**")
                .addOpenApiCustomizer(sharedSchemas()).build();
    }

    @Bean
    OpenApiCustomizer sharedSchemas() {
        return openApi -> openApi.getComponents()
                .addSchemas("Money", money())
                .addSchemas("ProblemCode", problemCode())
                .addSchemas("ProblemDetail", problemDetail());
    }

    static Schema<?> money() {
        return new ObjectSchema()
                .description("An amount of money: decimal string with two fraction digits plus currency.")
                .addProperty("amount", new StringSchema()
                        .pattern("^-?\\d+\\.\\d{2}$")
                        .example("1234.50"))
                .addProperty("currency", new StringSchema()._enum(List.of("EUR")))
                .required(List.of("amount", "currency"));
    }

    static Schema<?> problemCode() {
        return new StringSchema()
                .description("Closed set of machine-readable error codes.")
                ._enum(Arrays.stream(ProblemCode.values()).map(Enum::name).toList());
    }

    static Schema<?> problemDetail() {
        return new ObjectSchema()
                .description("RFC 9457 problem details with a closed code.")
                .addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().format("uri"))
                .addProperty("code", new Schema<>().$ref(REF_PREFIX + "ProblemCode"))
                .required(List.of("title", "status", "code"));
    }
}
