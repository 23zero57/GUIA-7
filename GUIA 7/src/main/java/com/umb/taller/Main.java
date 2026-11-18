package com.umb.taller;

import com.umb.taller.infrastructure.adapter.ColombiaDataGovAdapter;
import com.umb.taller.infrastructure.adapter.DaneDataAdapter;
import com.umb.taller.infrastructure.db.DatabaseManager;
import com.umb.taller.infrastructure.resilience.CircuitBreaker;
import java.net.http.HttpClient;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== INICIANDO APLICACION EVALDOC ===");

        // 1. Inicialización de DuckDB y ejecución de las DOS queries analíticas
        DatabaseManager.initializeDatabase();
        DatabaseManager.printAnalyticsReport();         // Query 1
        DatabaseManager.printAdvancedAnalyticsReport(); // Query 2 (Estadísticas descriptivas)

        // 2. Configuración de clientes y Circuit Breakers para las 2 APIs
        HttpClient httpClient = HttpClient.newHttpClient();
        CircuitBreaker cb1 = new CircuitBreaker(3, 2000L);
        CircuitBreaker cb2 = new CircuitBreaker(3, 2000L);

        // 3. API 1: Datos Abiertos (Colombia)
        ColombiaDataGovAdapter adapter1 = new ColombiaDataGovAdapter(httpClient, cb1);
        System.out.println("[INFO] Probando API 1 (Datos Abiertos)...");
        try {
            String res1 = adapter1.fetchData("https://www.datos.gov.co/resource/3hgb-ggvv.json?$limit=1");
            System.out.println("[API 1 Exitosa]: " + res1.substring(0, Math.min(res1.length(), 80)) + "...");
        } catch (Exception e) {
            System.out.println("[API 1 Protegida]: " + e.getMessage());
        }

        // 4. API 2: Segunda fuente colombiana con Retry y Backoff Exponencial
        DaneDataAdapter adapter2 = new DaneDataAdapter(httpClient, cb2);
        System.out.println("\n[INFO] Probando API 2 con Retry y Backoff Exponencial...");
        try {
            String res2 = adapter2.fetchDaneData("https://www.datos.gov.co/resource/gt2z-vqpa.json?$limit=1");
            System.out.println("[API 2 Exitosa]: " + res2.substring(0, Math.min(res2.length(), 80)) + "...");
        } catch (Exception e) {
            System.out.println("[API 2 Protegida con Backoff]: " + e.getMessage());
        }
    }
}