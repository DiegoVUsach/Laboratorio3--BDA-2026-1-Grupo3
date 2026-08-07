package usach.cl.laboratorio1.tablas;

import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "historial_botin")
public class HistorialBotin {
    @Id
    private Integer idEntrega;
    private Integer idPersonaje;
    private Integer idItem;
    private Integer idRaid;
    private LocalDateTime fechaEntrega;
    private String nombreItem;
    private String nombrePersonaje;
}