package usach.cl.laboratorio1.tablas;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "notificaciones")
public class Notificacion {
    @Id
    private Integer idNotificacion;
    private Integer idPersonaje;
    private String tipo;
    private String mensaje;
    private Boolean leida = false;
    private LocalDateTime fecha = LocalDateTime.now();
}
