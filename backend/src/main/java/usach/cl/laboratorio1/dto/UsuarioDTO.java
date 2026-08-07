package usach.cl.laboratorio1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

// DTO para exponer datos del usuario en la API.
// Notar que NO incluye el campo password.
// Nunca se debe devolver la contraseña en una respuesta HTTP,
// por eso usamos este DTO en vez de la entidad Usuario directa.
@Data
@AllArgsConstructor
public class UsuarioDTO {
    private Integer idUsuario;
    private String nombreUsuario;
    private String rol;
}