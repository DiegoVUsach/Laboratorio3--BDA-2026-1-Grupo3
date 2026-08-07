package usach.cl.laboratorio1.controller;

import usach.cl.laboratorio1.util.ErrorUtil;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import usach.cl.laboratorio1.dto.InventarioDTO;
import usach.cl.laboratorio1.dto.InventarioItemDTO;
import usach.cl.laboratorio1.repository.InventarioItemRepository;
import usach.cl.laboratorio1.repository.InventarioRepository;
import usach.cl.laboratorio1.service.InventarioItemService;
import usach.cl.laboratorio1.service.InventarioService;
import usach.cl.laboratorio1.tablas.Inventario;


@RestController
@RequestMapping("/api/inventarios")
@CrossOrigin("*")
public class InventarioController {

    @Autowired
    private InventarioRepository inventarioRepository;

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private InventarioItemRepository inventarioItemRepository;

    @Autowired
    private InventarioItemService inventarioItemService;

    // Obtener todos (Paginado)
    @GetMapping
    public List<InventarioDTO> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return inventarioRepository.findAllDetalle(page, size);
    }

    // --- RUTAS DE INVENTARIO ESPECÍFICO (Desambiguadas) ---

    // Cambiado de /{id} a /id/{id} para evitar el 403 por ambigüedad
    @GetMapping("/id/{id}")
    public ResponseEntity<InventarioDTO> findById(@PathVariable Integer id) {
        InventarioDTO e = inventarioRepository.findDetalleById(id);
        return e != null ? ResponseEntity.ok(e) : ResponseEntity.notFound().build();
    }

    @GetMapping("/por-personaje/{idPersonaje}")
    public ResponseEntity<InventarioDTO> findByPersonaje(@PathVariable Integer idPersonaje) {
        InventarioDTO e = inventarioRepository.findDetalleByPersonaje(idPersonaje);
        return e != null ? ResponseEntity.ok(e) : ResponseEntity.notFound().build();
    }

    // --- RUTAS DE ITEMS EN INVENTARIO ---

    @GetMapping("/id/{id}/items")
    public List<InventarioItemDTO> itemsByInventario(@PathVariable Integer id) {
        return inventarioItemRepository.findDetalleByInventario(id);
    }

    @GetMapping("/por-personaje/{idPersonaje}/items")
    public List<InventarioItemDTO> itemsByPersonaje(@PathVariable Integer idPersonaje) {
        return inventarioItemRepository.findDetalleByPersonaje(idPersonaje);
    }

    // --- OPERACIONES DE ESCRITURA ---

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Inventario inventario) {
        try {
            inventarioService.prepararInventario(inventario);
            inventarioRepository.save(inventario);
            return ResponseEntity.ok("Inventario creado exitosamente");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + ErrorUtil.msg(e));
        }
    }

    @PutMapping("/id/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody Inventario inventario) {
        inventario.setIdInventario(id);
        inventarioService.prepararInventario(inventario);
        inventarioRepository.update(inventario);
        return ResponseEntity.ok("Inventario actualizado");
    }

    @DeleteMapping("/id/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        inventarioRepository.deleteById(id);
        return ResponseEntity.ok("Inventario eliminado");
    }

    // --- GESTIÓN DE ITEMS INDIVIDUALES ---

    @PostMapping("/id/{id}/items")
    public ResponseEntity<?> addItem(@PathVariable Integer id, @RequestBody InventarioItemRequest request) {
        try {
            inventarioItemService.agregarItem(id, request.idItem);
            return ResponseEntity.ok("Item agregado al inventario");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + ErrorUtil.msg(e));
        }
    }

    @DeleteMapping("/id/{id}/items/{idItem}")
    public ResponseEntity<?> removeItem(@PathVariable Integer id, @PathVariable Integer idItem) {
        inventarioItemService.eliminarItem(id, idItem);
        return ResponseEntity.ok("Item eliminado del inventario");
    }

    static class InventarioItemRequest {
        public Integer idItem;
    }
}