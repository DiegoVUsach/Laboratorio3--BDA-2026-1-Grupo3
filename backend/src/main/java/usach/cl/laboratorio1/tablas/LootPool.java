package usach.cl.laboratorio1.tablas;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "loot_pool")
public class LootPool {
    @Id
    private Integer idPool;
    private Integer idPersonaje;
    private Integer idItem;
    private Integer idRaid;
    private Boolean participoRaid = true;
    private Boolean personajeCaido = false;
    private Boolean canjeado = false;
    private LocalDateTime fecha = LocalDateTime.now();
}
