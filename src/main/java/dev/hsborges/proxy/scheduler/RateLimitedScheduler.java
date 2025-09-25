package dev.hsborges.proxy.scheduler;

import dev.hsborges.proxy.config.ProxyConfig;
import com.github.benmanes.caffeine.cache.Cache;
import dev.hsborges.proxy.queue.PrioritizedRequest;
import dev.hsborges.proxy.queue.RequestQueue;
import dev.hsborges.proxy.upstream.UpstreamClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RateLimitedScheduler {
    private static final Logger log = LoggerFactory.getLogger(RateLimitedScheduler.class);

    private final RequestQueue queue;
    private final UpstreamClient upstreamClient;
    private final ProxyConfig config;
    private final MeterRegistry metrics;
    private final Cache<String, String> cache;

    private Thread workerThread;
    private final AtomicLong currentIntervalMs = new AtomicLong();
    private final Timer latencyTimer;

    public RateLimitedScheduler(RequestQueue queue, UpstreamClient upstreamClient, ProxyConfig config, MeterRegistry registry, Cache<String, String> cache) {
        this.queue = queue;
        this.upstreamClient = upstreamClient;
        this.config = config;
        this.metrics = registry;
        this.cache = cache;
        this.currentIntervalMs.set(config.getSchedulerBaseIntervalMs());
        this.latencyTimer = Timer.builder("proxy.upstream.latency").register(registry);
    }

    @PostConstruct
    public void start() {
        workerThread = new Thread(() -> {
            try {
                // pequeno atraso inicial
                Thread.sleep(200);
                while (!Thread.currentThread().isInterrupted()) {
                    drainOnce();
                    Thread.sleep(currentIntervalMs.get());
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }, "rate-limited-scheduler");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void drainOnce() {
        try {
            PrioritizedRequest req = queue.take();
            if (req.isExpired()) {
                metrics.counter("proxy.queue.drop", "reason", "ttl").increment();
                return;
            }
            Instant start = Instant.now();
            upstreamClient.fetchScore(req.getQueryParams(), req.getHeaders(), req.getClientId())
                    .doOnError(err -> metrics.counter("proxy.upstream.errors").increment())
                    .doOnSuccess(body -> {
                        metrics.counter("proxy.upstream.success").increment();
                        String cacheKey = buildCacheKey(req.getQueryParams());
                        cache.put(cacheKey, body);
                        req.getFuture().complete(body);
                    })
                    .doOnError(err -> req.getFuture().completeExceptionally(err))
                    .doFinally(s -> {
                        long ms = Duration.between(start, Instant.now()).toMillis();
                        latencyTimer.record(ms, TimeUnit.MILLISECONDS);
                        adjustCadence(ms);
                    })
                    .subscribe();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Scheduler error", e);
        }
    }

    private static String buildCacheKey(Map<String, String> params) {
        if (params == null || params.isEmpty()) return "";
        return params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
    }

    private void adjustCadence(long observedMs) {
        long base = config.getSchedulerBaseIntervalMs();
        long penalty = config.getPenaltyExtraDelayMs();
        long target = observedMs > (base + penalty - 50) ? base + penalty : base; // heurística simples
        long prev = currentIntervalMs.getAndSet(target);
        if (prev != target) {
            metrics.gauge("proxy.scheduler.interval.ms", currentIntervalMs);
            log.info("Ajuste de intervalo do scheduler: {}ms -> {}ms", prev, target);
        }
    }
}


