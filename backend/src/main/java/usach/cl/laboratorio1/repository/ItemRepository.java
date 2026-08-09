package usach.cl.laboratorio1.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
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

    @Autowired
    private PlatformTransactionManager transactionManager;


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

    // Tarea 3: Transaccion multi-documento REAL para distribuir Loot.
    // Usamos TransactionTemplate con sesion explicita para garantizar atomicidad.
    // Filtramos ANTES de insertar: solo confirmados y no-caidos reciben botin.
    // El $jsonSchema de MongoDB actua como segunda barrera de seguridad.
    public void distribuirBotin(Integer idRaid) {
        Raid raid = mongoTemplate.findById(idRaid, Raid.class);
        if (raid == null) {
            throw new RuntimeException("Raid no encontrada.");
        }

        List<Item> items = mongoTemplate.findAll(Item.class);
        if (items.isEmpty()) {
            throw new RuntimeException("No hay items cargados en el catalogo para distribuir.");
        }

        // Ejecutar dentro de una transaccion con sesion explicita
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);

        txTemplate.execute(status -> {
            Random rand = new Random();
            Set<String> itemsAsignados = new HashSet<>(); // Para evitar doble asignacion de mismo item en misma raid

            for (Raid.InscripcionRaid ins : raid.getInscripciones()) {
                // Filtrar: solo confirmados
                boolean confirmado = ins.getConfirmado() != null && ins.getConfirmado();
                if (!confirmado) continue;

                Personaje personaje = mongoTemplate.findById(ins.getIdPersonaje(), Personaje.class);
                if (personaje == null) continue;

                // Filtrar: no distribuir a caidos
                if (personaje.getCaido() != null && personaje.getCaido()) continue;

                // Elegir un item aleatorio que no haya sido asignado en esta raid
                Item randomItem = null;
                int intentos = 0;
                while (intentos < items.size()) {
                    Item candidato = items.get(rand.nextInt(items.size()));
                    String key = idRaid + "-" + candidato.getIdItem();
                    if (!itemsAsignados.contains(key)) {
                        randomItem = candidato;
                        itemsAsignados.add(key);
                        break;
                    }
                    intentos++;
                }
                if (randomItem == null) continue; // No quedan items disponibles

                LootPool lp = new LootPool();
                lp.setIdPool(sequenceGeneratorService.generateSequence("lootPoolId"));
                lp.setIdPersonaje(personaje.getIdPersonaje());
                lp.setIdItem(randomItem.getIdItem());
                lp.setIdRaid(idRaid);
                lp.setParticipoRaid(true);      // Ya filtramos: siempre true
                lp.setPersonajeCaido(false);     // Ya filtramos: siempre false
                lp.setCanjeado(false);
                lp.setFecha(LocalDateTime.now());

                // Insert atomico — el $jsonSchema valida como segunda barrera
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
            return null;
        });
    }

    public List<HistorialBotin> obtenerHistorialPorPersonaje(Integer idPersonaje) {
        Query query = new Query(Criteria.where("idPersonaje").is(idPersonaje))
                .with(Sort.by(Sort.Direction.DESC, "fechaEntrega"));
        return mongoTemplate.find(query, HistorialBotin.class);
    }

    // Tarea 4: Ranking materializado mediante Aggregation Pipeline con $merge.
    // Agrupa por personaje, calcula metricas de desempeno, y usa $merge para
    // persistir atomicamente en la coleccion materializada clan_rankings.
    // Tambien ejecuta un $bucket para agrupar personajes por rangos de iLvl.
    public void refrescarRanking() {
        // ============================================================
        // Pipeline 1: Ranking por personaje (con $merge)
        // ============================================================
        // Usamos la API nativa de MongoDB para poder incluir $merge
        com.mongodb.client.MongoDatabase database = mongoTemplate.getDb();
        List<org.bson.Document> pipeline = new ArrayList<>();

        // 1. Match: solo raids completadas
        pipeline.add(new org.bson.Document("$match",
                new org.bson.Document("estado", "COMPLETADA")));

        // 2. Unwind inscripciones
        pipeline.add(new org.bson.Document("$unwind", "$inscripciones"));

        // 3. Match: solo confirmados
        pipeline.add(new org.bson.Document("$match",
                new org.bson.Document("inscripciones.confirmado", true)));

        // 4. Lookup a personajes para obtener datos del personaje
        pipeline.add(new org.bson.Document("$lookup",
                new org.bson.Document("from", "personajes")
                        .append("localField", "inscripciones.idPersonaje")
                        .append("foreignField", "idPersonaje")
                        .append("as", "personaje_info")));

        // 5. Unwind personaje_info
        pipeline.add(new org.bson.Document("$unwind", "$personaje_info"));

        // 6. Lookup a clanes para obtener nombre del clan
        pipeline.add(new org.bson.Document("$lookup",
                new org.bson.Document("from", "clanes")
                        .append("localField", "idClan")
                        .append("foreignField", "idClan")
                        .append("as", "clan_info")));

        // 7. Unwind clan_info
        pipeline.add(new org.bson.Document("$unwind",
                new org.bson.Document("path", "$clan_info")
                        .append("preserveNullAndEmptyArrays", true)));

        // 8. Group por personaje
        pipeline.add(new org.bson.Document("$group",
                new org.bson.Document("_id", "$inscripciones.idPersonaje")
                        .append("nombre_personaje", new org.bson.Document("$first", "$personaje_info.nombrePersonaje"))
                        .append("nombre_clan", new org.bson.Document("$first", "$clan_info.nombreClan"))
                        .append("faccion", new org.bson.Document("$first", "$personaje_info.faccion"))
                        .append("raids_asistidas", new org.bson.Document("$sum", 1))
                        .append("contribucion_dkp", new org.bson.Document("$first", "$personaje_info.puntosDkpActuales"))
                        .append("item_level", new org.bson.Document("$first", "$personaje_info.itemLevel"))));

        // 9. Add fields: renombrar _id a id_personaje
        pipeline.add(new org.bson.Document("$addFields",
                new org.bson.Document("id_personaje", "$_id")));

        // 10. Sort descendente por raids asistidas y DKP
        pipeline.add(new org.bson.Document("$sort",
                new org.bson.Document("raids_asistidas", -1)
                        .append("contribucion_dkp", -1)));

        // 11. $merge: persistir atomicamente en clan_rankings (reemplaza si existe)
        pipeline.add(new org.bson.Document("$merge",
                new org.bson.Document("into", "clan_rankings")
                        .append("on", "_id")
                        .append("whenMatched", "replace")
                        .append("whenNotMatched", "insert")));

        // Ejecutar el pipeline (la salida va directo a clan_rankings via $merge)
        database.getCollection("raids").aggregate(pipeline).toCollection();

        // ============================================================
        // Pipeline 2: $bucket — agrupacion por rangos de Item Level
        // ============================================================
        // Genera un resumen de cuantos personajes hay en cada rango de iLvl.
        // Se guarda como un documento especial en clan_rankings con _id = "bucket_ilvl".
        List<org.bson.Document> bucketPipeline = new ArrayList<>();

        bucketPipeline.add(new org.bson.Document("$bucket",
                new org.bson.Document("groupBy", "$itemLevel")
                        .append("boundaries", java.util.Arrays.asList(0, 50, 100, 150, 200, 300))
                        .append("default", "300+")
                        .append("output", new org.bson.Document("count", new org.bson.Document("$sum", 1))
                                .append("personajes", new org.bson.Document("$push", "$nombrePersonaje")))));

        List<org.bson.Document> bucketResults = new ArrayList<>();
        database.getCollection("personajes").aggregate(bucketPipeline)
                .forEach(doc -> bucketResults.add(doc));

        // Guardar resultados de bucket como documento en clan_rankings
        org.bson.Document bucketDoc = new org.bson.Document("_id", "bucket_ilvl")
                .append("tipo", "bucket")
                .append("descripcion", "Distribucion de personajes por rango de Item Level")
                .append("rangos", bucketResults);

        database.getCollection("clan_rankings").replaceOne(
                new org.bson.Document("_id", "bucket_ilvl"),
                bucketDoc,
                new com.mongodb.client.model.ReplaceOptions().upsert(true));
    }

    public List<Map<String, Object>> obtenerRanking() {
        // Consultar la coleccion materializada (excluir documentos de tipo bucket)
        Query query = new Query(Criteria.where("tipo").ne("bucket"))
                .with(Sort.by(Sort.Direction.DESC, "raids_asistidas"));
        List<Map> list = mongoTemplate.find(query, Map.class, "clan_rankings");
        List<Map<String, Object>> res = new ArrayList<>();
        for (Map m : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("id_personaje", m.get("id_personaje"));
            map.put("nombre_personaje", m.get("nombre_personaje"));
            map.put("nombre_clan", m.get("nombre_clan"));
            map.put("faccion", m.get("faccion"));
            map.put("raids_asistidas", m.get("raids_asistidas"));
            map.put("contribucion_dkp", m.get("contribucion_dkp"));
            map.put("item_level", m.get("item_level"));
            res.add(map);
        }
        return res;
    }
}

