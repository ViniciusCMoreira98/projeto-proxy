package dev.hsborges.proxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "proxy")
public class ProxyConfig {
    private String upstreamBaseUrl = "https://score.hsborges.dev";
    private String clientId;
    private String scorePath = "/score";
    private int queueMaxSize = 100;
    private long queueOfferTimeoutMs = 50;
    private long requestTtlMs = 10000;
    private long schedulerBaseIntervalMs = 1000; // 1 req/s
    private long penaltyExtraDelayMs = 2000; // +2s
    private int cacheMaxSize = 1000;
    private long cacheTtlMs = 30000;

    public String getUpstreamBaseUrl() { return upstreamBaseUrl; }
    public void setUpstreamBaseUrl(String upstreamBaseUrl) { this.upstreamBaseUrl = upstreamBaseUrl; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getScorePath() { return scorePath; }
    public void setScorePath(String scorePath) { this.scorePath = scorePath; }
    public int getQueueMaxSize() { return queueMaxSize; }
    public void setQueueMaxSize(int queueMaxSize) { this.queueMaxSize = queueMaxSize; }
    public long getQueueOfferTimeoutMs() { return queueOfferTimeoutMs; }
    public void setQueueOfferTimeoutMs(long queueOfferTimeoutMs) { this.queueOfferTimeoutMs = queueOfferTimeoutMs; }
    public long getRequestTtlMs() { return requestTtlMs; }
    public void setRequestTtlMs(long requestTtlMs) { this.requestTtlMs = requestTtlMs; }
    public long getSchedulerBaseIntervalMs() { return schedulerBaseIntervalMs; }
    public void setSchedulerBaseIntervalMs(long schedulerBaseIntervalMs) { this.schedulerBaseIntervalMs = schedulerBaseIntervalMs; }
    public long getPenaltyExtraDelayMs() { return penaltyExtraDelayMs; }
    public void setPenaltyExtraDelayMs(long penaltyExtraDelayMs) { this.penaltyExtraDelayMs = penaltyExtraDelayMs; }
    public int getCacheMaxSize() { return cacheMaxSize; }
    public void setCacheMaxSize(int cacheMaxSize) { this.cacheMaxSize = cacheMaxSize; }
    public long getCacheTtlMs() { return cacheTtlMs; }
    public void setCacheTtlMs(long cacheTtlMs) { this.cacheTtlMs = cacheTtlMs; }
}




