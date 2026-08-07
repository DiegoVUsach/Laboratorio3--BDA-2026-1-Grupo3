package usach.cl.laboratorio1.service;

import usach.cl.laboratorio1.repository.InventarioRepository;
import usach.cl.laboratorio1.repository.ItemRepository;
import usach.cl.laboratorio1.tablas.Inventario;
import usach.cl.laboratorio1.tablas.Item;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InventarioService {

    @Autowired
    private InventarioRepository inventarioRepository;

    @Autowired
    private ItemRepository itemRepository;

    public void prepararInventario(Inventario inventario) {
        validarSlotItem(inventario.getArmaduraEquipado(), Item.TipoItem.ARMADURA,
                "armadura");
        validarSlotItem(inventario.getArmaEquipado(), Item.TipoItem.ARMA,
                "arma");
        validarSlotItem(inventario.getAccesorioEquipado(), Item.TipoItem.ACCESORIO,
                "accesorio");
    }

    private void validarSlotItem(Integer idItem, Item.TipoItem tipoEsperado,
                                 String nombreSlot) {
        if (idItem == null) {
            return;
        }

        Item item = itemRepository.findById(idItem);
        if (item == null) {
            throw new RuntimeException("El item de " + nombreSlot + " no existe.");
        }
        if (item.getTipo() == null || item.getTipo() != tipoEsperado) {
            throw new RuntimeException("El item de " + nombreSlot + " debe ser de tipo " +
                    tipoEsperado + ".");
        }
    }

    public Inventario findByPersonaje(Integer idPersonaje) {
        return inventarioRepository.findByPersonaje(idPersonaje);
    }
}
