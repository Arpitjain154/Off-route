package com.idr.navigator.cloud;

import java.util.*;

/** Serializes authorization changes with request dispatch, cancelling in-flight connections. */
public final class CloudRequestGate {

    public interface Action {
        void run() throws Exception;
    }

    private final Object lock;
    private final Set<Runnable> active = new HashSet<>();

    public CloudRequestGate() {
        this(new Object());
    }

    public CloudRequestGate(Object lock) {
        this.lock = lock;
    }

    public AutoCloseable begin(Action guard, Runnable cancel, Action dispatch) throws Exception {
        synchronized (lock) {
            guard.run();
            active.add(cancel);
            try {
                dispatch.run();
            } catch (Exception e) {
                active.remove(cancel);
                cancel.run();
                throw e;
            }
            return () -> {
                synchronized (lock) {
                    active.remove(cancel);
                }
            };
        }
    }

    public void invalidate(Runnable change) {
        synchronized (lock) {
            change.run();
            for (Runnable cancel : active) cancel.run();
            active.clear();
        }
    }
}
