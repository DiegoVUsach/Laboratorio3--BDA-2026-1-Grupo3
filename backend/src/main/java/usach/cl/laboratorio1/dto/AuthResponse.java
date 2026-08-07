package usach.cl.laboratorio1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

// DTO para la respuesta del login.
// Cuando el login es exitoso, le devolvemos al usuario
// unicamente el token JWT. Con ese token puede hacer
// todas las peticiones posteriores.
// @AllArgsConstructor genera el constructor AuthResponse(String token)
@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
}