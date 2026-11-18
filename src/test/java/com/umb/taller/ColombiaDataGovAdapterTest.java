package com.umb.taller;

import com.umb.taller.infrastructure.adapter.ColombiaDataGovAdapter;
import com.umb.taller.infrastructure.resilience.CircuitBreaker;
import org.junit.jupiter.api.Test;
import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

public class ColombiaDataGovAdapterTest {

    @Test
    public void testDependencyInjectionAndAdapterInitialization() {
        HttpClient httpClientMock = mock(HttpClient.class);
        CircuitBreaker circuitBreaker = new CircuitBreaker(3, 1000L);

        ColombiaDataGovAdapter adapter = new ColombiaDataGovAdapter(httpClientMock, circuitBreaker);

        assertNotNull(adapter, "El adaptador debe inicializarse correctamente por constructor");
    }
}