package com.umb.taller.infrastructure.security;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class SecurityService {

    private static final Map<String, UserCredentials> usersDb = new HashMap<>();

    static {
        // Usuarios originales para compatibilidad con Main.java y pruebas
        usersDb.put("admin_evaldoc", new UserCredentials("admin_evaldoc", "Admin123*", "ADMIN"));
        usersDb.put("user_docente", new UserCredentials("user_docente", "Docente2026*", "USER"));
        // Cuentas institucionales UMB
        usersDb.put("admin@umb.edu.co", new UserCredentials("admin@umb.edu.co", "AdminUMB2026*", "ADMIN"));
        usersDb.put("nicolas.sanchez@academia.umb.edu.co", new UserCredentials("nicolas.sanchez@academia.umb.edu.co", "Estudiante123*", "ESTUDIANTE"));
        usersDb.put("maria.gomez@academia.umb.edu.co", new UserCredentials("maria.gomez@academia.umb.edu.co", "Estudiante123*", "ESTUDIANTE"));
    }

    public static class UserCredentials {
        String username;
        String password;
        String role;

        public UserCredentials(String username, String password, String role) {
            this.username = username;
            this.password = password;
            this.role = role;
        }
    }

    public String authenticate(String username, String password) {
        UserCredentials user = usersDb.get(username);
        if (user == null || !user.password.equals(password)) {
            throw new SecurityException("[SECURITY] Autenticación fallida: Credenciales inválidas.");
        }
        return generateToken(username, user.role);
    }

    public String generateToken(String username, String role) {
        String header = Base64.getEncoder().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = Base64.getEncoder().encodeToString(("{\"sub\":\"" + username + "\",\"role\":\"" + role + "\",\"iat\":" + System.currentTimeMillis() + "}").getBytes());
        String signature = Base64.getEncoder().encodeToString((header + "." + payload + ".UMB_SECRET_KEY_2026").getBytes());
        return header + "." + payload + "." + signature;
    }

    public boolean authorize(String token, String requiredRole) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return false;

            String payloadJson = new String(Base64.getDecoder().decode(parts[1]));
            return payloadJson.contains("\"role\":\"" + requiredRole + "\"") || payloadJson.contains("\"role\":\"ADMIN\"");
        } catch (Exception e) {
            return false;
        }
    }
}