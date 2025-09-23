package dev.hsborges.proxy.queue;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

public class PrioritizedRequest implements Comparable<PrioritizedRequest> {

    public enum Priority { HIGH, MEDIUM, LOW }

    private final String id;
    private final String path;
    private final Map<String, String> queryParams;
    private final Map<String, String> headers;
    private final Priority priority;
    private final Instant enqueuedAt;
    private final Instant expiresAt;
    private final CompletableFuture<String> future;

    public PrioritizedRequest(String path,
                              Map<String, String> queryParams,
                              Map<String, String> headers,
                              Priority priority,
                              Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.path = path;
        this.queryParams = queryParams;
        this.headers = headers;
        this.priority = priority == null ? Priority.MEDIUM : priority;
        this.enqueuedAt = Instant.now();
        this.expiresAt = expiresAt;
        this.future = new CompletableFuture<>();
    }

    public String getId() { return id; }
    public String getPath() { return path; }
    public Map<String, String> getQueryParams() { return queryParams; }
    public Map<String, String> getHeaders() { return headers; }
    public Priority getPriority() { return priority; }
    public Instant getEnqueuedAt() { return enqueuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isExpired() { return expiresAt != null && Instant.now().isAfter(expiresAt); }
    public CompletableFuture<String> getFuture() { return future; }

    @Override
    public int compareTo(PrioritizedRequest other) {
        int byPriority = other.priority.ordinal() - this.priority.ordinal();
        if (byPriority != 0) return byPriority;
        return this.enqueuedAt.compareTo(other.enqueuedAt);
    }
}


