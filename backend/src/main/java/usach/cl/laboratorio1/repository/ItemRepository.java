package usach.cl.laboratorio1.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import usach.cl.laboratorio1.tablas.*;
import usach.cl.laboratorio1.service.SequenceGeneratorService;

import java.time.LocalDateTime;
import java.util.*;

@Repository
public class ItemRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private SequenceGeneratorService sequenceGeneratorService;

    @Autowired
    private PersonajeRepository personajeRepository;

    public List<Item> findAll(int page, int size) {
        Query query = new Query().with(PageRequest.of(page, size, Sort.by("idItem").ascending()));
        return mongoTemplate.find(query, Item.class);
    }

    public List<Item> findAll() {
        return mongoTemplate.findAll(Item.class);
    }

    public Item findById(Integer id) {
        return mongoTemplate.findById(id, Item.class);
    }

    public int save(Item item) {
        if (item.getIdItem() == null) {
            item.setIdItem(sequenceGeneratorService.generateSequence("itemId"));
        }
        if (item.getRareza() == null) {
            item.setRareza("Comun");
        }
        if (item.getTipo() == null) {
            item.setTipo(Item.TipoItem.ACCESORIO);
        }
        if (item.getNivel() == null) {
            item.setNivel(1);
        }
        mongoTemplate.save(item);
        return 1;
    }

    public int update(Item item) {
        mongoTemplate.save(item);
        return 1;
    }

    public int deleteById(Integer id) {
        Query query = new Query(Criteria.where("idItem").is(id));
        mongoTemplate.remove(query, Item.class);
        return 1;
    }

    public List<Map<String, Object>> miPool(Integer idPersonaje) {
        Query query = new Query(Criteria.where("idPersonaje").is(idPersonaje))
                .with(Sort.by(Sort.Direction.ASC, "canjeado").and(Sort.by(Sort.Direction.DESC, "idPool")));
        List<LootPool> list = mongoTemplate.find(query, LootPool.class);
        List<Map<String, Object>> res = new ArrayList<>();
        for (LootPool lp : list) {
            Item item = mongoTemplate.findById(lp.getIdItem(), Item.class);
            if (item != null) {
                Map<String, Object> map = new HashMap<>();
                map.put("id_pool", lp.getIdPool());
                map.put("id_item", lp.getIdItem());
                map.put("nombre_item", item.getNombreItem());
                map.put("rareza", item.getRareza());
                map.put("tipo", item.getTipo().name());
                map.put("nivel", item.getNivel());
                map.put("costo_dkp", item.getCostoDkp());
                map.put("canjeado", lp.getCanjeado());
                map.put("id_raid", lp.getIdRaid());
                res.add(map);
            }
        }
        return res;
    }

    // Canjear un item (transaccional en Java)
    public void canjear(Integer idPool, Integer idPersonaje) {
        LootPool lp = mongoTemplate.findById(idPool, LootPool.class);
        if (lp == null) {
            throw new RuntimeException("El item no esta en tu pool de canje.");
        }
        if (!lp.getIdPersonaje().equals(idPersonaje)) {
            throw new RuntimeException("No tienes permiso sobre este botin.");
        }
        if (lp.getCanjeado()) {
            throw new RuntimeException("Este botin ya fue canjeado.");
        }

        Item item = mongoTemplate.findById(lp.getIdItem(), Item.class);
        if (item == null) {
            throw new RuntimeException("El item no existe en la base de datos.");
        }

        Personaje personaje = mongoTemplate.findById(idPersonaje, Personaje.class);
        if (personaje == null) {
            throw new RuntimeException("Personaje no encontrado.");
        }

        if (personaje.getPuntosDkpActuales() < item.getCostoDkp()) {
            throw new RuntimeException("Puntos DKP insuficientes para canjear este item.");
        }

        // Descontar DKP
        personaje.setPuntosDkpActuales(personaje.getPuntosDkpActuales() - item.getCostoDkp());

        // Agregar al inventario
        if (personaje.getInventario() == null) {
            personaje.setInventario(new Personaje.Inventario());
        }
        if (personaje.getInventario().getItems() == null) {
            personaje.getInventario().setItems(new ArrayList<>());
        }
        personaje.getInventario().getItems().add(item.getIdItem());
        personajeRepository.update(personaje);

        // Marcar canjeado
        lp.setCanjeado(true);
        mongoTemplate.save(lp);

        // Registrar en historial
        HistorialBotin hist = new HistorialBotin();
        hist.setIdEntrega(sequenceGeneratorService.generateSequence("historialBotinId"));
        hist.setIdPersonaje(idPersonaje);
        hist.setIdItem(item.getIdItem());
        hist.setIdRaid(lp.getIdRaid());
        hist.setNombreItem(item.getNombreItem());
        hist.setNombrePersonaje(personaje.getNombrePersonaje());
        hist.setFechaEntrega(LocalDateTime.now());
        mongoTemplate.save(hist);

        // Notificacion
        Notificacion notif = new Notificacion();
        notif.setIdNotificacion(sequenceGeneratorService.generateSequence("notificacionId"));
        notif.setIdPersonaje(idPersonaje);
        notif.setTipo("BOTIN_CANJEADO");
        notif.setMensaje("Has canjeado " + item.getNombreItem() + " por " + item.getCostoDkp() + " DKP.");
        notif.setLeida(false);
        notif.setFecha(LocalDateTime.now());
        mongoTemplate.save(notif);
    }

    // Tarea 3: Transaccion multi-documento para distribuir Loot.
    // Usamos @org.springframework.transaction.annotation.Transactional para asegurar
    // que la operacion sea atomica. Si un item no puede ser asignado (ej. por validacion
    // del $jsonSchema de MongoDB al estar el jugador caido), la transaccion completa se revierte.
    @org.springframework.transaction.annotation.Transactional
    public void distribuirBotin(Integer idRaid) {
        Raid raid = mongoTemplate.findById(idRaid, Raid.class);
        if (raid == null) {
            throw new RuntimeException("Raid no encontrada.");
        }

        List<Item> items = mongoTemplate.findAll(Item.class);
        if (items.isEmpty()) {
            throw new RuntimeException("No hay items cargados en el catalogo para distribuir.");
        }

        Random rand = new Random();

        for (Raid.InscripcionRaid ins : raid.getInscripciones()) {
            // Solo distribuir a confirmados
            boolean confirmado = ins.getConfirmado() != null && ins.getConfirmado();
            
            Personaje personaje = mongoTemplate.findById(ins.getIdPersonaje(), Personaje.class);
            if (personaje == null) continue;

            Item randomItem = items.get(rand.nextInt(items.size()));

            LootPool lp = new LootPool();
            lp.setIdPool(sequenceGeneratorService.generateSequence("lootPoolId"));
            lp.setIdPersonaje(personaje.getIdPersonaje());
            lp.setIdItem(randomItem.getIdItem());
            lp.setIdRaid(idRaid);
            
            // Establecer valores que validara el Schema Validation ($jsonSchema) de MongoDB
            lp.setParticipoRaid(confirmado);
            lp.setPersonajeCaido(personaje.getCaido() != null && personaje.getCaido());
            lp.setCanjeado(false);
            lp.setFecha(LocalDateTime.now());

            // Este insert disparara la validacion del Schema Validation de MongoDB.
            // Si el personaje no participo (confirmado=false) o esta caido (caido=true),
            // la validacion fallara y lanzara una excepcion, abortando la transaccion.
            mongoTemplate.insert(lp);

            // Crear notificacion de botin
            Notificacion notif = new Notificacion();
            notif.setIdNotificacion(sequenceGeneratorService.generateSequence("notificacionId"));
            notif.setIdPersonaje(personaje.getIdPersonaje());
            notif.setTipo("BOTIN");
            notif.setMensaje("Recibiste un item para canjear de la raid: " + randomItem.getNombreItem());
            notif.setLeida(false);
            notif.setFecha(LocalDateTime.now());
            mongoTemplate.save(notif);
        }
    }

    public List<HistorialBotin> obtenerHistorialPorPersonaje(Integer idPersonaje) {
        Query query = new Query(Criteria.where("idPersonaje").is(idPersonaje))
                .with(Sort.by(Sort.Direction.DESC, "fechaEntrega"));
        return mongoTemplate.find(query, HistorialBotin.class);
    }

    // Requerimiento 7: ranking materializado mediante Pipeline de Agregación
    public void refrescarRanking() {
        mongoTemplate.remove(new Query(), "clan_rankings");

        // Pipeline de Agregación: $lookup, $unwind, $group, $sort
        org.springframework.data.mongodb.core.aggregation.Aggregation aggregation = org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                // 1. Filtrar solo raids completadas
                org.springframework.data.mongodb.core.aggregation.Aggregation.match(Criteria.where("estado").is("COMPLETADA")),
                
                // 2. Unwind inscripciones para contar asistencias individuales
                org.springframework.data.mongodb.core.aggregation.Aggregation.unwind("inscripciones"),
                
                // 3. Filtrar solo los que asistieron (confirmado = true)
                org.springframework.data.mongodb.core.aggregation.Aggregation.match(Criteria.where("inscripciones.confirmado").is(true)),
                
                // 4. Lookup a la coleccion clanes para obtener el nombre del clan
                org.springframework.data.mongodb.core.aggregation.Aggregation.lookup("clanes", "idClan", "idClan", "clan_info"),
                
                // 5. Unwind clan_info
                org.springframework.data.mongodb.core.aggregation.Aggregation.unwind("clan_info"),
                
                // 6. Group por clan y calcular metricas (ej. asistencias totales y conteo de raids)
                org.springframework.data.mongodb.core.aggregation.Aggregation.group("idClan")
                        .first("clan_info.nombreClan").as("nombreClan")
                        .count().as("asistenciasTotales"),
                
                // 7. Sort descendente por asistencias
                org.springframework.data.mongodb.core.aggregation.Aggregation.sort(Sort.Direction.DESC, "asistenciasTotales")
        );

        org.springframework.data.mongodb.core.aggregation.AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, "raids", Map.class);
        List<Map> rankingResult = results.getMappedResults();

        // Guardar resultados en la coleccion materializada
        for (Map map : rankingResult) {
            mongoTemplate.insert(map, "clan_rankings");
        }
    }

    public List<Map<String, Object>> obtenerRanking() {
        // Consultar la colección materializada
        List<Map> list = mongoTemplate.findAll(Map.class, "clan_rankings");
        List<Map<String, Object>> res = new ArrayList<>();
        for (Map m : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("id_personaje", m.get("id_personaje"));
            map.put("nombre_personaje", m.get("nombre_personaje"));
            map.put("nombre_clan", m.get("nombre_clan"));
            map.put("faccion", m.get("faccion"));
            map.put("raids_invitado", m.get("raids_invitado"));
            map.put("raids_asistidas", m.get("raids_asistidas"));
            map.put("ausencias", m.get("ausencias"));
            map.put("asistencia_perfecta", m.get("asistencia_perfecta"));
            map.put("contribucion_dkp", m.get("contribucion_dkp"));
            res.add(map);
        }
        return res;
    }
}