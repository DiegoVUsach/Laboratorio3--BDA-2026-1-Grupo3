package usach.cl.laboratorio1.service;

import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.OperationType;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import usach.cl.laboratorio1.repository.ItemRepository;
import usach.cl.laboratorio1.tablas.Raid;

@Service
public class ChangeStreamService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ItemRepository itemRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void startListening() {
        new Thread(() -> {
            try {
                System.out.println("Iniciando Listener de Change Streams en coleccion 'raids'...");
                mongoTemplate.getCollection("raids")
                        .watch()
                        .forEach((ChangeStreamDocument<Document> change) -> {
                            if (change.getOperationType() == OperationType.UPDATE) {
                                org.bson.BsonDocument updatedFields = change.getUpdateDescription().getUpdatedFields();
                                if (updatedFields != null && updatedFields.containsKey("estado") && "BOSS_MUERTO".equals(updatedFields.getString("estado").getValue())) {
                                    // El documentKey contiene el _id del documento modificado
                                    Integer idRaid = change.getDocumentKey().getInt32("_id").getValue();
                                    if (idRaid != null) {
                                        System.out.println("Change Stream: Muerte del Boss detectada para la Raid " + idRaid);
                                        try {
                                            // 1. Distribuir loot (ACID transaccional, con validaciones)
                                            itemRepository.distribuirBotin(idRaid);
                                            
                                            // 2. Finalizar la Raid cambiando su estado a COMPLETADA
                                            Raid raid = mongoTemplate.findById(idRaid, Raid.class);
                                            if (raid != null) {
                                                raid.setEstado("COMPLETADA");
                                                mongoTemplate.save(raid);
                                            }

                                            // 3. Actualizar la coleccion materializada de ranking de clanes
                                            itemRepository.refrescarRanking();
                                            System.out.println("Change Stream: Botin distribuido y ranking de clanes refrescado.");
                                        } catch (Exception ex) {
                                            System.err.println("Error procesando Change Stream para Raid " + idRaid + ": " + ex.getMessage());
                                        }
                                    }
                                }
                            }
                        });
            } catch (Exception e) {
                System.err.println("Change Stream finalizado o no disponible: " + e.getMessage());
            }
        }).start();
    }
}
