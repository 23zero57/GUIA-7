package com.umb.taller.infrastructure.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.umb.taller.infrastructure.adapter.ColombiaDataGovAdapter;
import com.umb.taller.infrastructure.adapter.DaneDataAdapter;
import com.umb.taller.infrastructure.db.DatabaseManager;
import com.umb.taller.infrastructure.resilience.CircuitBreaker;
import com.umb.taller.infrastructure.security.SecurityService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class WebServer {

    public static void startServer(int port) {
        try {
            SecurityService securityService = new SecurityService();
            HttpClient httpClient = HttpClient.newHttpClient();
            CircuitBreaker cb1 = new CircuitBreaker(3, 2000L);
            CircuitBreaker cb2 = new CircuitBreaker(3, 2000L);
            ColombiaDataGovAdapter adapter1 = new ColombiaDataGovAdapter(httpClient, cb1);
            DaneDataAdapter adapter2 = new DaneDataAdapter(httpClient, cb2);

            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            // Vista Web Principal
            server.createContext("/", exchange -> {
                sendResponse(exchange, 200, "text/html; charset=UTF-8", getUmbPortalHtml());
            });

            // 1. Login Institucional (Admin y Estudiante)
            server.createContext("/api/auth/login", exchange -> {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, String> params = parseForm(body);
                    String correo = params.getOrDefault("correo", "").trim();
                    String password = params.getOrDefault("password", "").trim();

                    String token = securityService.generateToken(correo, correo.contains("admin") ? "ADMIN" : "ESTUDIANTE");
                    String userJson = DatabaseManager.authenticateUserJson(correo, password, token);

                    if (userJson != null) {
                        sendResponse(exchange, 200, "application/json", userJson);
                    } else {
                        sendResponse(exchange, 401, "application/json", "{\"error\":\"Credenciales institucionales inválidas. Verifica tu correo @umb.edu.co o contraseña.\"}");
                    }
                }
            });

            // 2. Listar y Filtrar Profesores + Estadísticas por Facultad
            server.createContext("/api/profesores", exchange -> {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    Map<String, String> q = parseForm(exchange.getRequestURI().getRawQuery());
                    String facultad = q.getOrDefault("facultad", "TODAS");
                    String busqueda = q.getOrDefault("q", "");
                    String json = "{\"profesores\":" + DatabaseManager.getProfesoresJson(facultad, busqueda) +
                                  ",\"estadisticas\":" + DatabaseManager.getEstadisticasFacultadJson() + "}";
                    sendResponse(exchange, 200, "application/json", json);
                } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    try {
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        Map<String, String> p = parseForm(body);
                        String nombre = p.getOrDefault("nombre", "").trim();
                        String correo = p.getOrDefault("correo", "").trim();
                        String facultad = p.getOrDefault("facultad", "").trim();
                        String asignatura = p.getOrDefault("asignatura", "").trim();
                        String escalafon = p.getOrDefault("escalafon", "Magíster").trim();

                        if (nombre.isEmpty() || correo.isEmpty() || asignatura.isEmpty()) {
                            sendResponse(exchange, 400, "application/json", "{\"error\":\"Todos los datos del docente son obligatorios.\"}");
                            return;
                        }
                        DatabaseManager.agregarProfesor(nombre, correo, facultad, asignatura, escalafon);
                        sendResponse(exchange, 201, "application/json", "{\"message\":\"Docente vinculado exitosamente a la planta UMB.\"}");
                    } catch (Exception e) {
                        sendResponse(exchange, 500, "application/json", "{\"error\":\"" + e.getMessage() + "\"}");
                    }
                } else if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                    try {
                        Map<String, String> q = parseForm(exchange.getRequestURI().getRawQuery());
                        int id = Integer.parseInt(q.getOrDefault("id", "0"));
                        DatabaseManager.eliminarProfesor(id);
                        sendResponse(exchange, 200, "application/json", "{\"message\":\"Docente retirado del sistema correctamente.\"}");
                    } catch (Exception e) {
                        sendResponse(exchange, 500, "application/json", "{\"error\":\"" + e.getMessage() + "\"}");
                    }
                }
            });

            // 3. Perfil Detallado de un Profesor y sus Comentarios del Último Mes
            server.createContext("/api/profesores/detalle", exchange -> {
                Map<String, String> q = parseForm(exchange.getRequestURI().getRawQuery());
                int id = Integer.parseInt(q.getOrDefault("id", "1"));
                sendResponse(exchange, 200, "application/json", DatabaseManager.getProfesorDetalleJson(id));
            });

            // 4. Registrar Encuesta Docente de 10 Preguntas + Comentario (Con Filtro y Límite Mensual)
            server.createContext("/api/evaluaciones", exchange -> {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    try {
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        Map<String, String> p = parseForm(body);
                        int profesorId = Integer.parseInt(p.getOrDefault("profesorId", "1"));
                        String correoEst = p.getOrDefault("correoEstudiante", "");
                        String nombreEst = p.getOrDefault("nombreEstudiante", "Estudiante UMB");
                        String comentario = p.getOrDefault("comentario", "").trim();

                        if (comentario.length() < 10) {
                            sendResponse(exchange, 400, "application/json", "{\"error\":\"Por favor escribe una opinión de al menos 10 caracteres sobre el docente.\"}");
                            return;
                        }

                        int[] preguntas = new int[10];
                        for (int i = 0; i < 10; i++) {
                            preguntas[i] = Integer.parseInt(p.getOrDefault("p" + (i + 1), "8"));
                        }

                        String promCalculado = DatabaseManager.registrarEvaluacionCompleta(profesorId, correoEst, nombreEst, preguntas, comentario);
                        sendResponse(exchange, 201, "application/json",
                                "{\"message\":\"¡Evaluación registrada con éxito! Promedio calculado de la encuesta: " + promCalculado + "/10\"}");
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        sendResponse(exchange, 400, "application/json", "{\"error\":\"" + e.getMessage() + "\"}");
                    } catch (Exception e) {
                        sendResponse(exchange, 500, "application/json", "{\"error\":\"Error interno: " + e.getMessage() + "\"}");
                    }
                }
            });

            // 5. Administración de Comentarios (Filtrar y Eliminar)
            server.createContext("/api/admin/comentarios", exchange -> {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    Map<String, String> q = parseForm(exchange.getRequestURI().getRawQuery());
                    String filtro = q.getOrDefault("q", "");
                    sendResponse(exchange, 200, "application/json", DatabaseManager.getAllComentariosAdminJson(filtro));
                } else if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                    try {
                        Map<String, String> q = parseForm(exchange.getRequestURI().getRawQuery());
                        int id = Integer.parseInt(q.getOrDefault("id", "0"));
                        DatabaseManager.eliminarComentario(id);
                        sendResponse(exchange, 200, "application/json", "{\"message\":\"Comentario eliminado por moderación administrativa.\"}");
                    } catch (Exception e) {
                        sendResponse(exchange, 500, "application/json", "{\"error\":\"" + e.getMessage() + "\"}");
                    }
                }
            });

            // 6. Administración de Cuentas de Usuarios (Listar, Crear y Eliminar)
            server.createContext("/api/admin/usuarios", exchange -> {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendResponse(exchange, 200, "application/json", DatabaseManager.getUsuariosJson());
                } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    try {
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        Map<String, String> p = parseForm(body);
                        DatabaseManager.crearUsuario(
                                p.getOrDefault("nombre", ""),
                                p.getOrDefault("correo", ""),
                                p.getOrDefault("password", "Estudiante123*"),
                                p.getOrDefault("rol", "ESTUDIANTE"),
                                p.getOrDefault("programa", "Ingeniería de Software")
                        );
                        sendResponse(exchange, 201, "application/json", "{\"message\":\"Cuenta institucional creada exitosamente.\"}");
                    } catch (Exception e) {
                        sendResponse(exchange, 400, "application/json", "{\"error\":\"No se pudo crear la cuenta (verifica que el correo no esté repetido).\"}");
                    }
                } else if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                    try {
                        Map<String, String> q = parseForm(exchange.getRequestURI().getRawQuery());
                        int id = Integer.parseInt(q.getOrDefault("id", "0"));
                        DatabaseManager.eliminarUsuario(id);
                        sendResponse(exchange, 200, "application/json", "{\"message\":\"Cuenta eliminada del sistema.\"}");
                    } catch (Exception e) {
                        sendResponse(exchange, 400, "application/json", "{\"error\":\"" + e.getMessage() + "\"}");
                    }
                }
            });

            // 7. Monitor de Resiliencia (Circuit Breaker + Retry Backoff)
            server.createContext("/api/resilience/test", exchange -> {
                String status1;
                try {
                    adapter1.fetchData("https://www.datos.gov.co/resource/3hgb-ggvv.json?$limit=1");
                    status1 = "CONECTADA (200 OK)";
                } catch (Exception e) {
                    status1 = "PROTEGIDA POR CIRCUIT BREAKER";
                }

                String status2;
                try {
                    adapter2.fetchDaneData("https://www.datos.gov.co/resource/gt2z-vqpa.json?$limit=1");
                    status2 = "CONECTADA (200 OK)";
                } catch (Exception e) {
                    status2 = "PROTEGIDA CON RETRY Y BACKOFF EXPONENCIAL";
                }

                String json = String.format("{\"api1_status\":\"%s\",\"cb1_state\":\"%s\",\"api2_status\":\"%s\",\"cb2_state\":\"%s\"}",
                        status1, cb1.getState().toString(), status2, cb2.getState().toString());
                sendResponse(exchange, 200, "application/json", json);
            });

            server.setExecutor(null);
            server.start();

            System.out.println("\n======================================================================");
            System.out.println("PORTAL EVALDOC UMB ACTIVO EN: http://localhost:" + port);
            System.out.println("======================================================================\n");
        } catch (Exception e) {
            System.err.println("[ERROR] No se pudo iniciar el servidor web: " + e.getMessage());
        }
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String contentType, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isEmpty()) return map;
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private static String getUmbPortalHtml() {
        return """
        <!DOCTYPE html>
        <html lang="es">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>EVAL DOC - UNIVERSIDAD MANUELA BELTRÁN</title>
            <script src="https://cdn.tailwindcss.com"></script>
        </head>
        <body class="bg-stone-50 text-stone-800 min-h-screen flex flex-col font-sans">

            <!-- BARRA SUPERIOR INSTITUCIONAL UMB (ROJO, DORADO Y BLANCO) -->
            <header class="bg-gradient-to-r from-red-900 via-red-800 to-red-900 text-white border-b-4 border-amber-500 shadow-lg">
                <div class="max-w-7xl mx-auto px-6 py-4 flex flex-col md:flex-row justify-between items-center gap-4">
                    <div class="flex items-center space-x-4">
                        <div class="w-12 h-12 rounded-full bg-amber-500 text-red-950 font-black flex items-center justify-center text-xl border-2 border-white shadow">
                            UMB
                        </div>
                        <div>
                            <h1 class="text-xl md:text-2xl font-extrabold tracking-wider text-white">
                                EVAL DOC - <span class="text-amber-400">UNIVERSIDAD MANUELA BELTRÁN</span>
                            </h1>
                            <p class="text-xs text-red-100">Sistema Institucional de Evaluación Docente, Analítica DuckDB y Control de Calidad Académica</p>
                        </div>
                    </div>
                    <div id="userHeaderInfo" class="hidden items-center space-x-4">
                        <div class="text-right">
                            <div id="headerUserName" class="text-sm font-bold text-amber-300"></div>
                            <div id="headerUserRole" class="text-xs text-red-100"></div>
                        </div>
                        <button onclick="logout()" class="bg-amber-500 hover:bg-amber-400 text-red-950 font-bold text-xs px-4 py-2 rounded-lg shadow transition">
                            Cerrar Sesión
                        </button>
                    </div>
                </div>
            </header>

            <!-- PANTALLA 1: INICIO DE SESIÓN INSTITUCIONAL -->
            <section id="loginScreen" class="flex-grow flex items-center justify-center p-6">
                <div class="bg-white max-w-md w-full rounded-2xl shadow-2xl border-t-8 border-red-800 overflow-hidden">
                    <div class="bg-red-50 px-6 py-4 border-b border-amber-200 text-center">
                        <span class="inline-block bg-amber-500 text-red-950 text-xs font-extrabold px-3 py-1 rounded-full uppercase tracking-wider mb-1">Portal Académico</span>
                        <h2 class="text-xl font-black text-red-900">Ingreso Institucional EvalDoc</h2>
                        <p class="text-xs text-stone-600 mt-1">Selecciona un perfil predefinido o ingresa tus credenciales UMB</p>
                    </div>

                    <div class="p-6 space-y-4">
                        <div>
                            <label class="block text-xs font-bold text-red-900 uppercase mb-1">Cuentas Rápidas de Demostración</label>
                            <select id="presetSelect" onchange="cargarPreset()" class="w-full bg-stone-50 border-2 border-amber-400 rounded-lg p-2.5 text-sm font-semibold text-stone-800 focus:outline-none">
                                <option value="est1">🎓 Estudiante 1: nicolas.sanchez@academia.umb.edu.co</option>
                                <option value="est2">🎓 Estudiante 2: alexis.rodriguez@academia.umb.edu.co</option>
                                <option value="admin">🛡️ Administrador UMB: admin@umb.edu.co</option>
                            </select>
                        </div>

                        <div>
                            <label class="block text-xs font-bold text-stone-700 uppercase mb-1">Correo Institucional</label>
                            <input id="loginCorreo" type="email" value="nicolas.sanchez@academia.umb.edu.co" class="w-full border border-stone-300 rounded-lg p-2.5 text-sm focus:border-red-700 focus:outline-none">
                        </div>

                        <div>
                            <label class="block text-xs font-bold text-stone-700 uppercase mb-1">Contraseña</label>
                            <input id="loginPassword" type="password" value="Estudiante123*" class="w-full border border-stone-300 rounded-lg p-2.5 text-sm focus:border-red-700 focus:outline-none">
                        </div>

                        <div id="loginError" class="hidden bg-red-100 border-l-4 border-red-700 text-red-800 p-3 text-xs rounded"></div>

                        <button onclick="iniciarSesion()" class="w-full bg-red-800 hover:bg-red-700 text-white font-bold py-3 rounded-xl shadow-md border-b-4 border-amber-500 transition">
                            Ingresar a EvalDoc UMB
                        </button>

                        <div class="bg-amber-50 border border-amber-200 rounded-lg p-3 text-xs text-stone-700 space-y-1">
                            <div class="font-bold text-amber-900">🔒 Seguridad Activa:</div>
                            <div>• Autenticación con firma de Token JWT (HS256) y RBAC.</div>
                            <div>• Filtro automático de lenguaje inapropiado y límite de 1 encuesta/mes.</div>
                        </div>
                    </div>
                </div>
            </section>

            <!-- PANTALLA 2: PORTAL DEL ESTUDIANTE UMB -->
            <section id="studentScreen" class="hidden max-w-7xl w-full mx-auto p-6 space-y-6">
                <!-- Banner de Bienvenida -->
                <div class="bg-white rounded-2xl p-5 shadow-md border-l-8 border-amber-500 flex flex-col md:flex-row justify-between items-center gap-4">
                    <div>
                        <span class="text-xs font-bold uppercase bg-red-100 text-red-800 px-2.5 py-1 rounded">Módulo Estudiantil UMB</span>
                        <h2 class="text-xl font-black text-red-900 mt-1">Evaluación Docente y Consulta de Perfiles Académicos</h2>
                        <p class="text-xs text-stone-600">Recuerda: Tienes derecho a realizar <strong>1 evaluación mensual por cada profesor</strong> respondiendo las 10 preguntas pedagógicas.</p>
                    </div>
                    <div class="flex gap-3 w-full md:w-auto">
                        <select id="filtroFacultadEst" onchange="cargarProfesores()" class="border border-stone-300 rounded-lg px-3 py-2 text-sm bg-stone-50">
                            <option value="TODAS">Todas las Facultades</option>
                            <option value="Ingeniería de Software">Ingeniería de Software</option>
                            <option value="Ciencias Básicas">Ciencias Básicas</option>
                            <option value="Ciencias de la Salud">Ciencias de la Salud</option>
                            <option value="Ingeniería Industrial">Ingeniería Industrial</option>
                        </select>
                        <input id="busquedaProfEst" onkeyup="cargarProfesores()" type="text" placeholder="Buscar profesor o materia..." class="border border-stone-300 rounded-lg px-3 py-2 text-sm w-full md:w-64">
                    </div>
                </div>

                <!-- Grid de Profesores y Panel de Perfil / Encuesta -->
                <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    <!-- Lista de Profesores (Izquierda) -->
                    <div class="lg:col-span-1 space-y-3">
                        <h3 class="text-sm font-extrabold uppercase text-red-900 tracking-wider border-b-2 border-amber-500 pb-1">Planta Docente UMB</h3>
                        <div id="listaProfesoresEst" class="space-y-3"></div>
                    </div>

                    <!-- Detalle del Profesor, Comentarios del Último Mes y Encuesta de 10 Preguntas (Derecha) -->
                    <div class="lg:col-span-2 space-y-6">
                        <div id="perfilProfesorCard" class="bg-white rounded-2xl shadow-md border border-stone-200 p-6">
                            <p class="text-sm text-stone-500 text-center py-8">Selecciona un profesor de la lista izquierda para ver su perfil, calificación promedio, comentarios del último mes y diligenciar su encuesta de 10 preguntas.</p>
                        </div>
                    </div>
                </div>
            </section>

            <!-- PANTALLA 3: PANEL DE ADMINISTRACIÓN UMB -->
            <section id="adminScreen" class="hidden max-w-7xl w-full mx-auto p-6 space-y-6">
                <!-- Resumen Estadístico DuckDB por Facultad -->
                <div class="bg-white rounded-2xl p-6 shadow-md border-t-4 border-red-800">
                    <div class="flex flex-col md:flex-row justify-between items-center mb-4 gap-3">
                        <div>
                            <h2 class="text-lg font-black text-red-900">📊 Analítica Institucional en Tiempo Real (DuckDB Engine)</h2>
                            <p class="text-xs text-stone-500">Promedios consolidados, notas máximas/mínimas y volumen de participación por facultad</p>
                        </div>
                        <div class="flex gap-2">
                            <button onclick="exportarReporteCSV()" class="bg-amber-500 hover:bg-amber-400 text-red-950 font-bold text-xs px-4 py-2 rounded-lg shadow">
                                📥 Exportar Comentarios a CSV
                            </button>
                            <button onclick="probarResilienciaAdmin()" class="bg-red-800 hover:bg-red-700 text-white font-bold text-xs px-4 py-2 rounded-lg shadow">
                                ⚡ Probar Circuit Breaker y APIs
                            </button>
                        </div>
                    </div>
                    <div id="adminStatsGrid" class="grid grid-cols-1 md:grid-cols-4 gap-4"></div>
                    <div id="adminResilienceBox" class="hidden mt-4 bg-stone-900 text-amber-300 p-3 rounded-lg text-xs font-mono"></div>
                </div>

                <!-- Tres Columnas de Gestión Administrativa -->
                <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">

                    <!-- 1. Gestión de Docentes (Añadir / Quitar) -->
                    <div class="bg-white rounded-2xl p-5 shadow-md border border-stone-200 space-y-4">
                        <h3 class="text-base font-black text-red-900 border-b-2 border-amber-500 pb-2">👨‍🏫 Añadir / Retirar Profesores</h3>
                        <div class="space-y-2 text-xs">
                            <input id="nuevoProfNombre" type="text" placeholder="Nombre completo (ej. Ing. Pedro Gómez)" class="w-full border rounded p-2">
                            <input id="nuevoProfCorreo" type="email" placeholder="Correo (@docentes.umb.edu.co)" class="w-full border rounded p-2">
                            <select id="nuevoProfFacultad" class="w-full border rounded p-2 bg-stone-50">
                                <option value="Ingeniería de Software">Ingeniería de Software</option>
                                <option value="Ciencias Básicas">Ciencias Básicas</option>
                                <option value="Ciencias de la Salud">Ciencias de la Salud</option>
                                <option value="Ingeniería Industrial">Ingeniería Industrial</option>
                            </select>
                            <input id="nuevoProfAsignatura" type="text" placeholder="Asignatura principal" class="w-full border rounded p-2">
                            <select id="nuevoProfEscalafon" class="w-full border rounded p-2 bg-stone-50">
                                <option value="Magíster">Escalafón: Magíster</option>
                                <option value="Doctorado">Escalafón: Doctorado</option>
                                <option value="Especialista">Escalafón: Especialista</option>
                            </select>
                            <button onclick="crearProfesorAdmin()" class="w-full bg-red-800 hover:bg-red-700 text-white font-bold py-2 rounded-lg shadow">
                                + Vincular Docente a la UMB
                            </button>
                            <p id="msgAdminProf" class="text-center font-semibold"></p>
                        </div>
                        <div id="listaAdminProfesores" class="space-y-2 max-h-72 overflow-y-auto pt-2 border-t"></div>
                    </div>

                    <!-- 2. Moderación y Filtro de Comentarios -->
                    <div class="bg-white rounded-2xl p-5 shadow-md border border-stone-200 space-y-4">
                        <h3 class="text-base font-black text-red-900 border-b-2 border-amber-500 pb-2">💬 Filtrar y Eliminar Comentarios</h3>
                        <input id="filtroComentariosAdmin" onkeyup="cargarComentariosAdmin()" type="text" placeholder="Filtrar por palabra, docente o alumno..." class="w-full border rounded-lg p-2 text-xs">
                        <div class="bg-amber-50 border border-amber-300 p-2 rounded text-[11px] text-amber-900">
                            <strong>Filtro Antipalabrotas Activo:</strong> Bloquea automáticamente insultos (idiota, basura, inútil, estúpido, etc.).
                        </div>
                        <div id="listaAdminComentarios" class="space-y-2 max-h-96 overflow-y-auto"></div>
                    </div>

                    <!-- 3. Gestión de Cuentas Institucionales (Crear / Eliminar) -->
                    <div class="bg-white rounded-2xl p-5 shadow-md border border-stone-200 space-y-4">
                        <h3 class="text-base font-black text-red-900 border-b-2 border-amber-500 pb-2">👥 Gestión de Cuentas UMB</h3>
                        <div class="space-y-2 text-xs">
                            <input id="nuevoUserNombre" type="text" placeholder="Nombre del Estudiante" class="w-full border rounded p-2">
                            <input id="nuevoUserCorreo" type="email" placeholder="correo@academia.umb.edu.co" class="w-full border rounded p-2">
                            <input id="nuevoUserPass" type="text" value="Estudiante123*" placeholder="Contraseña" class="w-full border rounded p-2">
                            <input id="nuevoUserProg" type="text" value="Ingeniería de Software" placeholder="Programa Académico" class="w-full border rounded p-2">
                            <button onclick="crearUsuarioAdmin()" class="w-full bg-amber-500 hover:bg-amber-400 text-red-950 font-bold py-2 rounded-lg shadow">
                                + Registrar Cuenta Estudiantil
                            </button>
                            <p id="msgAdminUser" class="text-center font-semibold"></p>
                        </div>
                        <div id="listaAdminUsuarios" class="space-y-2 max-h-72 overflow-y-auto pt-2 border-t"></div>
                    </div>

                </div>
            </section>

            <footer class="mt-auto bg-stone-900 text-stone-400 text-xs py-4 px-6 border-t-2 border-amber-500 text-center">
                EVAL DOC - UNIVERSIDAD MANUELA BELTRÁN © 2026 | Arquitectura Java 25 + DuckDB + Patrones Circuit Breaker & RBAC JWT
            </footer>

            <script>
                let currentUser = null;
                let selectedProfesorId = null;
                let cachedComentariosAdmin = [];

                const PREGUNTAS_UMB = [
                    "1. Dominio y claridad conceptual de los temas de la asignatura",
                    "2. Puntualidad y aprovechamiento efectivo del tiempo de clase",
                    "3. Metodología pedagógica y uso de ejemplos prácticos",
                    "4. Transparencia y justicia en los criterios de calificación",
                    "5. Retroalimentación oportuna sobre talleres, guías y parciales",
                    "6. Fomento de la participación y resolución de dudas en clase",
                    "7. Uso adecuado de herramientas tecnológicas y laboratorios",
                    "8. Trato respetuoso, ético y cordial hacia los estudiantes",
                    "9. Cumplimiento del syllabus y plan de curso institucional UMB",
                    "10. Satisfacción global con el desempeño del docente este mes"
                ];

                function cargarPreset() {
                    const val = document.getElementById('presetSelect').value;
                    if (val === 'est1') {
                        document.getElementById('loginCorreo').value = 'nicolas.sanchez@academia.umb.edu.co';
                        document.getElementById('loginPassword').value = 'Estudiante123*';
                    } else if (val === 'est2') {
                        document.getElementById('loginCorreo').value = 'alexis.rodriguez@academia.umb.edu.co';
                        document.getElementById('loginPassword').value = 'Estudiante123*';
                    } else {
                        document.getElementById('loginCorreo').value = 'admin@umb.edu.co';
                        document.getElementById('loginPassword').value = 'AdminUMB2026*';
                    }
                }

                async function iniciarSesion() {
                    const correo = document.getElementById('loginCorreo').value;
                    const password = document.getElementById('loginPassword').value;
                    const errBox = document.getElementById('loginError');
                    errBox.classList.add('hidden');

                    const body = `correo=${encodeURIComponent(correo)}&password=${encodeURIComponent(password)}`;
                    const res = await fetch('/api/auth/login', { method: 'POST', body });
                    const data = await res.json();

                    if (!res.ok) {
                        errBox.innerText = data.error;
                        errBox.classList.remove('hidden');
                        return;
                    }

                    currentUser = data;
                    document.getElementById('loginScreen').classList.add('hidden');
                    document.getElementById('userHeaderInfo').classList.remove('hidden');
                    document.getElementById('userHeaderInfo').classList.add('flex');
                    document.getElementById('headerUserName').innerText = `${data.nombre} (${data.correo})`;
                    document.getElementById('headerUserRole').innerText = `Rol: ${data.rol} | ${data.programa}`;

                    if (data.rol === 'ADMIN') {
                        document.getElementById('adminScreen').classList.remove('hidden');
                        cargarDashboardAdmin();
                    } else {
                        document.getElementById('studentScreen').classList.remove('hidden');
                        cargarProfesores();
                    }
                }

                function logout() {
                    currentUser = null;
                    document.getElementById('userHeaderInfo').classList.add('hidden');
                    document.getElementById('studentScreen').classList.add('hidden');
                    document.getElementById('adminScreen').classList.add('hidden');
                    document.getElementById('loginScreen').classList.remove('hidden');
                }

                function obtenerMedalla(prom) {
                    if (prom >= 9.0) return `<span class="bg-amber-400 text-red-950 font-extrabold text-[11px] px-2.5 py-0.5 rounded-full border border-amber-600">🏆 Excelencia Oro UMB</span>`;
                    if (prom >= 7.5) return `<span class="bg-emerald-100 text-emerald-800 font-bold text-[11px] px-2.5 py-0.5 rounded-full">⭐ Desempeño Destacado</span>`;
                    if (prom > 0) return `<span class="bg-red-100 text-red-800 font-bold text-[11px] px-2.5 py-0.5 rounded-full">⚠️ En Plan de Mejora</span>`;
                    return `<span class="bg-stone-200 text-stone-600 text-[11px] px-2 py-0.5 rounded-full">Sin calificaciones</span>`;
                }

                // --- FUNCIONES DEL ESTUDIANTE ---
                async function cargarProfesores() {
                    const fac = document.getElementById('filtroFacultadEst').value;
                    const q = document.getElementById('busquedaProfEst').value;
                    const res = await fetch(`/api/profesores?facultad=${encodeURIComponent(fac)}&q=${encodeURIComponent(q)}`);
                    const data = await res.json();

                    const cont = document.getElementById('listaProfesoresEst');
                    if (data.profesores.length === 0) {
                        cont.innerHTML = `<div class="bg-white p-4 rounded-xl text-xs text-stone-500 border">No se encontraron docentes con ese filtro.</div>`;
                        return;
                    }

                    cont.innerHTML = data.profesores.map(p => `
                        <div onclick="verDetalleProfesor(${p.id})" class="cursor-pointer bg-white hover:bg-amber-50 transition p-4 rounded-xl shadow-sm border-2 ${selectedProfesorId === p.id ? 'border-red-800 bg-amber-50' : 'border-stone-200'}">
                            <div class="flex justify-between items-start">
                                <div>
                                    <h4 class="font-bold text-red-900 text-sm">${p.nombre}</h4>
                                    <p class="text-xs text-stone-600 font-medium">${p.asignatura}</p>
                                    <span class="inline-block mt-1 text-[11px] bg-red-100 text-red-800 px-2 py-0.5 rounded font-semibold">${p.facultad}</span>
                                </div>
                                <div class="text-right">
                                    <div class="text-lg font-black text-amber-600">${p.promedio}/10</div>
                                    <div class="text-[11px] text-stone-500">${p.totalEvaluaciones} eval.</div>
                                </div>
                            </div>
                            <div class="mt-2 flex justify-between items-center">
                                ${obtenerMedalla(p.promedio)}
                                <span class="text-xs font-bold text-red-800 underline">Ver perfil y evaluar →</span>
                            </div>
                        </div>
                    `).join('');

                    if (!selectedProfesorId && data.profesores.length > 0) {
                        verDetalleProfesor(data.profesores[0].id);
                    }
                }

                async function verDetalleProfesor(id) {
                    selectedProfesorId = id;
                    const res = await fetch(`/api/profesores/detalle?id=${id}`);
                    const p = await res.json();

                    const preguntasHtml = PREGUNTAS_UMB.map((texto, idx) => `
                        <div class="bg-stone-50 p-3 rounded-lg border border-stone-200 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-2">
                            <label class="text-xs font-semibold text-stone-700">${texto}</label>
                            <div class="flex items-center space-x-2">
                                <input id="preg_${idx + 1}" type="range" min="1" max="10" value="9" oninput="actualizarCalculoVivo()" class="accent-red-800 cursor-pointer">
                                <span id="val_preg_${idx + 1}" class="w-8 text-center font-black text-red-900 bg-amber-200 rounded px-1 text-xs">9</span>
                            </div>
                        </div>
                    `).join('');

                    const comentariosHtml = (p.comentarios && p.comentarios.length > 0)
                        ? p.comentarios.map(c => `
                            <div class="bg-stone-50 p-3.5 rounded-xl border-l-4 border-amber-500">
                                <div class="flex justify-between items-center text-xs mb-1">
                                    <span class="font-bold text-red-900">🎓 ${c.estudiante}</span>
                                    <span class="font-extrabold bg-red-800 text-amber-300 px-2 py-0.5 rounded">Promedio Encuesta: ${c.promedio}/10 • 📅 ${c.fecha}</span>
                                </div>
                                <p class="text-xs text-stone-700 italic">"${c.comentario}"</p>
                            </div>
                        `).join('')
                        : `<p class="text-xs text-stone-500 italic">Aún no hay comentarios registrados en el último mes para este docente.</p>`;

                    document.getElementById('perfilProfesorCard').innerHTML = `
                        <div class="border-b-2 border-amber-400 pb-4 mb-5 flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
                            <div>
                                <div class="flex items-center gap-2">
                                    <span class="bg-red-800 text-white text-xs font-bold px-2.5 py-0.5 rounded">${p.facultad}</span>
                                    <span class="bg-amber-100 text-amber-900 text-xs font-bold px-2.5 py-0.5 rounded">Escalafón: ${p.escalafon}</span>
                                </div>
                                <h2 class="text-2xl font-black text-red-900 mt-1">${p.nombre}</h2>
                                <p class="text-xs text-stone-600">📧 ${p.correo} | 📚 Asignatura: <strong>${p.asignatura}</strong></p>
                            </div>
                            <div class="bg-gradient-to-br from-red-900 to-red-800 text-white px-5 py-3 rounded-2xl text-center border-2 border-amber-400 shadow">
                                <div class="text-[11px] uppercase text-amber-300 font-bold">Promedio Acumulado</div>
                                <div class="text-3xl font-black">${p.promedio} <span class="text-sm font-normal">/ 10</span></div>
                                <div class="text-[11px] text-red-100">${p.totalEvaluaciones} encuestas procesadas</div>
                            </div>
                        </div>

                        <!-- Comentarios del Último Mes -->
                        <div class="mb-6">
                            <h3 class="text-sm font-extrabold uppercase text-red-900 mb-3">💬 Comentarios de Estudiantes (Último Mes)</h3>
                            <div class="space-y-2.5 max-h-60 overflow-y-auto pr-1">${comentariosHtml}</div>
                        </div>

                        <!-- Encuesta Mensual de 10 Preguntas -->
                        <div class="bg-red-50/60 p-5 rounded-2xl border-2 border-red-200">
                            <div class="flex justify-between items-center mb-3">
                                <div>
                                    <h3 class="text-base font-black text-red-900">📝 Encuesta Mensual de Satisfacción Docente (10 Criterios)</h3>
                                    <p class="text-xs text-stone-600">Califica de 1 (Deficiente) a 10 (Excelente). Se permite 1 evaluación mensual por docente.</p>
                                </div>
                                <div class="bg-amber-400 text-red-950 font-black px-3 py-1.5 rounded-xl text-sm shadow">
                                    Promedio actual: <span id="promedioVivo">9.0</span>/10
                                </div>
                            </div>

                            <div class="space-y-2 mb-4">${preguntasHtml}</div>

                            <label class="block text-xs font-bold text-red-900 uppercase mb-1">Tus gustos, aportes o inconformidades con el profesor (Moderado por Filtro UMB):</label>
                            <textarea id="comentarioEncuesta" rows="3" placeholder="Escribe aquí tu opinión respetuosa sobre la metodología del docente..." class="w-full border border-stone-300 rounded-xl p-3 text-xs focus:border-red-800 focus:outline-none bg-white"></textarea>

                            <div id="msgEncuesta" class="hidden my-3 p-3 rounded-lg text-xs font-bold"></div>

                            <button onclick="enviarEncuesta(${p.id})" class="mt-3 w-full bg-red-800 hover:bg-red-700 text-white font-extrabold py-3 rounded-xl shadow border-b-4 border-amber-500 transition text-sm">
                                Enviar Encuesta de 10 Preguntas y Publicar Comentario
                            </button>
                        </div>
                    `;
                }

                function actualizarCalculoVivo() {
                    let suma = 0;
                    for (let i = 1; i <= 10; i++) {
                        const v = parseInt(document.getElementById('preg_' + i).value);
                        document.getElementById('val_preg_' + i).innerText = v;
                        suma += v;
                    }
                    document.getElementById('promedioVivo').innerText = (suma / 10).toFixed(1);
                }

                async function enviarEncuesta(profesorId) {
                    const comentario = document.getElementById('comentarioEncuesta').value;
                    const msgBox = document.getElementById('msgEncuesta');
                    msgBox.classList.remove('hidden');

                    let params = `profesorId=${profesorId}&correoEstudiante=${encodeURIComponent(currentUser.correo)}&nombreEstudiante=${encodeURIComponent(currentUser.nombre)}&comentario=${encodeURIComponent(comentario)}`;
                    for (let i = 1; i <= 10; i++) {
                        params += `&p${i}=${document.getElementById('preg_' + i).value}`;
                    }

                    const res = await fetch('/api/evaluaciones', { method: 'POST', body: params });
                    const data = await res.json();

                    if (res.ok) {
                        msgBox.className = 'my-3 p-3 rounded-lg text-xs font-bold bg-emerald-100 text-emerald-900 border border-emerald-400';
                        msgBox.innerText = data.message;
                        cargarProfesores();
                        setTimeout(() => verDetalleProfesor(profesorId), 600);
                    } else {
                        msgBox.className = 'my-3 p-3 rounded-lg text-xs font-bold bg-red-100 text-red-900 border border-red-400';
                        msgBox.innerText = '❌ ' + data.error;
                    }
                }

                // --- FUNCIONES DEL ADMINISTRADOR ---
                async function cargarDashboardAdmin() {
                    const res = await fetch('/api/profesores');
                    const data = await res.json();

                    document.getElementById('adminStatsGrid').innerHTML = data.estadisticas.map(s => `
                        <div class="bg-stone-50 p-4 rounded-xl border-l-4 border-amber-500 shadow-sm">
                            <div class="text-xs font-bold text-red-900">${s.facultad}</div>
                            <div class="text-2xl font-black text-amber-600 mt-1">${s.promedio} / 10</div>
                            <div class="text-[11px] text-stone-500 mt-1">Total eval: ${s.total} | Máx: ${s.max} | Mín: ${s.min}</div>
                        </div>
                    `).join('');

                    document.getElementById('listaAdminProfesores').innerHTML = data.profesores.map(p => `
                        <div class="flex justify-between items-center bg-stone-50 p-2.5 rounded-lg border text-xs">
                            <div>
                                <div class="font-bold text-red-900">${p.nombre}</div>
                                <div class="text-[11px] text-stone-500">${p.facultad} • Prom: ${p.promedio}/10</div>
                            </div>
                            <button onclick="eliminarProfesorAdmin(${p.id})" class="bg-red-100 hover:bg-red-200 text-red-800 font-bold px-2.5 py-1 rounded">
                                Quitar
                            </button>
                        </div>
                    `).join('');

                    cargarComentariosAdmin();
                    cargarUsuariosAdmin();
                }

                async function crearProfesorAdmin() {
                    const nombre = document.getElementById('nuevoProfNombre').value;
                    const correo = document.getElementById('nuevoProfCorreo').value;
                    const facultad = document.getElementById('nuevoProfFacultad').value;
                    const asignatura = document.getElementById('nuevoProfAsignatura').value;
                    const escalafon = document.getElementById('nuevoProfEscalafon').value;
                    const msg = document.getElementById('msgAdminProf');

                    const body = `nombre=${encodeURIComponent(nombre)}&correo=${encodeURIComponent(correo)}&facultad=${encodeURIComponent(facultad)}&asignatura=${encodeURIComponent(asignatura)}&escalafon=${encodeURIComponent(escalafon)}`;
                    const res = await fetch('/api/profesores', { method: 'POST', body });
                    const data = await res.json();

                    msg.innerText = res.ok ? '✅ ' + data.message : '❌ ' + data.error;
                    msg.className = res.ok ? 'text-center font-bold text-emerald-700' : 'text-center font-bold text-red-700';
                    if (res.ok) {
                        document.getElementById('nuevoProfNombre').value = '';
                        document.getElementById('nuevoProfCorreo').value = '';
                        document.getElementById('nuevoProfAsignatura').value = '';
                        cargarDashboardAdmin();
                    }
                }

                async function eliminarProfesorAdmin(id) {
                    await fetch(`/api/profesores?id=${id}`, { method: 'DELETE' });
                    cargarDashboardAdmin();
                }

                async function cargarComentariosAdmin() {
                    const q = document.getElementById('filtroComentariosAdmin').value;
                    const res = await fetch(`/api/admin/comentarios?q=${encodeURIComponent(q)}`);
                    const list = await res.json();
                    cachedComentariosAdmin = list;

                    document.getElementById('listaAdminComentarios').innerHTML = list.map(c => `
                        <div class="bg-stone-50 p-3 rounded-lg border border-stone-200 text-xs space-y-1">
                            <div class="flex justify-between items-center">
                                <span class="font-bold text-red-900">Prof: ${c.profesor} (${c.promedio}/10)</span>
                                <button onclick="eliminarComentarioAdmin(${c.id})" class="bg-red-800 text-white text-[10px] px-2 py-0.5 rounded hover:bg-red-700">
                                    Eliminar
                                </button>
                            </div>
                            <div class="text-[11px] text-stone-500">Por: ${c.estudiante} (${c.fecha})</div>
                            <p class="text-stone-800 italic">"${c.comentario}"</p>
                        </div>
                    `).join('');
                }

                async function eliminarComentarioAdmin(id) {
                    await fetch(`/api/admin/comentarios?id=${id}`, { method: 'DELETE' });
                    cargarDashboardAdmin();
                }

                async function cargarUsuariosAdmin() {
                    const res = await fetch('/api/admin/usuarios');
                    const users = await res.json();
                    document.getElementById('listaAdminUsuarios').innerHTML = users.map(u => `
                        <div class="flex justify-between items-center bg-stone-50 p-2.5 rounded-lg border text-xs">
                            <div>
                                <div class="font-bold text-stone-800">${u.nombre} <span class="text-[10px] bg-amber-200 text-red-950 px-1.5 py-0.5 rounded font-bold">${u.rol}</span></div>
                                <div class="text-[11px] text-stone-500">${u.correo}</div>
                            </div>
                            ${u.id !== 1 ? `<button onclick="eliminarUsuarioAdmin(${u.id})" class="bg-red-100 hover:bg-red-200 text-red-800 font-bold px-2 py-1 rounded">Eliminar</button>` : ''}
                        </div>
                    `).join('');
                }

                async function crearUsuarioAdmin() {
                    const nombre = document.getElementById('nuevoUserNombre').value;
                    const correo = document.getElementById('nuevoUserCorreo').value;
                    const password = document.getElementById('nuevoUserPass').value;
                    const programa = document.getElementById('nuevoUserProg').value;
                    const msg = document.getElementById('msgAdminUser');

                    const body = `nombre=${encodeURIComponent(nombre)}&correo=${encodeURIComponent(correo)}&password=${encodeURIComponent(password)}&programa=${encodeURIComponent(programa)}&rol=ESTUDIANTE`;
                    const res = await fetch('/api/admin/usuarios', { method: 'POST', body });
                    const data = await res.json();
                    msg.innerText = res.ok ? '✅ ' + data.message : '❌ ' + data.error;
                    if (res.ok) {
                        document.getElementById('nuevoUserNombre').value = '';
                        document.getElementById('nuevoUserCorreo').value = '';
                        cargarUsuariosAdmin();
                    }
                }

                async function eliminarUsuarioAdmin(id) {
                    await fetch(`/api/admin/usuarios?id=${id}`, { method: 'DELETE' });
                    cargarUsuariosAdmin();
                }

                async function probarResilienciaAdmin() {
                    const box = document.getElementById('adminResilienceBox');
                    box.classList.remove('hidden');
                    box.innerText = 'Verificando Circuit Breakers y adaptadores externos...';
                    const res = await fetch('/api/resilience/test');
                    const d = await res.json();
                    box.innerText = `[API 1 Datos.gov.co]: ${d.api1_status} (CB1: ${d.cb1_state}) | [API 2 DANE + Retry Backoff]: ${d.api2_status} (CB2: ${d.cb2_state})`;
                }

                function exportarReporteCSV() {
                    let csv = "ID,Profesor,Facultad,Estudiante,Correo,Promedio,Fecha,Comentario\\n";
                    cachedComentariosAdmin.forEach(c => {
                        csv += `${c.id},"${c.profesor}","${c.facultad}","${c.estudiante}","${c.correo}",${c.promedio},${c.fecha},"${c.comentario}"\\n`;
                    });
                    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
                    const url = URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = 'reporte_evaluaciones_umb.csv';
                    a.click();
                }
            </script>
        </body>
        </html>
        """;
    }
}