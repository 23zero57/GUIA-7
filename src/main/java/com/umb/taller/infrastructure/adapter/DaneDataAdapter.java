package com.umb.taller.infrastructure.adapter;

import com.umb.taller.infrastructure.resilience.CircuitBreaker;
import com.umb.taller.infrastructure.resilience.RetryHelper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class DaneDataAdapter {
    private final HttpClient httpClient;
    private final CircuitBreaker circuitBreaker;

    public DaneDataAdapter(HttpClient httpClient, CircuitBreaker circuitBreaker) {
        this.httpClient = httpClient;
        this.circuitBreaker = circuitBreaker;
    }

    public String fetchDaneData(String urlString) {
        // Protegido por Circuit Breaker Y por Retry con Backoff Exponencial
        return circuitBreaker.execute(() -> 
            RetryHelper.executeWithRetry(() -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(urlString))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("HTTP Error Status: " + response.statusCode());
                    }
                    return response.body();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, 3, 1000L) // 3 intentos máximos con espera inicial de 1 segundo
        );
    }
}