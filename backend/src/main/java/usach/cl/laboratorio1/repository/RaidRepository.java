package usach.cl.laboratorio1.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import usach.cl.laboratorio1.tablas.Personaje;
import usach.cl.laboratorio1.tablas.Raid;
import usach.cl.laboratorio1.service.SequenceGeneratorService;

import java.util.*;

@Repository
public class RaidRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private SequenceGeneratorService sequenceGeneratorService;

    @Autowired
    private ItemRepository itemRepository;

    public List<Raid> findAll(int page, int size) {
        Query query = new Query().with(PageRequest.of(page, size, Sort.by("fechaRaid").ascending()));
        return mongoTemplate.find(query, Raid.class);
    }

    public List<Raid> findAll() {
        Query query = new Query().with(Sort.by("fechaRaid").ascending());
        return mongoTemplate.find(query, Raid.class);
    }

    public Raid findById(Integer id) {
        return mongoTemplate.findById(id, Raid.class);
    }

    public Integer save(Raid r) {
        if (r.getIdRaid() == null) {
            r.setIdRaid(sequenceGeneratorService.generateSequence("raidId"));
        }
        if (r.getEstado() == null) {
            r.setEstado("PROGRAMADA");
        }
        if (r.getInscripciones() == null) {
            r.setInscripciones(new ArrayList<>());
        }
        mongoTemplate.save(r);
        return r.getIdRaid();
    }

    public int update(Raid r) {
        mongoTemplate.save(r);
        return 1;
    }

    public int deleteById(Integer id) {
        Query query = new Query(Criteria.where("idRaid").is(id));
        mongoTemplate.remove(query, Raid.class);
        return 1;
    }

    // Invitacion masiva (implementado en Java/MongoDB)
    public List<Map<String, Object>> invitarRaiders(Integer idRaid, Integer idClan) {
        Raid raid = mongoTemplate.findById(idRaid, Raid.class);
        if (raid == null) {
            throw new RuntimeException("Raid no encontrada.");
        }

        // Buscar todos los personajes del clan que sean Raider o Guild Master
        Query query = new Query(Criteria.where("idClan").is(idClan).and("rolClan").in("Raider", "Guild Master"));
        List<Personaje> raiders = mongoTemplate.find(query, Personaje.class);

        List<Map<String, Object>> invitados = new ArrayList<>();

        for (Personaje p : raiders) {
            // Verificar si ya está inscrito
            boolean yaInscrito = false;
            for (Raid.InscripcionRaid ins : raid.getInscripciones()) {
                if (ins.getIdPersonaje().equals(p.getIdPersonaje())) {
                    yaInscrito = true;
                    break;
                }
            }

            if (!yaInscrito) {
                Raid.InscripcionRaid nueva = new Raid.InscripcionRaid();
                nueva.setIdInscripcion(sequenceGeneratorService.generateSequence("inscripcionId"));
                nueva.setIdPersonaje(p.getIdPersonaje());
                nueva.setRolEnRaid("Dps"); // rol por defecto
                nueva.setConfirmado(true); // invitar los confirma por defecto en este MMORPG
                raid.getInscripciones().add(nueva);

                Map<String, Object> map = new HashMap<>();
                map.put("id_personaje", p.getIdPersonaje());
                map.put("nombre_personaje", p.getNombrePersonaje());
                map.put("item_level", p.getItemLevel());
                invitados.add(map);
            }
        }

        mongoTemplate.save(raid);
        return invitados;
    }

    public int eliminarInscripcion(Integer idRaid, Integer idPersonaje) {
        Raid r = mongoTemplate.findById(idRaid, Raid.class);
        if (r != null) {
            r.getInscripciones().removeIf(ins -> ins.getIdPersonaje().equals(idPersonaje));
            mongoTemplate.save(r);
            return 1;
        }
        return 0;
    }

    // Finalizar raid: marca BOSS_MUERTO para que el Change Stream reactive dispare el Loot y complete
    public void finalizarRaid(Integer idRaid) {
        Raid raid = mongoTemplate.findById(idRaid, Raid.class);
        if (raid == null) {
            throw new RuntimeException("La raid con ID " + idRaid + " no existe.");
        }
        if ("COMPLETADA".equals(raid.getEstado())) {
            throw new RuntimeException("La raid " + idRaid + " ya estaba finalizada.");
        }

        // Fijar estado intermedio BOSS_MUERTO para activar el Change Stream
        raid.setEstado("BOSS_MUERTO");
        mongoTemplate.save(raid);
    }
}
