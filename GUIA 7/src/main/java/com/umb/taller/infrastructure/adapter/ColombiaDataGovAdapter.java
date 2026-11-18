package com.umb.taller.infrastructure.adapter;

import com.umb.taller.infrastructure.resilience.CircuitBreaker;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ColombiaDataGovAdapter {

    private final HttpClient httpClient;
    private final CircuitBreaker circuitBreaker;

    public ColombiaDataGovAdapter(HttpClient httpClient, CircuitBreaker circuitBreaker) {
        this.httpClient = httpClient;
        this.circuitBreaker = circuitBreaker;
    }

    public String fetchData(String urlString) {
        return circuitBreaker.execute(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlString))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() != 200) {
                    throw new RuntimeException("Respuesta con status " + response.statusCode());
                }
                return response.body();
            } catch (Exception e) {
                throw new RuntimeException("Fallo en peticion HTTP: " + e.getMessage(), e);
            }
        });
    }
}