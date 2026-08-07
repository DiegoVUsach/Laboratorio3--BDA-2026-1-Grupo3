package usach.cl.laboratorio1.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import usach.cl.laboratorio1.tablas.Raid;

// DTO para el endpoint de calendario de raids (Requerimiento 9).
// Envuelve la raid con los cupos disponibles ya calculados.
// Ejemplo: si la raid pide 2 tanques y ya hay 1 inscrito,
// cuposTanqueLibres = 1.
// Este calculo se hace en RaidService comparando los cupos
// totales de la raid con los inscritos por rol.
@Data
@AllArgsConstructor
public class RaidDTO {
    private Raid raid;
    private int cuposTanqueLibres;
    private int cuposHealerLibres;
    private int cuposDpsLibres;
    private List<Integer> idsParticipantes; // Añade esta lista
}