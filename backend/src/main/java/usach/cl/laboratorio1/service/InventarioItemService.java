package usach.cl.laboratorio1.service;

import usach.cl.laboratorio1.repository.InventarioItemRepository;
import usach.cl.laboratorio1.repository.InventarioRepository;
import usach.cl.laboratorio1.repository.ItemRepository;
import usach.cl.laboratorio1.tablas.Inventario;
import usach.cl.laboratorio1.tablas.Item;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InventarioItemService {

    @Autowired
    private InventarioItemRepository inventarioItemRepository;

    @Autowired
    private InventarioRepository inventarioRepository;

    @Autowired
    private ItemRepository itemRepository;

    public void agregarItem(Integer idInventario, Integer idItem) {
        Inventario inventario = inventarioRepository.findById(idInventario);
        if (inventario == null) {
            throw new RuntimeException("El inventario no existe.");
        }

        Item item = itemRepository.findById(idItem);
        if (item == null) {
            throw new RuntimeException("El item no existe.");
        }

        inventarioItemRepository.save(idInventario, idItem);
    }

    public void eliminarItem(Integer idInventario, Integer idItem) {
        inventarioItemRepository.delete(idInventario, idItem);
    }
}
