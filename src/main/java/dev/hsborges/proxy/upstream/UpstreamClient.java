package dev.hsborges.proxy.upstream;

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

        // Sempre envia client-id como header conforme contrato do provider
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

        // Força aceitar gzip (mesmo que não venha, não atrapalha)
        spec.header(HttpHeaders.ACCEPT_ENCODING, "gzip");

        return spec.exchangeToMono(response -> {
            if (response.statusCode().is2xxSuccessful()) {
                return response.bodyToMono(byte[].class).map(bytes -> extractJson(new String(bytes, StandardCharsets.UTF_8)));
            }
            int code = response.statusCode().value();
            return response.bodyToMono(byte[].class)
                    .defaultIfEmpty(new byte[0])
                    .map(bytes -> {
                        String raw = new String(bytes, StandardCharsets.UTF_8);
                        String body = extractJson(raw);
                        if (code == 401 && body.contains("Client ID is required")) {
                            return "{\"status\":\"upstream_error\",\"code\":" + code + ",\"body\":" +
                                    quoteJson("Client ID inválido ou ausente. Verifique se o Client ID está correto e ativo no serviço upstream.") + "}";
                        }
                        return "{\"status\":\"upstream_error\",\"code\":" + code + ",\"body\":" + quoteJson(body) + "}";
                    });
        });
    }

    @SuppressWarnings("unused")
    private Mono<String> fallbackScore(Map<String, String> query, Map<String, String> headers,
                                       String overrideClientId, Throwable t) {
        return Mono.just("{\"status\":\"fallback\",\"reason\":\"upstream unavailable\"}");
    }

    private static String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw; // fallback caso não ache JSON
    }

    private static String quoteJson(String raw) {
        if (raw == null) return "\"\"";
        String escaped = raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
        return "\"" + escaped + "\"";
    }
}
