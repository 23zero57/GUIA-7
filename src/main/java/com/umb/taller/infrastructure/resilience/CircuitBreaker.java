package com.umb.taller.infrastructure.resilience;

import com.umb.taller.domain.CircuitBreakerOpenException;
import java.util.function.Supplier;

public class CircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }

    private State state = State.CLOSED;
    private int failureCount = 0;
    private final int failureThreshold;
    private final long timeoutMillis;
    private long lastFailureTime = 0;

    public CircuitBreaker(int failureThreshold, long timeoutMillis) {
        this.failureThreshold = failureThreshold;
        this.timeoutMillis = timeoutMillis;
    }

    public synchronized <T> T execute(Supplier<T> supplier) {
        if (state == State.OPEN) {
            if (System.currentTimeMillis() - lastFailureTime > timeoutMillis) {
                state = State.HALF_OPEN;
            } else {
                throw new CircuitBreakerOpenException("Circuit Breaker esta ABIERTO. Peticion bloqueada.");
            }
        }

        try {
            T result = supplier.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }

    private void onSuccess() {
        failureCount = 0;
        state = State.CLOSED;
    }

    private void onFailure() {
        failureCount++;
        lastFailureTime = System.currentTimeMillis();
        if (failureCount >= failureThreshold) {
            state = State.OPEN;
        }
    }

    public State getState() { return state; }
}