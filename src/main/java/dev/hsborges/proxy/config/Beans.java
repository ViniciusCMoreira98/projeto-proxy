package dev.hsborges.proxy.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.hsborges.proxy.queue.RequestQueue;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class Beans {

    @Bean
    public RequestQueue requestQueue(ProxyConfig config, MeterRegistry registry) {
        RequestQueue q = new RequestQueue(config.getQueueMaxSize());
        registry.gauge("proxy.queue.size", q, RequestQueue::size);
        return q;
    }

    @Bean
    public Cache<String, String> responseCache(ProxyConfig config) {
        return Caffeine.newBuilder()
                .maximumSize(config.getCacheMaxSize())
                .expireAfterWrite(Duration.ofMillis(config.getCacheTtlMs()))
                .recordStats()
                .build();
    }
}


