package usach.cl.laboratorio1.controller;

import usach.cl.laboratorio1.util.ErrorUtil;
import usach.cl.laboratorio1.repository.PersonajeRepository;
import usach.cl.laboratorio1.repository.UsuarioRepository;
import usach.cl.laboratorio1.service.PersonajeService;
import usach.cl.laboratorio1.tablas.Personaje;
import usach.cl.laboratorio1.tablas.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// CRUD completo de Personajes (Requerimiento 1)
// + Asignacion de roles por el Guild Master (Requerimiento 2)
@RestController
@RequestMapping("/api/personajes")
@CrossOrigin("*")
public class PersonajeController {

    @Autowired
    private PersonajeRepository personajeRepository;

    @Autowired
    private PersonajeService personajeService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    // GET /api/personajes?page=0&size=10
    // FIX BUG 7: paginacion agregada
    // RBAC: el listado global de personajes es una vista de administracion.
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<Personaje> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return personajeRepository.findAll(page, size);
    }

    // GET /api/personajes/3 - Obtener un personaje por su ID


    // GET /api/personajes/mis-personajes
    // Devuelve solo los personajes del usuario autenticado (Req 2).
    // auth.getName() extrae el username del token JWT.
    // Personajes del usuario autenticado
    @GetMapping("/mis-personajes")
    public List<Personaje> misPersonajes(Authentication auth) {
        return personajeRepository.findByUsuario(auth.getName());
    }

    // FIX BUG 1: el idUsuario se obtiene del token JWT, no del body.
    // Un jugador solo puede crear personajes para su propia cuenta.
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Personaje personaje, Authentication auth) {
        try {
            Usuario owner = usuarioRepository.findByUsername(auth.getName());
            if (owner == null) {
                return ResponseEntity.status(401).body("Usuario no encontrado.");
            }
            if (personaje.getFaccion() == null) {
                return ResponseEntity.badRequest().body("Faccion invalida.");
            }
            // Forzar que el personaje pertenezca al usuario del token
            personaje.setIdUsuario(owner.getIdUsuario());
            personajeRepository.save(personaje);
            return ResponseEntity.ok("Personaje creado exitosamente");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + ErrorUtil.msg(e));
        }
    }

    // PUT /api/personajes/3 - Actualizar un personaje
    // Solo el dueno del personaje puede modificarlo (validacion JWT).
    // Solo el dueño del personaje puede modificarlo
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id,
                                    @RequestBody Personaje personaje,
                                    Authentication auth) {
        try {
            if (!personajeRepository.perteneceAUsuario(id, auth.getName())) {
                return ResponseEntity.status(403).body("No tienes permiso sobre este personaje.");
            }
            if (personaje.getFaccion() == null) {
                return ResponseEntity.badRequest().body("Faccion invalida.");
            }
            personaje.setIdPersonaje(id);
            personajeRepository.update(personaje);
            return ResponseEntity.ok("Personaje actualizado");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + ErrorUtil.msg(e));
        }
    }

    // DELETE /api/personajes/3 - Eliminar un personaje
    // Solo el dueno puede eliminarlo.
    // Solo el dueño puede eliminarlo
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id, Authentication auth) {
        if (!personajeRepository.perteneceAUsuario(id, auth.getName())) {
            return ResponseEntity.status(403).body("No tienes permiso sobre este personaje.");
        }
        personajeRepository.deleteById(id);
        return ResponseEntity.ok("Personaje eliminado");
    }

    // PUT /api/personajes/asignar-rol
    // Solo el Guild Master puede cambiar roles (Req 2).
    // Recibe: idEjecutor (quien lo hace), idObjetivo (a quien),
    // nuevoRol ("Raider", "Member", etc.)
    // Asignar rol (solo Guild Master)
    @PutMapping("/asignar-rol")
    public ResponseEntity<?> asignarRol(@RequestBody RolRequest request,
                                        Authentication auth) {
        try {
            personajeService.asignarNuevoRol(auth.getName(),
                    request.idEjecutor, request.idObjetivo, request.nuevoRol);
            return ResponseEntity.ok("Rol actualizado a " + request.nuevoRol);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorUtil.msg(e));
        }
    }

    // Clase interna para recibir los datos de asignacion de rol.
    // Es como un mini-DTO pero solo se usa en este controller.
    static class RolRequest {
        public Integer idEjecutor;
        public Integer idObjetivo;
        public String nuevoRol;
    }


    @GetMapping("/{id}")
    public ResponseEntity<Personaje> findById(@PathVariable Integer id) {
        Personaje p = personajeRepository.findById(id);
        return p != null ? ResponseEntity.ok(p) : ResponseEntity.notFound().build();
    }
}
