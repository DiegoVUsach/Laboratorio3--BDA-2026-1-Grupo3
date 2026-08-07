package usach.cl.laboratorio1.tablas;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "items")
public class Item {
    @Id
    private Integer idItem;
    private String nombreItem;
    private String rareza;
    private TipoItem tipo;
    private Integer nivel;
    private Integer costoDkp;

    public enum TipoItem {
        ARMADURA,
        ARMA,
        ACCESORIO
    }
}