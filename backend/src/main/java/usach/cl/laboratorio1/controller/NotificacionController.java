package usach.cl.laboratorio1.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import usach.cl.laboratorio1.repository.NotificacionRepository;

@RestController
@RequestMapping("/api/notificaciones")
@CrossOrigin("*")
public class NotificacionController {

    @Autowired
    private NotificacionRepository repo;

    @GetMapping("/mias")
    public List<Map<String, Object>> mias(@RequestParam Integer idPersonaje) {
        return repo.misNotificaciones(idPersonaje);
    }

    @GetMapping("/no-leidas")
    public Map<String, Integer> noLeidas(@RequestParam Integer idPersonaje) {
        return Map.of("count", repo.contarNoLeidas(idPersonaje));
    }

    @PutMapping("/marcar-todas")
    public ResponseEntity<?> marcarTodas(@RequestParam Integer idPersonaje) {
        repo.marcarTodasLeidas(idPersonaje);
        return ResponseEntity.ok("Notificaciones marcadas como leidas.");
    }

    @PutMapping("/{id}/leida")
    public ResponseEntity<?> marcar(@PathVariable Integer id) {
        repo.marcarLeida(id);
        return ResponseEntity.ok("Marcada como leida.");
    }
}
