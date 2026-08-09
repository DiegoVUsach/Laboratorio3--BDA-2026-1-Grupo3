package usach.cl.laboratorio1.tablas;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

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

    /**
     * En MongoDB se guarda el nombre de la constante (LOS_PRIMORDIALES_DE_LA_LUZ),
     * porque Spring Data serializa los enum por su name(). Hacia la API, en cambio,
     * se expone el nombre legible mediante @JsonValue / @JsonCreator, que es lo que
     * envia y muestra el frontend.
     */
    public enum Faccion {
        LOS_PRIMORDIALES_DE_LA_LUZ("Los Primordiales de la Luz"),
        LOS_HIJOS_DEL_GRIS("Los Hijos del Gris"),
        LOS_MARCADOS_POR_EL_ABISMO("Los Marcados por el Abismo");

        private final String label;

        Faccion(String label) {
            this.label = label;
        }

        @JsonValue
        public String getLabel() {
            return label;
        }

        @JsonCreator
        public static Faccion fromLabel(String valor) {
            if (valor == null) {
                return null;
            }
            for (Faccion f : values()) {
                if (f.label.equalsIgnoreCase(valor) || f.name().equalsIgnoreCase(valor)) {
                    return f;
                }
            }
            throw new IllegalArgumentException("Faccion invalida: " + valor);
        }
    }
}
