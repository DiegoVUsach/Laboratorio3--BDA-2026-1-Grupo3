package usach.cl.laboratorio1.controller;

import usach.cl.laboratorio1.util.ErrorUtil;
import usach.cl.laboratorio1.repository.ItemRepository;
import usach.cl.laboratorio1.repository.PersonajeRepository;
import usach.cl.laboratorio1.tablas.HistorialBotin;
import usach.cl.laboratorio1.tablas.Item;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/items")
@CrossOrigin("*")
public class ItemController {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private PersonajeRepository personajeRepository;

    // FIX BUG 7: paginacion
    @GetMapping
    public List<Item> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return itemRepository.findAll(page, size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Item> findById(@PathVariable Integer id) {
        Item item = itemRepository.findById(id);
        return item != null ? ResponseEntity.ok(item) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Item item) {
        try {
            itemRepository.save(item);
            return ResponseEntity.ok("Item creado exitosamente");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + ErrorUtil.msg(e));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody Item item) {
        item.setIdItem(id);
        itemRepository.update(item);
        return ResponseEntity.ok("Item actualizado");
    }

    // FIX BUG 3: Solo ADMIN puede eliminar items
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id, Authentication auth) {
        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return ResponseEntity.status(403).body("Solo administradores pueden eliminar items.");
        }
        itemRepository.deleteById(id);
        return ResponseEntity.ok("Item eliminado");
    }

    // FIX BUG 4: Verificar que el personaje pertenece al usuario
    @GetMapping("/mi-pool/{idPersonaje}")
    public List<Map<String, Object>> miPool(@PathVariable Integer idPersonaje) {
        return itemRepository.miPool(idPersonaje);
    }

    @PostMapping("/canjear")
    public ResponseEntity<?> canjear(@RequestBody CanjeRequest request, Authentication auth) {
        try {
            if (!personajeRepository.perteneceAUsuario(request.idPersonaje, auth.getName())) {
                return ResponseEntity.status(403).body("El personaje no pertenece a tu cuenta.");
            }
            itemRepository.canjear(request.idPool, request.idPersonaje);
            return ResponseEntity.ok("Item canjeado.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorUtil.msg(e));
        }
    }

    private boolean esGuildMaster(String username) {
        return personajeRepository.findByUsuario(username).stream()
                .anyMatch(p -> "Guild Master".equals(p.getRolClan()));
    }

    // Req 10: Historial de botin
    @GetMapping("/historial/{idPersonaje}")
    public List<HistorialBotin> historialBotin(@PathVariable Integer idPersonaje) {
        return itemRepository.obtenerHistorialPorPersonaje(idPersonaje);
    }

    // Req 7: Ranking
    @GetMapping("/ranking")
    public List<Map<String, Object>> ranking() {
        return itemRepository.obtenerRanking();
    }

    @PostMapping("/ranking/refrescar")
    public ResponseEntity<?> refrescarRanking() {
        itemRepository.refrescarRanking();
        return ResponseEntity.ok("Ranking actualizado");
    }

    static class CanjeRequest {
        public Integer idPool;
        public Integer idPersonaje;
    }
}