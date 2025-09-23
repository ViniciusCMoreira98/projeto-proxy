package dev.hsborges.proxy.upstream;

import dev.hsborges.proxy.config.ProxyConfig;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
    public Mono<String> fetchScore(Map<String, String> query, Map<String, String> headers) {
        WebClient.RequestHeadersUriSpec<?> req = webClient.get();
        WebClient.RequestHeadersSpec<?> spec = req.uri(uriBuilder -> {
            var b = uriBuilder.path("/score");
            if (query != null) {
                query.forEach(b::queryParam);
            }
            return b.build();
        }).accept(MediaType.APPLICATION_JSON)
                .header("x-client-id", config.getClientId());

        if (headers != null) {
            headers.forEach((k, v) -> {
                if (!HttpHeaders.HOST.equalsIgnoreCase(k)) {
                    spec.header(k, v);
                }
            });
        }

        return spec.retrieve().bodyToMono(String.class);
    }

    @SuppressWarnings("unused")
    private Mono<String> fallbackScore(Map<String, String> query, Map<String, String> headers, Throwable t) {
        return Mono.just("{\"status\":\"fallback\",\"reason\":\"upstream unavailable\"}");
    }
}



