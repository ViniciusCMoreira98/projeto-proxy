package dev.hsborges.proxy.upstream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hsborges.proxy.config.ProxyConfig;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class UpstreamClient {
    private final WebClient webClient;
    private final ProxyConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    public UpstreamClient(ProxyConfig config, WebClient.Builder builder) {
        this.config = config;
        this.webClient = builder.baseUrl(config.getUpstreamBaseUrl()).build();
    }

    @CircuitBreaker(name = "upstream", fallbackMethod = "fallbackScore")
    @TimeLimiter(name = "upstream")
    public Mono<String> fetchScore(Map<String, String> query, Map<String, String> headers, String overrideClientId) {
        String effectiveClientId = (overrideClientId != null && !overrideClientId.isBlank())
                ? overrideClientId
                : config.getClientId();

        WebClient.RequestHeadersUriSpec<?> req = webClient.get();
        WebClient.RequestHeadersSpec<?> spec = req.uri(uriBuilder -> {
            var b = uriBuilder.path(config.getScorePath());
            if (query != null) {
                query.forEach(b::queryParam);
            }
            return b.build();
        }).accept(MediaType.APPLICATION_JSON);

        if (effectiveClientId != null && !effectiveClientId.isBlank()) {
            spec.header("client-id", effectiveClientId);
        }

        if (headers != null) {
            headers.forEach((k, v) -> {
                if (!HttpHeaders.HOST.equalsIgnoreCase(k)) {
                    spec.header(k, v);
                }
            });
        }

        spec.header(HttpHeaders.ACCEPT_ENCODING, "gzip");

        return spec.exchangeToMono(response -> {
            if (response.statusCode().is2xxSuccessful()) {
                return response.bodyToMono(byte[].class)
                        .map(bytes -> fixMessage(new String(bytes, StandardCharsets.UTF_8)));
            }
            int code = response.statusCode().value();
            return response.bodyToMono(byte[].class)
                    .defaultIfEmpty(new byte[0])
                    .map(bytes -> {
                        String raw = new String(bytes, StandardCharsets.UTF_8);
                        String body = extractJson(raw);
                        return "{\"status\":\"upstream_error\",\"code\":" + code + ",\"body\":" + quoteJson(body) + "}";
                    });
        });
    }

    @SuppressWarnings("unused")
    private Mono<String> fallbackScore(Map<String, String> query, Map<String, String> headers,
                                       String overrideClientId, Throwable t) {
        return Mono.just("{\"status\":\"fallback\",\"reason\":\"upstream unavailable\"}");
    }

    private String fixMessage(String raw) {
        try {
            String json = extractJson(raw);
            JsonNode node = mapper.readTree(json);

            if (node.has("cpf") && node.has("score")) {
                String cpf = node.get("cpf").asText();
                int score = node.get("score").asInt();

                ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("message",
                        "O score de " + cpf + " é " + score);
            }

            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            return extractJson(raw);
        }
    }

    private static String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    private static String quoteJson(String raw) {
        if (raw == null) return "\"\"";
        String escaped = raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
        return "\"" + escaped + "\"";
    }
}
