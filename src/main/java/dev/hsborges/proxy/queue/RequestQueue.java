package dev.hsborges.proxy.queue;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class RequestQueue {
    private final PriorityBlockingQueue<PrioritizedRequest> queue;
    private final int maxSize;
    private final Semaphore sizeGuard;

    public RequestQueue(int maxSize) {
        this.maxSize = maxSize;
        this.queue = new PriorityBlockingQueue<>();
        this.sizeGuard = new Semaphore(maxSize);
    }

    public boolean offer(PrioritizedRequest req, long timeoutMs) throws InterruptedException {
        if (!sizeGuard.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
            return false;
        }
        boolean added = queue.offer(req);
        if (!added) {
            sizeGuard.release();
        }
        return added;
    }

    public PrioritizedRequest take() throws InterruptedException {
        PrioritizedRequest req = queue.take();
        sizeGuard.release();
        return req;
    }

    public int size() { return maxSize - sizeGuard.availablePermits(); }
}




