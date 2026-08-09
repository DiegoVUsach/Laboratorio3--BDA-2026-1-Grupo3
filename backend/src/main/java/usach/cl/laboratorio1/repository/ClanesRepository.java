package usach.cl.laboratorio1.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import usach.cl.laboratorio1.tablas.Clanes;
import usach.cl.laboratorio1.tablas.Personaje;
import usach.cl.laboratorio1.service.SequenceGeneratorService;

import java.util.ArrayList;
import java.util.List;

@Repository
public class ClanesRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private SequenceGeneratorService sequenceGeneratorService;

    public List<Clanes> findAll() {
        return mongoTemplate.findAll(Clanes.class);
    }

    public Clanes findById(Integer id) {
        return mongoTemplate.findById(id, Clanes.class);
    }

    public int save(Clanes entity) {
        if (entity.getIdClan() == null) {
            entity.setIdClan(sequenceGeneratorService.generateSequence("clanId"));
        }
        if (entity.getAuditoriaLiderazgo() == null) {
            entity.setAuditoriaLiderazgo(new ArrayList<>());
        }
        mongoTemplate.save(entity);
        return 1;
    }

    public int update(Clanes entity) {
        mongoTemplate.save(entity);
        return 1;
    }

    public int deleteById(Integer id) {
        Query query = new Query(Criteria.where("idClan").is(id));
        mongoTemplate.remove(query, Clanes.class);
        return 1;
    }

    // --- Ciclo de vida del clan (Implementados en Java reemplazando Stored Procedures) ---

    public void fundarClan(Integer idPersonaje, String nombreClan) {
        Personaje p = mongoTemplate.findById(idPersonaje, Personaje.class);
        if (p == null) {
            throw new RuntimeException("Personaje no encontrado.");
        }
        if (p.getIdClan() != null) {
            throw new RuntimeException("Ya perteneces a un clan. Debes salir antes de fundar uno nuevo.");
        }

        // Crear clan
        Clanes clan = new Clanes();
        clan.setIdClan(sequenceGeneratorService.generateSequence("clanId"));
        clan.setNombreClan(nombreClan);
        clan.setIdLider(idPersonaje);
        clan.setFaccion(p.getFaccion().name());
        clan.setAuditoriaLiderazgo(new ArrayList<>());
        mongoTemplate.save(clan);

        // Promover personaje a Guild Master de este clan
        p.setIdClan(clan.getIdClan());
        p.setRolClan("Guild Master");
        mongoTemplate.save(p);
    }

    public void unirseClan(Integer idPersonaje, Integer idClan) {
        Personaje p = mongoTemplate.findById(idPersonaje, Personaje.class);
        if (p == null) {
            throw new RuntimeException("Personaje no encontrado.");
        }
        if (p.getIdClan() != null) {
            throw new RuntimeException("Ya perteneces a un clan. Debes salir antes de unirte a otro.");
        }

        Clanes clan = mongoTemplate.findById(idClan, Clanes.class);
        if (clan == null) {
            throw new RuntimeException("El clan no existe.");
        }

        // Validar faccion
        if (clan.getFaccion() != null && !clan.getFaccion().equals(p.getFaccion().name())) {
            throw new RuntimeException("Un personaje solo entra a clanes de su faccion.");
        }

        p.setIdClan(idClan);
        p.setRolClan("Member");
        mongoTemplate.save(p);
    }

    public void salirClan(Integer idPersonaje) {
        Personaje p = mongoTemplate.findById(idPersonaje, Personaje.class);
        if (p == null) {
            throw new RuntimeException("Personaje no encontrado.");
        }
        if (p.getIdClan() == null) {
            throw new RuntimeException("No perteneces a ningun clan.");
        }
        if ("Guild Master".equals(p.getRolClan())) {
            throw new RuntimeException("El Guild Master debe transferir el liderazgo o disolver el clan antes de salir.");
        }

        p.setIdClan(null);
        p.setRolClan("Member");
        mongoTemplate.save(p);
    }

    public void disolverClan(Integer idClan, Integer idEjecutor) {
        Clanes clan = mongoTemplate.findById(idClan, Clanes.class);
        if (clan == null) {
            throw new RuntimeException("El clan con ID " + idClan + " no existe.");
        }
        if (!clan.getIdLider().equals(idEjecutor)) {
            throw new RuntimeException("Solo el Guild Master puede disolver el clan.");
        }

        // Desasociar a todos los miembros del clan
        Query query = new Query(Criteria.where("idClan").is(idClan));
        Update update = new Update().set("idClan", null).set("rolClan", "Member");
        mongoTemplate.updateMulti(query, update, Personaje.class);

        // Eliminar clan
        mongoTemplate.remove(clan);
    }
}
