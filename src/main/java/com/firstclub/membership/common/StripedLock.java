package com.firstclub.membership.common;

import org.springframework.stereotype.Component;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class StripedLock {
    private final ReentrantLock[] stripes;

    public StripedLock() { this(64); }

    public StripedLock(int size) {
        this.stripes = new ReentrantLock[size];
        for (int i = 0; i < size; i++) stripes[i] = new ReentrantLock();
    }

    public <T> T withLock(long key, Supplier<T> action) {
        ReentrantLock lock = stripes[(int) Math.floorMod(key, stripes.length)];
        lock.lock();
        try { return action.get(); }
        finally { lock.unlock(); }
    }

    public void withLock(long key, Runnable action) {
        withLock(key, () -> { action.run(); return null; });
    }
}
