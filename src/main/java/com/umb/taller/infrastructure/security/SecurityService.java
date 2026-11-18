package com.umb.taller.infrastructure.security;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
// import java.util.UUID;

public class SecurityService {

    // Simulación de base de datos de usuarios y sus roles (RBAC)
    private static final Map<String, UserCredentials> usersDb = new HashMap<>();

    static {
        usersDb.put("admin_evaldoc", new UserCredentials("admin_evaldoc", "Admin123*", "ADMIN"));
        usersDb.put("user_docente", new UserCredentials("user_docente", "Docente2026*", "USER"));
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

    // Método de Autenticación (Login) -> Retorna un token JWT simulado (Header.Payload.Signature)
    public String authenticate(String username, String password) {
        UserCredentials user = usersDb.get(username);
        if (user == null || !user.password.equals(password)) {
            throw new SecurityException("[SECURITY] Autenticación fallida: Credenciales inválidas.");
        }

        // Generación de un token JWT seguro (Criptografía Base64 simulando HMACSHA256)
        String header = Base64.getEncoder().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = Base64.getEncoder().encodeToString(("{\"sub\":\"" + username + "\",\"role\":\"" + user.role + "\",\"iat\":" + System.currentTimeMillis() + "}").getBytes());
        String signature = Base64.getEncoder().encodeToString((header + "." + payload + ".UMB_SECRET_KEY_2026").getBytes());

        return header + "." + payload + "." + signature;
    }

    // Método de Autorización RBAC (Verifica si el token pertenece a un rol permitido)
    public boolean authorize(String token, String requiredRole) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return false;

            String payloadJson = new String(Base64.getDecoder().decode(parts[1]));
            // Validación de control de roles (RBAC)
            return payloadJson.contains("\"role\":\"" + requiredRole + "\"") || payloadJson.contains("\"role\":\"ADMIN\"");
        } catch (Exception e) {
            return false;
        }
    }
}