package usach.cl.laboratorio1.controller;

import usach.cl.laboratorio1.util.ErrorUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import usach.cl.laboratorio1.dto.AuthResponse;
import usach.cl.laboratorio1.dto.LoginRequest;
import usach.cl.laboratorio1.repository.UsuarioRepository;
import usach.cl.laboratorio1.service.JwtService;
import usach.cl.laboratorio1.tablas.Usuario;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin("*")
public class AuthController {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest request) {
        if (req == null || req.getUsername() == null) {
            return ResponseEntity.badRequest().body("Error: No enviaste el JSON en el Body");
        }

        Usuario user = usuarioRepository.findByUsername(req.getUsername());
        if (user != null && passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            String token = jwtService.createToken(user.getNombreUsuario(), user.getRol());
            return ResponseEntity.ok(new AuthResponse(token));
        }

        return ResponseEntity.status(401).body("Credenciales incorrectas");
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Usuario usuario) {
        try {
            if (usuario.getRol() == null) usuario.setRol("USER");
            usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
            usuarioRepository.save(usuario);
            return ResponseEntity.ok("Usuario registrado exitosamente");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al registrar: " + ErrorUtil.msg(e));
        }
    }
}