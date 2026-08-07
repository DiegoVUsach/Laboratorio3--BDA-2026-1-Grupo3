package usach.cl.laboratorio1.controller;

import usach.cl.laboratorio1.dto.UsuarioDTO;
import usach.cl.laboratorio1.repository.UsuarioRepository;
import usach.cl.laboratorio1.tablas.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// Controller para operaciones del usuario autenticado.
@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin("*")
public class UsuarioController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    // GET /api/usuarios/me
    // Ruta PROTEGIDA. Devuelve los datos del usuario autenticado.
    // "Authentication auth" es inyectado automaticamente por Spring Security
    // a partir del token JWT. auth.getName() retorna el username del token.
    @GetMapping("/me")
    public UsuarioDTO miPerfil(Authentication auth) {
        Usuario u = usuarioRepository.findByUsername(auth.getName());
        if (u == null) return null;
        return new UsuarioDTO(u.getIdUsuario(), u.getNombreUsuario(), u.getRol());
    }
}