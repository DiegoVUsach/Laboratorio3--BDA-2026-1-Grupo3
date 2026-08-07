package usach.cl.laboratorio1.dto;

import lombok.Data;

// DTO para recibir las credenciales de login.
// Solo necesitamos username y password, nada mas.
// Asi evitamos recibir un objeto Usuario completo
// que tiene campos como id y rol que el usuario no deberia enviar.
@Data
public class LoginRequest {
    private String username;
    private String password;
}