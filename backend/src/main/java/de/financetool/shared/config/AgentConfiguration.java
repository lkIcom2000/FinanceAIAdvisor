package de.financetool.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The active Agent Configuration (AD-14), bound from {@code config/agent/<APP_AGENT_CONFIG>.yaml}. Read only by
 * {@code RequestContextFactory}; everything else receives it through the {@code RequestContext}.
 */
@Validated
@ConfigurationProperties("agent")
public record AgentConfiguration(
        @NotBlank String id,
        @NotNull @Valid DerivedFrom derivedFrom,
        @NotNull @Valid Llm llm,
        @NotNull Boolean retrieval,
        @NotBlank @Pattern(regexp = "[a-z0-9-]+") String rolePrompt,
        @NotNull ComplianceLevel compliance,
        @NotNull DataExposureLevel dataExposure,
        @NotNull @Valid Decoding decoding,
        @NotNull @Valid Seeds seeds) {

    /** Model tag per role (AD-5, AD-20) plus the embedding model (AD-9). */
    public record Llm(@NotBlank String phrasing, @NotBlank String nlu, @NotBlank String judge,
            @NotBlank String embedding) {
    }

    /** Decoding parameters passed to Ollama. */
    public record Decoding(
            @NotNull @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double topP,
            @NotNull Long seed,
            @NotNull @Positive Integer numCtx,
            @NotNull @Positive Integer numPredict) {
    }

    /** Seeds for stochastic Core functions and for LLM calls (AD-2, AD-4). */
    public record Seeds(@NotNull Long core, @NotNull Long llm) {
    }
}
