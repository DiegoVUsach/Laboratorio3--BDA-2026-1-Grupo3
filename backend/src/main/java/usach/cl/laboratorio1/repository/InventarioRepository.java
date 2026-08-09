package usach.cl.laboratorio1.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import usach.cl.laboratorio1.dto.InventarioDTO;
import usach.cl.laboratorio1.dto.InventarioItemDTO;
import usach.cl.laboratorio1.tablas.Inventario;
import usach.cl.laboratorio1.tablas.Item;
import usach.cl.laboratorio1.tablas.Personaje;

@Repository
public class InventarioRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    public List<InventarioItemDTO> findItemsByPersonaje(Integer idPersonaje) {
        Personaje p = mongoTemplate.findById(idPersonaje, Personaje.class);
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

    public int addItemToInventario(Integer idInventario, Integer idItem) {
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

    public List<Inventario> findAll(int page, int size) {
        // En MongoDB el inventario está embebido en Personaje, pero podemos mapearlo
        Query query = new Query().skip((long) page * size).limit(size);
        List<Personaje> personajes = mongoTemplate.find(query, Personaje.class);
        List<Inventario> res = new ArrayList<>();
        for (Personaje p : personajes) {
            if (p.getInventario() != null) {
                res.add(mapToEntity(p));
            }
        }
        return res;
    }

    public List<InventarioDTO> findAllDetalle(int page, int size) {
        Query query = new Query().skip((long) page * size).limit(size);
        List<Personaje> personajes = mongoTemplate.find(query, Personaje.class);
        List<InventarioDTO> res = new ArrayList<>();
        for (Personaje p : personajes) {
            if (p.getInventario() != null) {
                res.add(mapToDTO(p));
            }
        }
        return res;
    }

    public Inventario findById(Integer id) {
        Query query = new Query(Criteria.where("inventario.idInventario").is(id));
        Personaje p = mongoTemplate.findOne(query, Personaje.class);
        return p != null ? mapToEntity(p) : null;
    }

    public InventarioDTO findDetalleById(Integer id) {
        Query query = new Query(Criteria.where("inventario.idInventario").is(id));
        Personaje p = mongoTemplate.findOne(query, Personaje.class);
        return p != null ? mapToDTO(p) : null;
    }

    public Inventario findByPersonaje(Integer idPersonaje) {
        Personaje p = mongoTemplate.findById(idPersonaje, Personaje.class);
        return p != null ? mapToEntity(p) : null;
    }

    public InventarioDTO findDetalleByPersonaje(Integer idPersonaje) {
        Personaje p = mongoTemplate.findById(idPersonaje, Personaje.class);
        return p != null ? mapToDTO(p) : null;
    }

    public int save(Inventario e) {
        Personaje p = mongoTemplate.findById(e.getIdPersonaje(), Personaje.class);
        if (p != null) {
            Personaje.Inventario anterior = p.getInventario();
            Personaje.Inventario inv = new Personaje.Inventario();
            inv.setIdInventario(e.getIdInventario() != null ? e.getIdInventario() : p.getIdPersonaje());
            inv.setArmaduraEquipado(e.getArmaduraEquipado());
            inv.setArmaEquipado(e.getArmaEquipado());
            inv.setAccesorioEquipado(e.getAccesorioEquipado());

            List<Integer> items = anterior != null && anterior.getItems() != null
                    ? new ArrayList<>(anterior.getItems())
                    : new ArrayList<>();
            agregarDesequipado(items, anterior != null ? anterior.getArmaduraEquipado() : null,
                    inv.getArmaduraEquipado());
            agregarDesequipado(items, anterior != null ? anterior.getArmaEquipado() : null,
                    inv.getArmaEquipado());
            agregarDesequipado(items, anterior != null ? anterior.getAccesorioEquipado() : null,
                    inv.getAccesorioEquipado());
            inv.setItems(items);

            p.setInventario(inv);
            mongoTemplate.save(p);
            return 1;
        }
        return 0;
    }

    private void agregarDesequipado(List<Integer> items, Integer anterior, Integer actual) {
        if (anterior != null && !anterior.equals(actual) && !items.contains(anterior)) {
            items.add(anterior);
        }
    }

    public int update(Inventario e) {
        return save(e);
    }

    public int deleteById(Integer id) {
        Query query = new Query(Criteria.where("inventario.idInventario").is(id));
        Personaje p = mongoTemplate.findOne(query, Personaje.class);
        if (p != null) {
            p.setInventario(new Personaje.Inventario());
            mongoTemplate.save(p);
            return 1;
        }
        return 0;
    }

    private Inventario mapToEntity(Personaje p) {
        Inventario ent = new Inventario();
        ent.setIdInventario(p.getInventario().getIdInventario());
        ent.setIdPersonaje(p.getIdPersonaje());
        ent.setArmaduraEquipado(p.getInventario().getArmaduraEquipado());
        ent.setArmaEquipado(p.getInventario().getArmaEquipado());
        ent.setAccesorioEquipado(p.getInventario().getAccesorioEquipado());
        return ent;
    }

    private InventarioDTO mapToDTO(Personaje p) {
        InventarioDTO dto = new InventarioDTO();
        Personaje.Inventario inv = p.getInventario();
        dto.setIdInventario(inv.getIdInventario().longValue());
        dto.setIdPersonaje(p.getIdPersonaje().longValue());

        if (inv.getArmaduraEquipado() != null) {
            dto.setArmaduraEquipado(inv.getArmaduraEquipado().longValue());
            Item item = mongoTemplate.findById(inv.getArmaduraEquipado(), Item.class);
            if (item != null) {
                dto.setNombreArmadura(item.getNombreItem());
                dto.setNivelArmadura(item.getNivel());
            }
        }
        if (inv.getArmaEquipado() != null) {
            dto.setArmaEquipado(inv.getArmaEquipado().longValue());
            Item item = mongoTemplate.findById(inv.getArmaEquipado(), Item.class);
            if (item != null) {
                dto.setNombreArma(item.getNombreItem());
                dto.setNivelArma(item.getNivel());
            }
        }
        if (inv.getAccesorioEquipado() != null) {
            dto.setAccesorioEquipado(inv.getAccesorioEquipado().longValue());
            Item item = mongoTemplate.findById(inv.getAccesorioEquipado(), Item.class);
            if (item != null) {
                dto.setNombreAccesorio(item.getNombreItem());
                dto.setNivelAccesorio(item.getNivel());
            }
        }
        return dto;
    }
}
