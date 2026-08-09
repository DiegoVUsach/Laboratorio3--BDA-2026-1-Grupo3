package usach.cl.laboratorio1.tablas;

import java.util.ArrayList;
import java.util.List;

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

    public enum Faccion {
        LOS_PRIMORDIALES_DE_LA_LUZ,
        LOS_HIJOS_DEL_GRIS,
        LOS_MARCADOS_POR_EL_ABISMO
    }
}
