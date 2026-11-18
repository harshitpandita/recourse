package com.recourse.explain;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** Minimal HTTP client for a local Ollama server. Returns empty on any failure so callers can fall back. */
@Component
public class OllamaClient {

    record GenerateResponse(String response, boolean done) {}

    private final LlmProperties props;
    private final RestClient http;

    public OllamaClient(LlmProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(props.timeoutSeconds()));
        this.http = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build();
    }

    public Optional<String> generate(String prompt) {
        if (!props.enabled()) return Optional.empty();
        try {
            GenerateResponse r = http.post().uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", props.model(),
                            "prompt", prompt,
                            "stream", false,
                            "options", Map.of("temperature", 0.3, "num_predict", 220)))
                    .retrieve().body(GenerateResponse.class);
            if (r == null || r.response() == null || r.response().isBlank()) return Optional.empty();
            return Optional.of(r.response().strip());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Asks Ollama to load the model into memory, so the first real explanation doesn't pay the
     * loading time. An empty prompt only loads the model and generates nothing. Loading can take
     * longer than a normal answer, so this call gets its own, longer read timeout.
     * Returns false on any failure; callers ignore it.
     */
    public boolean warmUp() {
        if (!props.enabled()) return false;
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(2));
            factory.setReadTimeout(Duration.ofSeconds(WARM_UP_TIMEOUT_SECONDS));
            RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build()
                    .post().uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", props.model(), "prompt", "", "stream", false))
                    .retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static final int WARM_UP_TIMEOUT_SECONDS = 120;

    public boolean reachable() {
        if (!props.enabled()) return false;
        try {
            http.get().uri("/api/tags").retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public String model() { return props.model(); }
    public boolean enabled() { return props.enabled(); }
}
