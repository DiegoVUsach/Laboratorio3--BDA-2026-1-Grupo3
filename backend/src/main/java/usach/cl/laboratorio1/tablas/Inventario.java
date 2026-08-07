package usach.cl.laboratorio1.tablas;

import lombok.Data;

// Clase ligera usada como DTO para request/response del inventario.
// En MongoDB, el inventario real esta embebido dentro de Personaje.Inventario.
@Data
public class Inventario {
    private Integer idInventario;
    private Integer idPersonaje;
    private Integer armaduraEquipado;
    private Integer armaEquipado;
    private Integer accesorioEquipado;
}
