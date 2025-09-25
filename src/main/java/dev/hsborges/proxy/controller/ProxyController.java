package dev.hsborges.proxy.controller;

import com.github.benmanes.caffeine.cache.Cache;
import dev.hsborges.proxy.config.ProxyConfig;
import dev.hsborges.proxy.queue.PrioritizedRequest;
import dev.hsborges.proxy.queue.RequestQueue;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/proxy")
public class ProxyController {

    private final RequestQueue queue;
    private final Cache<String, String> cache;
    private final ProxyConfig config;
    private final MeterRegistry metrics;

    public ProxyController(RequestQueue queue, Cache<String, String> cache, ProxyConfig config, MeterRegistry metrics) {
        this.queue = queue;
        this.cache = cache;
        this.config = config;
        this.metrics = metrics;
    }

    @GetMapping("/score")
    public ResponseEntity<?> getScore(@RequestParam Map<String, String> params,
                                      @RequestHeader Map<String, String> headers) throws InterruptedException {
        String overrideClientId = headers.getOrDefault("x-client-id", params.getOrDefault("clientId", "")).trim();
        boolean hasOverride = !overrideClientId.isBlank();
        
        if (!hasOverride && (config.getClientId() == null || config.getClientId().isBlank())) {
            return ResponseEntity.badRequest().body("CLIENT_ID ausente. Informe header x-client-id ou query clientId, ou configure proxy.client-id.");
        }
        String cacheKey = buildCacheKey(params);
        String cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            metrics.counter("proxy.cache.hit").increment();
            return ResponseEntity.ok(cached);
        }
        metrics.counter("proxy.cache.miss").increment();

        PrioritizedRequest.Priority prio = priorityFromHeader(headers.getOrDefault("x-priority", "MEDIUM"));
        Instant ttl = Instant.now().plusMillis(config.getRequestTtlMs());
        Map<String, String> safeHeaders = new HashMap<>(headers);

        PrioritizedRequest req = new PrioritizedRequest("/score", params, safeHeaders, overrideClientId, prio, ttl);
        boolean accepted = queue.offer(req, config.getQueueOfferTimeoutMs());
        if (!accepted) {
            metrics.counter("proxy.queue.drop", "reason", "full").increment();
            return ResponseEntity.status(429).body("Queue full - request dropped");
        }
        metrics.counter("proxy.queue.enqueued").increment();
        try {
            String body = req.getFuture().get(config.getRequestTtlMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
            return ResponseEntity.ok(body);
        } catch (java.util.concurrent.TimeoutException te) {
            metrics.counter("proxy.queue.drop", "reason", "timeout").increment();
            return ResponseEntity.status(504).body("Timeout waiting upstream");
        } catch (Exception e) {
            return ResponseEntity.status(502).body("Upstream error");
        }
    }

    private static PrioritizedRequest.Priority priorityFromHeader(String value) {
        try {
            return PrioritizedRequest.Priority.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return PrioritizedRequest.Priority.MEDIUM;
        }
    }

    private static String buildCacheKey(Map<String, String> params) {
        return params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
    }
}


