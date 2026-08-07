package usach.cl.laboratorio1.tablas;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "personajes")
public class Personaje {
    @Id
    private Integer idPersonaje;
    private Integer idUsuario;
    private Integer idClan;
    private String nombrePersonaje;
    private String clase;
    private Integer nivel;
    private Faccion faccion;
    private String rolClan;
    private Integer itemLevel;
    private Integer puntosDkpActuales;
    private Boolean caido = false;
    private Inventario inventario = new Inventario();

    @Data
    public static class Inventario {
        private Integer idInventario;
        private Integer armaduraEquipado;
        private Integer armaEquipado;
        private Integer accesorioEquipado;
        private List<Integer> items = new ArrayList<>();
    }

    public enum Faccion {
        PRIMORDIALES_LUZ("Los Primordiales de la Luz"),
        HIJOS_GRIS("Los Hijos del Gris"),
        MARCADOS_ABISMO("Los Marcados por el Abismo");

        private final String label;

        Faccion(String label) {
            this.label = label;
        }

        @JsonValue
        public String getLabel() {
            return label;
        }

        @JsonCreator
        public static Faccion fromLabel(String label) {
            if (label == null) {
                return null;
            }
            for (Faccion faccion : values()) {
                if (faccion.label.equals(label)) {
                    return faccion;
                }
            }
            throw new IllegalArgumentException("Faccion invalida.");
        }
    }
}