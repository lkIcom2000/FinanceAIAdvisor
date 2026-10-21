package de.financetool.llm.adapter;

import de.financetool.shared.AppHome;
import de.financetool.shared.config.AgentConfiguration;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Wires the Ollama adapter and its startup checks (AD-18). */
@Configuration(proxyBeanMethods = false)
class OllamaConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OllamaConfiguration.class);

    @Bean
    OllamaLlmAdapter ollamaLlmAdapter(AgentConfiguration agent, AppHome appHome,
            @Value("${app.ollama.url}") String url) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        // CPU inference of a long answer can take minutes.
        requests.setReadTimeout(Duration.ofMinutes(10));
        RestClient rest = RestClient.builder().baseUrl(url).requestFactory(requests).build();
        return new OllamaLlmAdapter(new OllamaClient(rest), agent, ModelLock.read(appHome.models()), url);
    }

    /** Lock and smoke checks before the app is ready; any failure stops startup. */
    @Bean
    @ConditionalOnProperty(name = "app.llm.verify-on-startup", havingValue = "true", matchIfMissing = true)
    ApplicationRunner modelVerification(OllamaLlmAdapter adapter) {
        return args -> {
            long start = System.nanoTime();
            adapter.verify();
            log.info("Models verified against models/{} and smoke calls passed in {} ms", ModelLock.FILE,
                    Duration.ofNanos(System.nanoTime() - start).toMillis());
        };
    }
}
