package usach.cl.laboratorio1.tablas;

import lombok.Data;

// Clase ligera usada como DTO para recibir inscripciones del frontend.
// En MongoDB, las inscripciones reales estan embebidas dentro de Raid.InscripcionRaid.
@Data
public class InscripcionRaid {
    private Integer idInscripcion;
    private Integer idRaid;
    private Integer idPersonaje;
    private String rolEnRaid;
    private Boolean confirmado;
}