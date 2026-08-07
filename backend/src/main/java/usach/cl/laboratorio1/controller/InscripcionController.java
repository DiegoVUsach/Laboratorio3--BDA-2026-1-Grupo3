package usach.cl.laboratorio1.controller;

import usach.cl.laboratorio1.util.ErrorUtil;
import usach.cl.laboratorio1.repository.InscripcionRepository;
import usach.cl.laboratorio1.service.InscripcionService;
import usach.cl.laboratorio1.tablas.InscripcionRaid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inscripciones")
@CrossOrigin("*")
public class InscripcionController {

    @Autowired
    private InscripcionService inscripcionService;

    @Autowired
    private InscripcionRepository inscripcionRepository;

    // Listar inscritos de una raid con nombre y estado de confirmacion.
    // Se usa en el frontend para que el GM confirme asistencias (Req 2).
    @GetMapping("/por-raid/{idRaid}")
    public List<Map<String, Object>> inscritosPorRaid(@PathVariable Integer idRaid) {
        return inscripcionRepository.findByRaid(idRaid);
    }

    // FIX BUG 8: Ahora usa InscripcionService que valida pertenencia JWT
    // Inscripciones del personaje autenticado (para confirmar desde la lista de raids)
    @GetMapping("/mias")
    public List<Map<String, Object>> misInscripciones(@RequestParam Integer idPersonaje) {
        return inscripcionRepository.findByPersonaje(idPersonaje);
    }

    @PostMapping("/anotarse")
    public ResponseEntity<?> inscribir(@RequestBody InscripcionRaid inscripcion,
                                       Authentication auth) {
        try {
            inscripcionService.anotarPersonaje(inscripcion, auth.getName());
            return ResponseEntity.ok("Inscripcion realizada con exito.");
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Error al inscribirse: " + ErrorUtil.msg(e));
        }
    }

    // FIX BUG 6: Confirmar asistencia requiere ser Guild Master
    @PutMapping("/{id}/confirmar")
    public ResponseEntity<?> confirmarAsistencia(@PathVariable Integer id,
                                                 @RequestBody ConfirmarRequest request,
                                                 Authentication auth) {
        try {
            inscripcionService.confirmarAsistencia(id, request.idEjecutor, auth.getName());
            return ResponseEntity.ok("Asistencia confirmada.");
        } catch (Exception e) {
            return ResponseEntity.status(403).body(ErrorUtil.msg(e));
        }
    }

    static class ConfirmarRequest {
        public Integer idEjecutor;
    }
}