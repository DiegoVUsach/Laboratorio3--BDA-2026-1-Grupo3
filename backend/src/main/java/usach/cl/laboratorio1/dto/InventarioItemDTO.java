package usach.cl.laboratorio1.dto;

import lombok.Data;
import usach.cl.laboratorio1.tablas.Item;

@Data
public class InventarioItemDTO {
    private Integer idInventario;
    private Integer idItem;
    private String nombreItem;
    private String rareza;
    private Item.TipoItem tipo;
    private Integer nivel;
    private Integer costoDkp;
}
