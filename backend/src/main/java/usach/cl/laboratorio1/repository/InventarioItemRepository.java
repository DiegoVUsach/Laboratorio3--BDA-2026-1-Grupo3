package usach.cl.laboratorio1.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import usach.cl.laboratorio1.dto.InventarioItemDTO;
import usach.cl.laboratorio1.tablas.Item;
import usach.cl.laboratorio1.tablas.Personaje;

import java.util.ArrayList;
import java.util.List;

@Repository
public class InventarioItemRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    public List<InventarioItemDTO> findDetalleByInventario(Integer idInventario) {
        // En MongoDB el idInventario es igual al idPersonaje en nuestra relacion
        Query query = new Query(Criteria.where("inventario.idInventario").is(idInventario));
        Personaje p = mongoTemplate.findOne(query, Personaje.class);
        return mapToDTO(p);
    }

    public List<InventarioItemDTO> findDetalleByPersonaje(Integer idPersonaje) {
        Personaje p = mongoTemplate.findById(idPersonaje, Personaje.class);
        return mapToDTO(p);
    }

    public int save(Integer idInventario, Integer idItem) {
        Query query = new Query(Criteria.where("inventario.idInventario").is(idInventario));
        Personaje p = mongoTemplate.findOne(query, Personaje.class);
        if (p != null) {
            if (p.getInventario().getItems() == null) {
                p.getInventario().setItems(new ArrayList<>());
            }
            if (!p.getInventario().getItems().contains(idItem)) {
                p.getInventario().getItems().add(idItem);
                mongoTemplate.save(p);
            }
            return 1;
        }
        return 0;
    }

    public int delete(Integer idInventario, Integer idItem) {
        Query query = new Query(Criteria.where("inventario.idInventario").is(idInventario));
        Personaje p = mongoTemplate.findOne(query, Personaje.class);
        if (p != null) {
            if (p.getInventario().getItems() != null) {
                p.getInventario().getItems().remove(idItem);
                mongoTemplate.save(p);
            }
            return 1;
        }
        return 0;
    }

    private List<InventarioItemDTO> mapToDTO(Personaje p) {
        if (p == null || p.getInventario() == null || p.getInventario().getItems() == null) {
            return new ArrayList<>();
        }
        List<Integer> itemIds = p.getInventario().getItems();
        Query query = new Query(Criteria.where("idItem").in(itemIds));
        List<Item> items = mongoTemplate.find(query, Item.class);

        List<InventarioItemDTO> dtos = new ArrayList<>();
        for (Item item : items) {
            InventarioItemDTO dto = new InventarioItemDTO();
            dto.setIdInventario(p.getInventario().getIdInventario());
            dto.setIdItem(item.getIdItem());
            dto.setNombreItem(item.getNombreItem());
            dto.setRareza(item.getRareza());
            dto.setTipo(item.getTipo());
            dto.setNivel(item.getNivel());
            dto.setCostoDkp(item.getCostoDkp());
            dtos.add(dto);
        }
        return dtos;
    }
}
