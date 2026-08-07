package usach.cl.laboratorio1.tablas;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "clanes")
public class Clanes {
    @Id
    private Integer idClan;
    private Integer idLider;
    private String nombreClan;
    private String faccion;
    private List<AuditoriaLiderazgo> auditoriaLiderazgo = new ArrayList<>();

    @Data
    public static class AuditoriaLiderazgo {
        private Integer idAuditoria;
        private Integer idLiderAnterior;
        private Integer idLiderNuevo;
        private LocalDateTime fechaTransferencia;
    }
}