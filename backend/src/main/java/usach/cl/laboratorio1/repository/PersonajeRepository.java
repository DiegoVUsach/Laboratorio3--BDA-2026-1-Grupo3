package usach.cl.laboratorio1.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import usach.cl.laboratorio1.tablas.Personaje;
import usach.cl.laboratorio1.tablas.Usuario;
import usach.cl.laboratorio1.service.SequenceGeneratorService;

import java.util.ArrayList;
import java.util.List;

@Repository
public class PersonajeRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private SequenceGeneratorService sequenceGeneratorService;

    public List<Personaje> findAll(int page, int size) {
        Query query = new Query().with(PageRequest.of(page, size, Sort.by("idPersonaje").ascending()));
        return mongoTemplate.find(query, Personaje.class);
    }

    public List<Personaje> findAll() {
        return mongoTemplate.findAll(Personaje.class);
    }

    public Personaje findById(Integer id) {
        return mongoTemplate.findById(id, Personaje.class);
    }

    public List<Personaje> findByUsuario(String username) {
        Query uQuery = new Query(Criteria.where("nombreUsuario").is(username));
        Usuario u = mongoTemplate.findOne(uQuery, Usuario.class);
        if (u == null) {
            return new ArrayList<>();
        }
        Query pQuery = new Query(Criteria.where("idUsuario").is(u.getIdUsuario()));
        return mongoTemplate.find(pQuery, Personaje.class);
    }

    public int save(Personaje p) {
        if (p.getIdPersonaje() == null) {
            p.setIdPersonaje(sequenceGeneratorService.generateSequence("personajeId"));
        }
        if (p.getRolClan() == null) {
            p.setRolClan("Member");
        }
        if (p.getNivel() == null) {
            p.setNivel(1);
        }
        if (p.getItemLevel() == null) {
            p.setItemLevel(0);
        }
        if (p.getPuntosDkpActuales() == null) {
            p.setPuntosDkpActuales(0);
        }
        if (p.getCaido() == null) {
            p.setCaido(false);
        }
        if (p.getInventario() == null) {
            p.setInventario(new Personaje.Inventario());
        }
        if (p.getInventario().getIdInventario() == null) {
            p.getInventario().setIdInventario(p.getIdPersonaje()); // mapping 1:1 id
        }
        mongoTemplate.save(p);
        return 1;
    }

    public int update(Personaje p) {
        mongoTemplate.save(p);
        return 1;
    }

    public int deleteById(Integer id) {
        Query query = new Query(Criteria.where("idPersonaje").is(id));
        mongoTemplate.remove(query, Personaje.class);
        return 1;
    }

    public int updateRol(Integer idPersonaje, String nuevoRol) {
        Query query = new Query(Criteria.where("idPersonaje").is(idPersonaje));
        Update update = new Update().set("rolClan", nuevoRol);
        mongoTemplate.updateFirst(query, update, Personaje.class);
        return 1;
    }

    public boolean esLiderDeClan(Integer idPersonaje, Integer idClan) {
        Query query = new Query(Criteria.where("idClan").is(idClan).and("idLider").is(idPersonaje));
        return mongoTemplate.exists(query, usach.cl.laboratorio1.tablas.Clanes.class);
    }

    public boolean perteneceAUsuario(Integer idPersonaje, String username) {
        Query uQuery = new Query(Criteria.where("nombreUsuario").is(username));
        Usuario u = mongoTemplate.findOne(uQuery, Usuario.class);
        if (u == null) {
            return false;
        }
        Personaje p = findById(idPersonaje);
        return p != null && u.getIdUsuario().equals(p.getIdUsuario());
    }

    public List<Personaje> findByClan(Integer idClan) {
        Query query = new Query(Criteria.where("idClan").is(idClan))
                .with(Sort.by("rolClan").ascending().and(Sort.by("nombrePersonaje").ascending()));
        return mongoTemplate.find(query, Personaje.class);
    }

    public boolean tieneRaidsActivas(Integer idPersonaje) {
        Query query = new Query(Criteria.where("inscripciones.idPersonaje").is(idPersonaje)
                .and("estado").in("PROGRAMADA", "BOSS_MUERTO"));
        return mongoTemplate.exists(query, usach.cl.laboratorio1.tablas.Raid.class);
    }
}