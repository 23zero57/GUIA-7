package com.umb.taller.infrastructure.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:duckdb:evaldoc.duckdb";

    public static Connection getConnection() throws Exception {
        Class.forName("org.duckdb.DuckDBDriver");
        return DriverManager.getConnection(DB_URL);
    }

    public static void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("CREATE TABLE IF NOT EXISTS evaluaciones (" +
                         "id INTEGER PRIMARY KEY, " +
                         "profesor VARCHAR, " +
                         "facultad VARCHAR, " +
                         "calificacion INTEGER, " +
                         "comentario VARCHAR)");

            stmt.execute("DELETE FROM evaluaciones");
            stmt.execute("INSERT INTO evaluaciones VALUES " +
                         "(1, 'Juan Jose Osorio', 'Ingenieria', 9, 'Excelente manejo de la materia'), " +
                         "(2, 'Maria Perez', 'Ciencias', 8, 'Explicaciones muy claras'), " +
                         "(3, 'Carlos Ruiz', 'Ingenieria', 10, 'Gran metodologia y puntualidad'), " +
                         "(4, 'Ana Gomez', 'Ciencias', 7, 'Falta profundizar en los temas')");

            System.out.println("[INFO] DuckDB inicializado con datos de prueba.");
        } catch (Exception e) {
            System.err.println("[ERROR] Error al inicializar DuckDB: " + e.getMessage());
        }
    }

    // Query 1: Reporte analítico básico por facultad
    public static void printAnalyticsReport() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT facultad, AVG(calificacion) as promedio, COUNT(*) as total FROM evaluaciones GROUP BY facultad")) {

            System.out.println("\n==================================================");
            System.out.println("      EVALDOC - REPORTE ANALITICO (PROMEDIOS)");
            System.out.println("==================================================");
            while (rs.next()) {
                System.out.printf("Facultad: %-12s | Promedio: %.2f/10 | Total: %d\n", 
                        rs.getString("facultad"), rs.getDouble("promedio"), rs.getInt("total"));
            }
            System.out.println("==================================================\n");
        } catch (Exception e) {
            System.err.println("[ERROR] Error en Query 1: " + e.getMessage());
        }
    }

    // Query 2: Estadísticas descriptivas avanzadas (Máximo, Mínimo, Promedio y Conteo)
    public static void printAdvancedAnalyticsReport() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT facultad, MAX(calificacion) as nota_max, MIN(calificacion) as nota_min, AVG(calificacion) as promedio, COUNT(*) as total FROM evaluaciones GROUP BY facultad")) {

            System.out.println("\n==================================================================");
            System.out.println("   EVALDOC - ESTADISTICAS DESCRIPTIVAS AVANZADAS (DUCKDB QUERY 2) ");
            System.out.println("==================================================================");
            while (rs.next()) {
                System.out.printf("Facultad: %-12s | Máx: %d | Mín: %d | Prom: %.2f | Total: %d\n", 
                        rs.getString("facultad"), rs.getInt("nota_max"), rs.getInt("nota_min"), 
                        rs.getDouble("promedio"), rs.getInt("total"));
            }
            System.out.println("==================================================================\n");
        } catch (Exception e) {
            System.err.println("[ERROR] Error en Query 2: " + e.getMessage());
        }
    }
}