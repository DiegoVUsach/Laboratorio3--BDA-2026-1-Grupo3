package usach.cl.laboratorio1.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import usach.cl.laboratorio1.tablas.InscripcionRaid;
import usach.cl.laboratorio1.tablas.Personaje;
import usach.cl.laboratorio1.tablas.Raid;
import usach.cl.laboratorio1.service.SequenceGeneratorService;

import java.util.*;

@Repository
public class InscripcionRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private SequenceGeneratorService sequenceGeneratorService;

    public int inscribir(InscripcionRaid ins) {
        Raid raid = mongoTemplate.findById(ins.getIdRaid(), Raid.class);
        if (raid == null) {
            throw new RuntimeException("Raid no encontrada.");
        }

        // Simular trigger trg_bloquear_raid_cerrada
        if ("COMPLETADA".equals(raid.getEstado())) {
            throw new RuntimeException("Una raid completada queda congelada.");
        }

        Personaje personaje = mongoTemplate.findById(ins.getIdPersonaje(), Personaje.class);
        if (personaje == null) {
            throw new RuntimeException("Personaje no encontrado.");
        }

        // Simular trigger trg_validar_rol_raider
        if (!"Raider".equals(personaje.getRolClan()) && !"Guild Master".equals(personaje.getRolClan())) {
            throw new RuntimeException(personaje.getNombrePersonaje() + " no puede participar en raids: su rol es " 
                    + personaje.getRolClan() + ". El Guild Master debe promoverlo a Raider.");
        }

        // Simular trigger trg_validar_item_level
        if (personaje.getItemLevel() < raid.getItemLevelMinimo()) {
            throw new RuntimeException("Tu item level (" + personaje.getItemLevel() 
                    + ") es menor al minimo requerido (" + raid.getItemLevelMinimo() + ").");
        }

        // Simular trigger trg_validar_raid_mismo_clan
        if (personaje.getIdClan() == null || !personaje.getIdClan().equals(raid.getIdClan())) {
            throw new RuntimeException("No puedes inscribirte en raids de otro clan.");
        }

        // Buscar si ya esta inscrito
        Raid.InscripcionRaid existente = null;
        for (Raid.InscripcionRaid rIns : raid.getInscripciones()) {
            if (rIns.getIdPersonaje().equals(ins.getIdPersonaje())) {
                existente = rIns;
                break;
            }
        }

        if (existente != null) {
            // Actualizar rol
            existente.setRolEnRaid(ins.getRolEnRaid());
        } else {
            // Crear nueva inscripcion
            Raid.InscripcionRaid nueva = new Raid.InscripcionRaid();
            nueva.setIdInscripcion(sequenceGeneratorService.generateSequence("inscripcionId"));
            nueva.setIdPersonaje(ins.getIdPersonaje());
            nueva.setRolEnRaid(ins.getRolEnRaid());
            nueva.setConfirmado(ins.getConfirmado() != null && ins.getConfirmado());
            raid.getInscripciones().add(nueva);
        }

        mongoTemplate.save(raid);
        return 1;
    }

    public Integer contarInscritosPorRol(Integer idRaid, String rol) {
        Raid raid = mongoTemplate.findById(idRaid, Raid.class);
        if (raid == null) return 0;
        int count = 0;
        for (Raid.InscripcionRaid ins : raid.getInscripciones()) {
            if (rol.equalsIgnoreCase(ins.getRolEnRaid())) {
                count++;
            }
        }
        return count;
    }

    public int confirmar(Integer idInscripcion) {
        Query query = new Query(Criteria.where("inscripciones.idInscripcion").is(idInscripcion));
        Raid raid = mongoTemplate.findOne(query, Raid.class);
        if (raid == null) {
            throw new RuntimeException("Inscripcion no encontrada.");
        }
        for (Raid.InscripcionRaid ins : raid.getInscripciones()) {
            if (ins.getIdInscripcion().equals(idInscripcion)) {
                ins.setConfirmado(true);
                break;
            }
        }
        mongoTemplate.save(raid);
        return 1;
    }

    public List<Integer> obtenerIdsParticipantes(Integer idRaid) {
        Raid raid = mongoTemplate.findById(idRaid, Raid.class);
        if (raid == null) return new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        for (Raid.InscripcionRaid ins : raid.getInscripciones()) {
            ids.add(ins.getIdPersonaje());
        }
        return ids;
    }

    public List<Map<String, Object>> findByRaid(Integer idRaid) {
        Raid raid = mongoTemplate.findById(idRaid, Raid.class);
        if (raid == null) return new ArrayList<>();
        List<Map<String, Object>> res = new ArrayList<>();
        for (Raid.InscripcionRaid ins : raid.getInscripciones()) {
            Personaje p = mongoTemplate.findById(ins.getIdPersonaje(), Personaje.class);
            if (p != null) {
                Map<String, Object> map = new HashMap<>();
                map.put("id_inscripcion", ins.getIdInscripcion());
                map.put("id_personaje", ins.getIdPersonaje());
                map.put("nombre_personaje", p.getNombrePersonaje());
                map.put("rol_en_raid", ins.getRolEnRaid());
                map.put("confirmado", ins.getConfirmado());
                map.put("item_level", p.getItemLevel());
                map.put("clase", p.getClase());
                res.add(map);
            }
        }
        // Ordenar por rol y item_level desc
        res.sort((a, b) -> {
            String rolA = (String) a.get("rol_en_raid");
            String rolB = (String) b.get("rol_en_raid");
            int rolComp = rolA.compareTo(rolB);
            if (rolComp != 0) return rolComp;
            Integer ilvlA = (Integer) a.get("item_level");
            Integer ilvlB = (Integer) b.get("item_level");
            return ilvlB.compareTo(ilvlA);
        });
        return res;
    }

    public List<Map<String, Object>> findByPersonaje(Integer idPersonaje) {
        Query query = new Query(Criteria.where("inscripciones.idPersonaje").is(idPersonaje));
        List<Raid> raids = mongoTemplate.find(query, Raid.class);
        List<Map<String, Object>> res = new ArrayList<>();
        for (Raid r : raids) {
            for (Raid.InscripcionRaid ins : r.getInscripciones()) {
                if (ins.getIdPersonaje().equals(idPersonaje)) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id_inscripcion", ins.getIdInscripcion());
                    map.put("id_raid", r.getIdRaid());
                    map.put("rol_en_raid", ins.getRolEnRaid());
                    map.put("confirmado", ins.getConfirmado());
                    res.add(map);
                }
            }
        }
        return res;
    }

    public Integer obtenerPersonajeDeInscripcion(Integer idInscripcion) {
        Query query = new Query(Criteria.where("inscripciones.idInscripcion").is(idInscripcion));
        Raid r = mongoTemplate.findOne(query, Raid.class);
        if (r == null) return null;
        for (Raid.InscripcionRaid ins : r.getInscripciones()) {
            if (ins.getIdInscripcion().equals(idInscripcion)) {
                return ins.getIdPersonaje();
            }
        }
        return null;
    }
}
