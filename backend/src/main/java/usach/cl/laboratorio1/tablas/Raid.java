package usach.cl.laboratorio1.tablas;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "raids")
public class Raid {
    @Id
    private Integer idRaid;
    private Integer idClan;
    private String nombreRaid;
    private LocalDateTime fechaRaid;
    private Integer itemLevelMinimo;
    private Integer tanques;
    private Integer healers;
    private Integer dps;
    private String estado; // "PROGRAMADA", "BOSS_MUERTO", "COMPLETADA"
    private List<InscripcionRaid> inscripciones = new ArrayList<>();

    @Data
    public static class InscripcionRaid {
        private Integer idInscripcion;
        private Integer idPersonaje;
        private String rolEnRaid;
        private Boolean confirmado = false;
    }
}