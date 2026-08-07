package usach.cl.laboratorio1.dto;

import lombok.Data;

@Data
public class InventarioDTO {
    private Long idInventario;
    private Long idPersonaje;

    // Información de visualización (Niveles incluidos)
    private Long armaduraEquipado;
    private Long armaEquipado;
    private Long accesorioEquipado;

    private String nombreArmadura;
    private Integer nivelArmadura;
    private String nombreArma;
    private Integer nivelArma;
    private String nombreAccesorio;
    private Integer nivelAccesorio;
}

