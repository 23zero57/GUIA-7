package com.umb.taller.infrastructure.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:duckdb:evaldoc.duckdb";

    // Lista predefinida de palabras groseras o inapropiadas
    private static final List<String> BAD_WORDS = Arrays.asList(
            "idiota", "estupido", "estúpido", "imbecil", "imbécil", "inutil", "inútil",
            "basura", "malparido", "gonorrea", "mierda", "puto", "puta", "pendejo",
            "corrupto", "ladron", "ladrón", "vago", "mediocre", "burro", "asqueroso"
    );

    public static Connection getConnection() throws Exception {
        Class.forName("org.duckdb.DuckDBDriver");
        return DriverManager.getConnection(DB_URL);
    }

    public static void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Tabla original para compatibilidad con reportes de consola
            stmt.execute("CREATE TABLE IF NOT EXISTS evaluaciones (" +
                         "id INTEGER PRIMARY KEY, " +
                         "profesor VARCHAR, " +
                         "facultad VARCHAR, " +
                         "calificacion INTEGER, " +
                         "comentario VARCHAR)");

            stmt.execute("DELETE FROM evaluaciones");
            stmt.execute("INSERT INTO evaluaciones VALUES " +
                         "(1, 'Juan Jose Tabares', 'Ingeniería de Software', 9, 'Excelente manejo de arquitectura y pruebas'), " +
                         "(2, 'Maria Fernanda Perez', 'Ciencias Básicas', 8, 'Explicaciones muy claras en cálculo'), " +
                         "(3, 'Carlos Alberto Ruiz', 'Ingeniería de Sistemas', 10, 'Gran metodología y puntualidad'), " +
                         "(4, 'Ana Sofia Gomez', 'Ciencias de la Salud', 7, 'Falta profundizar en los laboratorios')");

            // 2. Nuevas tablas para el Sistema Completo UMB
            stmt.execute("CREATE TABLE IF NOT EXISTS usuarios (" +
                         "id INTEGER PRIMARY KEY, " +
                         "nombre VARCHAR, " +
                         "correo VARCHAR UNIQUE, " +
                         "password VARCHAR, " +
                         "rol VARCHAR, " +
                         "programa VARCHAR)");

            stmt.execute("CREATE TABLE IF NOT EXISTS profesores (" +
                         "id INTEGER PRIMARY KEY, " +
                         "nombre VARCHAR, " +
                         "correo VARCHAR, " +
                         "facultad VARCHAR, " +
                         "asignatura VARCHAR, " +
                         "escalafon VARCHAR)");

            stmt.execute("CREATE TABLE IF NOT EXISTS evaluaciones_umb (" +
                         "id INTEGER PRIMARY KEY, " +
                         "profesor_id INTEGER, " +
                         "estudiante_correo VARCHAR, " +
                         "estudiante_nombre VARCHAR, " +
                         "p1 INTEGER, p2 INTEGER, p3 INTEGER, p4 INTEGER, p5 INTEGER, " +
                         "p6 INTEGER, p7 INTEGER, p8 INTEGER, p9 INTEGER, p10 INTEGER, " +
                         "promedio DOUBLE, " +
                         "comentario VARCHAR, " +
                         "fecha VARCHAR, " +
                         "contiene_groseria BOOLEAN)");

            // Poblar usuarios iniciales si está vacía
            ResultSet rsU = stmt.executeQuery("SELECT COUNT(*) FROM usuarios");
            if (rsU.next() && rsU.getInt(1) == 0) {
                stmt.execute("INSERT INTO usuarios VALUES " +
                        "(1, 'Coordinador Académico UMB', 'admin@umb.edu.co', 'AdminUMB2026*', 'ADMIN', 'Dirección de Ingeniería'), " +
                        "(2, 'Nicolás Alejandro Sánchez', 'nicolas.sanchez@academia.umb.edu.co', 'Estudiante123*', 'ESTUDIANTE', 'Ingeniería de Software'), " +
                        "(3, 'Alexis Rodríguez', 'alexis.rodriguez@academia.umb.edu.co', 'Estudiante123*', 'ESTUDIANTE', 'Ingeniería de Software'), " +
                        "(4, 'Valentina Morales', 'valentina.morales@academia.umb.edu.co', 'Estudiante123*', 'ESTUDIANTE', 'Ingeniería Biomédica')");
            }

            // Poblar profesores iniciales si está vacía
            ResultSet rsP = stmt.executeQuery("SELECT COUNT(*) FROM profesores");
            if (rsP.next() && rsP.getInt(1) == 0) {
                stmt.execute("INSERT INTO profesores VALUES " +
                        "(1, 'Ing. Juan José Tabares', 'juan.tabares@docentes.umb.edu.co', 'Ingeniería de Software', 'Taller de Programación y Arquitectura', 'Magíster'), " +
                        "(2, 'Dra. María Fernanda Pérez', 'maria.perez@docentes.umb.edu.co', 'Ciencias Básicas', 'Ecuaciones Diferenciales y Física', 'Doctorado'), " +
                        "(3, 'Ing. Carlos Alberto Ruiz', 'carlos.ruiz@docentes.umb.edu.co', 'Ingeniería de Software', 'Bases de Datos Avanzadas y SQL', 'Especialista'), " +
                        "(4, 'Dra. Ana Sofía Gómez', 'ana.gomez@docentes.umb.edu.co', 'Ciencias de la Salud', 'Bioinformática Clínica', 'Magíster'), " +
                        "(5, 'Mg. Andrés Felipe Castro', 'andres.castro@docentes.umb.edu.co', 'Ingeniería Industrial', 'Investigación de Operaciones', 'Magíster')");
            }

            // Poblar evaluaciones de muestra del último mes si está vacía
            ResultSet rsE = stmt.executeQuery("SELECT COUNT(*) FROM evaluaciones_umb");
            if (rsE.next() && rsE.getInt(1) == 0) {
                String hoy = LocalDate.now().toString();
                stmt.execute("INSERT INTO evaluaciones_umb VALUES " +
                        "(1, 1, 'alexis.rodriguez@academia.umb.edu.co', 'Alexis Rodríguez', 10,10,9,10,9,10,9,10,10,10, 9.7, 'Excelente docente, explica muy bien patrones de diseño y pruebas con JUnit.', '" + hoy + "', false), " +
                        "(2, 2, 'valentina.morales@academia.umb.edu.co', 'Valentina Morales', 8,9,8,8,9,8,8,9,8,9, 8.4, 'Las clases son claras aunque los talleres son bastante extensos.', '" + hoy + "', false), " +
                        "(3, 3, 'nicolas.sanchez@academia.umb.edu.co', 'Nicolás Alejandro Sánchez', 9,9,10,9,9,10,9,9,9,10, 9.3, 'Muy buen dominio de motores SQL y modelado relacional.', '" + hoy + "', false)");
            }

            System.out.println("[INFO] DuckDB UMB inicializado con tablas de Usuarios, Profesores y Evaluaciones 360.");
        } catch (Exception e) {
            System.err.println("[ERROR] Error al inicializar DuckDB: " + e.getMessage());
        }
    }

    public static void printAnalyticsReport() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT facultad, AVG(calificacion) as promedio, COUNT(*) as total FROM evaluaciones GROUP BY facultad")) {

            System.out.println("\n==================================================");
            System.out.println("      EVALDOC - REPORTE ANALITICO (PROMEDIOS)");
            System.out.println("==================================================");
            while (rs.next()) {
                System.out.printf("Facultad: %-22s | Promedio: %.2f/10 | Total: %d\n",
                        rs.getString("facultad"), rs.getDouble("promedio"), rs.getInt("total"));
            }
            System.out.println("==================================================\n");
        } catch (Exception e) {
            System.err.println("[ERROR] Error en Query 1: " + e.getMessage());
        }
    }

    public static void printAdvancedAnalyticsReport() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT facultad, MAX(calificacion) as nota_max, MIN(calificacion) as nota_min, AVG(calificacion) as promedio, COUNT(*) as total FROM evaluaciones GROUP BY facultad")) {

            System.out.println("\n==================================================================");
            System.out.println("   EVALDOC - ESTADISTICAS DESCRIPTIVAS AVANZADAS (DUCKDB QUERY 2) ");
            System.out.println("==================================================================");
            while (rs.next()) {
                System.out.printf("Facultad: %-22s | Máx: %d | Mín: %d | Prom: %.2f | Total: %d\n",
                        rs.getString("facultad"), rs.getInt("nota_max"), rs.getInt("nota_min"),
                        rs.getDouble("promedio"), rs.getInt("total"));
            }
            System.out.println("==================================================================\n");
        } catch (Exception e) {
            System.err.println("[ERROR] Error en Query 2: " + e.getMessage());
        }
    }

    // --- LÓGICA DE FILTRO DE PALABRAS GROSERAS ---
    public static String checkProfanity(String text) {
        if (text == null) return null;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String bad : BAD_WORDS) {
            if (lower.contains(bad)) {
                return bad;
            }
        }
        return null;
    }

    // --- AUTENTICACIÓN DESDE DUCKDB ---
    public static String authenticateUserJson(String correo, String password, String token) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id, nombre, correo, rol, programa FROM usuarios WHERE LOWER(correo) = LOWER(?) AND password = ?")) {
            ps.setString(1, correo.trim());
            ps.setString(2, password.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return String.format("{\"id\":%d,\"nombre\":\"%s\",\"correo\":\"%s\",\"rol\":\"%s\",\"programa\":\"%s\",\"token\":\"%s\"}",
                            rs.getInt("id"), clean(rs.getString("nombre")), clean(rs.getString("correo")),
                            clean(rs.getString("rol")), clean(rs.getString("programa")), token);
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] authenticateUserJson: " + e.getMessage());
        }
        return null;
    }

    // --- OBTENER PROFESORES CON PROMEDIO Y TOTAL DE COMENTARIOS ---
    public static String getProfesoresJson(String filtroFacultad, String busqueda) {
        StringBuilder sb = new StringBuilder("[");
        String sql = "SELECT p.id, p.nombre, p.correo, p.facultad, p.asignatura, p.escalafon, " +
                     "COALESCE(ROUND(AVG(e.promedio), 2), 0.0) as prom_general, " +
                     "COUNT(e.id) as total_evaluaciones " +
                     "FROM profesores p LEFT JOIN evaluaciones_umb e ON p.id = e.profesor_id " +
                     "GROUP BY p.id, p.nombre, p.correo, p.facultad, p.asignatura, p.escalafon " +
                     "ORDER BY prom_general DESC, p.nombre ASC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            boolean first = true;
            while (rs.next()) {
                String fac = rs.getString("facultad");
                String nom = rs.getString("nombre");
                String asig = rs.getString("asignatura");

                if (filtroFacultad != null && !filtroFacultad.isEmpty() && !filtroFacultad.equalsIgnoreCase("TODAS")) {
                    if (!fac.equalsIgnoreCase(filtroFacultad)) continue;
                }
                if (busqueda != null && !busqueda.isEmpty()) {
                    String q = busqueda.toLowerCase(Locale.ROOT);
                    if (!nom.toLowerCase(Locale.ROOT).contains(q) && !asig.toLowerCase(Locale.ROOT).contains(q)) continue;
                }

                if (!first) sb.append(",");
                sb.append(String.format(Locale.US,
                        "{\"id\":%d,\"nombre\":\"%s\",\"correo\":\"%s\",\"facultad\":\"%s\",\"asignatura\":\"%s\",\"escalafon\":\"%s\",\"promedio\":%.2f,\"totalEvaluaciones\":%d}",
                        rs.getInt("id"), clean(nom), clean(rs.getString("correo")), clean(fac),
                        clean(asig), clean(rs.getString("escalafon")), rs.getDouble("prom_general"), rs.getInt("total_evaluaciones")));
                first = false;
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getProfesoresJson: " + e.getMessage());
        }
        sb.append("]");
        return sb.toString();
    }

    // --- DETALLE DE UN PROFESOR Y COMENTARIOS DEL ÚLTIMO MES ---
    public static String getProfesorDetalleJson(int profesorId) {
        StringBuilder sb = new StringBuilder("{");
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            ResultSet rsP = stmt.executeQuery("SELECT p.id, p.nombre, p.correo, p.facultad, p.asignatura, p.escalafon, " +
                    "COALESCE(ROUND(AVG(e.promedio), 2), 0.0) as prom_general, COUNT(e.id) as total_eval " +
                    "FROM profesores p LEFT JOIN evaluaciones_umb e ON p.id = e.profesor_id " +
                    "WHERE p.id = " + profesorId + " GROUP BY p.id, p.nombre, p.correo, p.facultad, p.asignatura, p.escalafon");

            if (rsP.next()) {
                sb.append(String.format(Locale.US,
                        "\"id\":%d,\"nombre\":\"%s\",\"correo\":\"%s\",\"facultad\":\"%s\",\"asignatura\":\"%s\",\"escalafon\":\"%s\",\"promedio\":%.2f,\"totalEvaluaciones\":%d,",
                        rsP.getInt("id"), clean(rsP.getString("nombre")), clean(rsP.getString("correo")),
                        clean(rsP.getString("facultad")), clean(rsP.getString("asignatura")),
                        clean(rsP.getString("escalafon")), rsP.getDouble("prom_general"), rsP.getInt("total_eval")));
            }

            // Comentarios del último mes (últimos 30 días)
            String fechaCorte = LocalDate.now().minusDays(30).toString();
            ResultSet rsC = stmt.executeQuery("SELECT id, estudiante_nombre, estudiante_correo, promedio, comentario, fecha, " +
                    "p1,p2,p3,p4,p5,p6,p7,p8,p9,p10 FROM evaluaciones_umb " +
                    "WHERE profesor_id = " + profesorId + " AND fecha >= '" + fechaCorte + "' ORDER BY id DESC");

            sb.append("\"comentarios\":[");
            boolean first = true;
            while (rsC.next()) {
                if (!first) sb.append(",");
                sb.append(String.format(Locale.US,
                        "{\"id\":%d,\"estudiante\":\"%s\",\"correo\":\"%s\",\"promedio\":%.2f,\"comentario\":\"%s\",\"fecha\":\"%s\"}",
                        rsC.getInt("id"), clean(rsC.getString("estudiante_nombre")), clean(rsC.getString("estudiante_correo")),
                        rsC.getDouble("promedio"), clean(rsC.getString("comentario")), clean(rsC.getString("fecha"))));
                first = false;
            }
            sb.append("]");
        } catch (Exception e) {
            System.err.println("[ERROR] getProfesorDetalleJson: " + e.getMessage());
        }
        sb.append("}");
        return sb.toString();
    }

    // --- REGISTRAR EVALUACIÓN DE 10 PREGUNTAS (CON REGLA DE 1 POR MES Y FILTRO DE GROSERÍAS) ---
    public static String registrarEvaluacionCompleta(int profesorId, String correoEst, String nombreEst, int[] preguntas, String comentario) throws Exception {
        // 1. Validar filtro de groserías
        String palabraDetectada = checkProfanity(comentario);
        if (palabraDetectada != null) {
            throw new IllegalArgumentException("Filtro Antipalabrotas UMB: Tu comentario contiene lenguaje inapropiado ('" + palabraDetectada + "'). Por favor redacta una crítica constructiva y respetuosa.");
        }

        // 2. Validar que el estudiante no haya evaluado a este profesor en el mes actual (YYYY-MM)
        String mesActual = LocalDate.now().toString().substring(0, 7); // Ejemplo: "2026-11"
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            ResultSet rsCheck = stmt.executeQuery("SELECT COUNT(*) FROM evaluaciones_umb WHERE profesor_id = " + profesorId +
                    " AND LOWER(estudiante_correo) = LOWER('" + clean(correoEst) + "') AND SUBSTRING(fecha, 1, 7) = '" + mesActual + "'");
            if (rsCheck.next() && rsCheck.getInt(1) > 0) {
                throw new IllegalStateException("Límite mensual alcanzado: Ya registraste tu evaluación mensual para este docente en el periodo " + mesActual + ".");
            }

            // 3. Calcular promedio de las 10 preguntas
            int suma = 0;
            for (int nota : preguntas) {
                if (nota < 1 || nota > 10) {
                    throw new IllegalArgumentException("Todas las 10 preguntas deben calificarse en escala de 1 a 10.");
                }
                suma += nota;
            }
            double promedio = Math.round((suma / 10.0) * 100.0) / 100.0;

            ResultSet rsId = stmt.executeQuery("SELECT COALESCE(MAX(id), 0) + 1 FROM evaluaciones_umb");
            int nextId = rsId.next() ? rsId.getInt(1) : 1;

            String sql = "INSERT INTO evaluaciones_umb VALUES (?, ?, ?, ?, ?,?,?,?,?,?,?,?,?,?, ?, ?, ?, false)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, nextId);
                ps.setInt(2, profesorId);
                ps.setString(3, correoEst.trim());
                ps.setString(4, nombreEst.trim());
                for (int i = 0; i < 10; i++) {
                    ps.setInt(5 + i, preguntas[i]);
                }
                ps.setDouble(15, promedio);
                ps.setString(16, comentario.trim());
                ps.setString(17, LocalDate.now().toString());
                ps.executeUpdate();
            }
            return String.format(Locale.US, "%.2f", promedio);
        }
    }

    // --- FUNCIONES EXCLUSIVAS DEL ADMINISTRADOR ---

    public static void agregarProfesor(String nombre, String correo, String facultad, String asignatura, String escalafon) throws Exception {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(id), 0) + 1 FROM profesores")) {
            int nextId = rs.next() ? rs.getInt(1) : 1;
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO profesores VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, nextId);
                ps.setString(2, nombre.trim());
                ps.setString(3, correo.trim());
                ps.setString(4, facultad.trim());
                ps.setString(5, asignatura.trim());
                ps.setString(6, escalafon.trim());
                ps.executeUpdate();
            }
        }
    }

    public static void eliminarProfesor(int id) throws Exception {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM evaluaciones_umb WHERE profesor_id = " + id);
            stmt.execute("DELETE FROM profesores WHERE id = " + id);
        }
    }

    public static void eliminarComentario(int evalId) throws Exception {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM evaluaciones_umb WHERE id = " + evalId);
        }
    }

    public static String getAllComentariosAdminJson(String filtroTexto) {
        StringBuilder sb = new StringBuilder("[");
        String sql = "SELECT e.id, p.nombre as profesor, p.facultad, e.estudiante_nombre, e.estudiante_correo, e.promedio, e.comentario, e.fecha " +
                     "FROM evaluaciones_umb e JOIN profesores p ON e.profesor_id = p.id ORDER BY e.id DESC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            boolean first = true;
            while (rs.next()) {
                String com = rs.getString("comentario");
                String prof = rs.getString("profesor");
                String est = rs.getString("estudiante_nombre");
                if (filtroTexto != null && !filtroTexto.isEmpty()) {
                    String q = filtroTexto.toLowerCase(Locale.ROOT);
                    if (!com.toLowerCase(Locale.ROOT).contains(q) &&
                        !prof.toLowerCase(Locale.ROOT).contains(q) &&
                        !est.toLowerCase(Locale.ROOT).contains(q)) {
                        continue;
                    }
                }
                if (!first) sb.append(",");
                sb.append(String.format(Locale.US,
                        "{\"id\":%d,\"profesor\":\"%s\",\"facultad\":\"%s\",\"estudiante\":\"%s\",\"correo\":\"%s\",\"promedio\":%.2f,\"comentario\":\"%s\",\"fecha\":\"%s\"}",
                        rs.getInt("id"), clean(prof), clean(rs.getString("facultad")), clean(est),
                        clean(rs.getString("estudiante_correo")), rs.getDouble("promedio"), clean(com), clean(rs.getString("fecha"))));
                first = false;
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getAllComentariosAdminJson: " + e.getMessage());
        }
        sb.append("]");
        return sb.toString();
    }

    public static String getUsuariosJson() {
        StringBuilder sb = new StringBuilder("[");
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, nombre, correo, rol, programa FROM usuarios ORDER BY id ASC")) {
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                sb.append(String.format("{\"id\":%d,\"nombre\":\"%s\",\"correo\":\"%s\",\"rol\":\"%s\",\"programa\":\"%s\"}",
                        rs.getInt("id"), clean(rs.getString("nombre")), clean(rs.getString("correo")),
                        clean(rs.getString("rol")), clean(rs.getString("programa"))));
                first = false;
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getUsuariosJson: " + e.getMessage());
        }
        sb.append("]");
        return sb.toString();
    }

    public static void crearUsuario(String nombre, String correo, String password, String rol, String programa) throws Exception {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(id), 0) + 1 FROM usuarios")) {
            int nextId = rs.next() ? rs.getInt(1) : 1;
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO usuarios VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, nextId);
                ps.setString(2, nombre.trim());
                ps.setString(3, correo.trim().toLowerCase(Locale.ROOT));
                ps.setString(4, password.trim());
                ps.setString(5, rol.trim());
                ps.setString(6, programa.trim());
                ps.executeUpdate();
            }
        }
    }

    public static void eliminarUsuario(int id) throws Exception {
        if (id == 1) {
            throw new IllegalArgumentException("No se puede eliminar la cuenta principal del Administrador UMB.");
        }
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM usuarios WHERE id = " + id);
        }
    }

    public static String getEstadisticasFacultadJson() {
        StringBuilder sb = new StringBuilder("[");
        String sql = "SELECT p.facultad, COUNT(e.id) as total_eval, COALESCE(ROUND(AVG(e.promedio), 2), 0.0) as promedio, " +
                     "COALESCE(MAX(e.promedio), 0.0) as max_nota, COALESCE(MIN(e.promedio), 0.0) as min_nota " +
                     "FROM profesores p LEFT JOIN evaluaciones_umb e ON p.id = e.profesor_id GROUP BY p.facultad";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                sb.append(String.format(Locale.US,
                        "{\"facultad\":\"%s\",\"total\":%d,\"promedio\":%.2f,\"max\":%.2f,\"min\":%.2f}",
                        clean(rs.getString("facultad")), rs.getInt("total_eval"), rs.getDouble("promedio"),
                        rs.getDouble("max_nota"), rs.getDouble("min_nota")));
                first = false;
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getEstadisticasFacultadJson: " + e.getMessage());
        }
        sb.append("]");
        return sb.toString();
    }

    private static String clean(String s) {
        if (s == null) return "";
        return s.replace("\"", "'").replace("\n", " ").replace("\r", "");
    }
}
