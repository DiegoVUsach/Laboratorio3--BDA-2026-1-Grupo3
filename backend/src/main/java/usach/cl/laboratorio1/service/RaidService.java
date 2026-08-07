package usach.cl.laboratorio1.service;

import usach.cl.laboratorio1.dto.RaidDTO;
import usach.cl.laboratorio1.repository.InscripcionRepository;
import usach.cl.laboratorio1.repository.RaidRepository;
import usach.cl.laboratorio1.tablas.Raid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RaidService {

    @Autowired
    private RaidRepository raidRepository;

    @Autowired
    private InscripcionRepository inscripcionRepository;

    // Requerimiento 9: Calendario de raids con cupos disponibles.
    // Para cada raid, calcula cuantos cupos quedan por rol:
    // cupos libres = cupos totales de la raid - inscritos en ese rol.
    // Math.max(x, 0) evita numeros negativos si se sobreinscribieron.
        public List<RaidDTO> getCalendarioSemanal() {
    List<Raid> raids = raidRepository.findAll();
    return raids.stream().map(r -> {
        int tanquesLibres = r.getTanques() 
                - inscripcionRepository.contarInscritosPorRol(r.getIdRaid(), "TANQUE");
        int healersLibres = r.getHealers() 
                - inscripcionRepository.contarInscritosPorRol(r.getIdRaid(), "HEALER");
        int dpsLibres = r.getDps() 
                - inscripcionRepository.contarInscritosPorRol(r.getIdRaid(), "DPS");

        // NUEVO: Obtener la lista de IDs inscritos
        List<Integer> participantes = inscripcionRepository.obtenerIdsParticipantes(r.getIdRaid());

        return new RaidDTO(
                r,
                Math.max(tanquesLibres, 0),
                Math.max(healersLibres, 0),
                Math.max(dpsLibres, 0),
                participantes // Se pasa al constructor del DTO
        );
    }).collect(Collectors.toList());
        }
}