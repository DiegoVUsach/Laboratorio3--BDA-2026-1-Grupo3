package usach.cl.laboratorio1.controller;

import usach.cl.laboratorio1.util.ErrorUtil;
import usach.cl.laboratorio1.service.ClanesService;
import usach.cl.laboratorio1.repository.PersonajeRepository;
import usach.cl.laboratorio1.tablas.Clanes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// FIX BUG 9: CRUD completo de clanes
@RestController
@RequestMapping("/api/clanes")
@CrossOrigin("*")
public class ClanesController {

    @Autowired
    private ClanesService clanesService;

    @Autowired
    private PersonajeRepository personajeRepository;

    @GetMapping
    public List<Clanes> findAll() {
        return clanesService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Clanes> findById(@PathVariable Integer id) {
        Clanes clan = clanesService.findById(id);
        return clan != null ? ResponseEntity.ok(clan) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Clanes clan) {
        clanesService.save(clan);
        return ResponseEntity.ok("Clan creado exitosamente");
    }

    // FIX BUG 9: Update de clan (solo datos basicos, no liderazgo)
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id,
                                    @RequestBody Clanes clan) {
        Clanes existing = clanesService.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        existing.setNombreClan(clan.getNombreClan());
        clanesService.update(existing);
        return ResponseEntity.ok("Clan actualizado");
    }

    // Transferir liderazgo (dispara Trigger 2)
    @PutMapping("/{id}/transfer-leadership")
    public ResponseEntity<?> transferLeadership(
            @PathVariable Integer id,
            @RequestBody TransferRequest request,
            Authentication auth) {
        try {
            clanesService.transferLeadership(auth.getName(), id,
                    request.idCurrentLeader, request.idNewLeader);
            return ResponseEntity.ok("Liderazgo transferido exitosamente.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(ErrorUtil.msg(e));
        }
    }

    // DELETE /api/clanes/{id} - Eliminar un clan
    // Solo el lider del clan puede eliminarlo (validacion JWT).
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id, Authentication auth) {
        try {
            clanesService.delete(auth.getName(), id);
            return ResponseEntity.ok("Clan eliminado");
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(ErrorUtil.msg(e));
        }
    }

    // Cualquier jugador SIN clan puede fundar uno (queda Guild Master).
    @PostMapping("/fundar")
    public ResponseEntity<?> fundar(@RequestBody FundarRequest req, Authentication auth) {
        try {
            if (!personajeRepository.perteneceAUsuario(req.idPersonaje, auth.getName()))
                return ResponseEntity.status(403).body("Ese personaje no es tuyo.");
            clanesService.fundarClan(req.idPersonaje, req.nombreClan);
            return ResponseEntity.ok("Clan fundado. Ahora eres Guild Master.");
        } catch (Exception e) { return ResponseEntity.badRequest().body(ErrorUtil.msg(e)); }
    }

    @PostMapping("/unirse")
    public ResponseEntity<?> unirse(@RequestBody UnirseRequest req, Authentication auth) {
        try {
            if (!personajeRepository.perteneceAUsuario(req.idPersonaje, auth.getName()))
                return ResponseEntity.status(403).body("Ese personaje no es tuyo.");
            clanesService.unirseClan(req.idPersonaje, req.idClan);
            return ResponseEntity.ok("Te uniste al clan.");
        } catch (Exception e) { return ResponseEntity.badRequest().body(ErrorUtil.msg(e)); }
    }

    @PostMapping("/salir")
    public ResponseEntity<?> salir(@RequestBody SalirRequest req, Authentication auth) {
        try {
            if (!personajeRepository.perteneceAUsuario(req.idPersonaje, auth.getName()))
                return ResponseEntity.status(403).body("Ese personaje no es tuyo.");
            clanesService.salirClan(req.idPersonaje);
            return ResponseEntity.ok("Saliste del clan.");
        } catch (Exception e) { return ResponseEntity.badRequest().body(ErrorUtil.msg(e)); }
    }

    static class FundarRequest { public Integer idPersonaje; public String nombreClan; }
    static class UnirseRequest { public Integer idPersonaje; public Integer idClan; }
    static class SalirRequest  { public Integer idPersonaje; }

    static class TransferRequest {
        public Integer idCurrentLeader;
        public Integer idNewLeader;
    }

    // GET /api/clanes/{id}/miembros
    // Devuelve solo los personajes que pertenecen a ese clan.
    // Seguro: no expone personajes de otros clanes.
    @GetMapping("/{id}/miembros")
    public ResponseEntity<?> miembros(@PathVariable Integer id) {
        Clanes clan = clanesService.findById(id);
        if (clan == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(personajeRepository.findByClan(id));
    }
}