package com.umb.taller.infrastructure.resilience;

import java.util.function.Supplier;

public class RetryHelper {
    public static <T> T executeWithRetry(Supplier<T> supplier, int maxRetries, long initialWaitMillis) {
        int attempts = 0;
        long currentWait = initialWaitMillis;
        
        while (true) {
            try {
                attempts++;
                return supplier.get();
            } catch (Exception e) {
                if (attempts >= maxRetries) {
                    throw new RuntimeException("Se agotaron los reintentos (" + maxRetries + "). Error: " + e.getMessage(), e);
                }
                System.out.println("[RETRY] Intento " + attempts + " falló. Reintentando en " + currentWait + " ms...");
                try {
                    Thread.sleep(currentWait);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Reintento interrumpido", ie);
                }
                currentWait *= 2; // Duplica el tiempo de espera (Backoff exponencial)
            }
        }
    }
}