package com.umb.taller;

import com.umb.taller.infrastructure.adapter.ColombiaDataGovAdapter;
import com.umb.taller.infrastructure.adapter.DaneDataAdapter;
import com.umb.taller.infrastructure.db.DatabaseManager;
import com.umb.taller.infrastructure.resilience.CircuitBreaker;
import com.umb.taller.infrastructure.security.SecurityService;
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

        // 5. Demostración de Autenticación JWT y Control de Acceso RBAC (Guía 7)
        System.out.println("\n[INFO] Probando Seguridad JWT y RBAC (Guía 7)...");
        SecurityService securityService = new SecurityService();
        
        try {
            // Test Login ADMIN
            String adminToken = securityService.authenticate("admin_evaldoc", "Admin123*");
            System.out.println("[AUTH SUCCESS] Token generado para ADMIN: " + adminToken.substring(0, Math.min(adminToken.length(), 30)) + "...");

            // Test RBAC Authorization
            boolean canAccessAdminPanel = securityService.authorize(adminToken, "ADMIN");
            System.out.println("[RBAC AUTHORIZATION] ¿Acceso de ADMIN a panel directivo?: " + canAccessAdminPanel);

            // Test Login USER
            String userToken = securityService.authenticate("user_docente", "Docente2026*");
            boolean userTriesAdmin = securityService.authorize(userToken, "ADMIN");
            System.out.println("[RBAC AUTHORIZATION] ¿Acceso de USER a panel directivo (Debe ser falso)?: " + userTriesAdmin);

        } catch (Exception e) {
            System.err.println("[SECURITY ERROR]: " + e.getMessage());
        }
    }
}