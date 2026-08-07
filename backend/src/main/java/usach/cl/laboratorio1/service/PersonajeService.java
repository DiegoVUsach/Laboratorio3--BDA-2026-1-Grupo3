package usach.cl.laboratorio1.service;

import usach.cl.laboratorio1.repository.PersonajeRepository;
import usach.cl.laboratorio1.tablas.Personaje;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PersonajeService {

    @Autowired
    private PersonajeRepository personajeRepository;

    // Asignar roles dentro del clan (Req 2).
    // Solo el Guild Master puede cambiar roles de otros miembros.
    // Flujo de validaciones:
    // 1. El personaje "ejecutor" debe pertenecer al usuario del JWT
    // 2. El ejecutor debe ser lider del clan (Guild Master)
    // 3. El objetivo debe ser del mismo clan
    // 4. No se puede asignar "Guild Master" por esta via
    //    (para eso existe transferLeadership en ClanesService)
    public void asignarNuevoRol(String usernameAuth, Integer idEjecutor,
                                Integer idObjetivo, String nuevoRol) {

        // Validacion JWT: ¿el ejecutor es del usuario autenticado?
        if (!personajeRepository.perteneceAUsuario(idEjecutor, usernameAuth)) {
            throw new RuntimeException("No tienes permiso sobre el personaje ejecutor.");
        }

        Personaje ejecutor = personajeRepository.findById(idEjecutor);
        Personaje objetivo = personajeRepository.findById(idObjetivo);

        // ¿El ejecutor es realmente el lider?
        if (!personajeRepository.esLiderDeClan(idEjecutor, ejecutor.getIdClan())) {
            throw new RuntimeException("Solo el Guild Master puede cambiar rangos.");
        }

        // ¿El objetivo esta en el mismo clan?
        if (objetivo == null || !objetivo.getIdClan().equals(ejecutor.getIdClan())) {
            throw new RuntimeException("El objetivo no pertenece a tu clan.");
        }

        // Bloquear asignacion directa de Guild Master
        if (nuevoRol.equalsIgnoreCase("Guild Master")) {
            throw new RuntimeException("Para transferir el liderazgo usa la opcion de Transferir.");
        }

        // Si se le quiere bajar a Member, validar que no tenga raids activas
        if (nuevoRol.equalsIgnoreCase("Member") && (objetivo.getRolClan().equalsIgnoreCase("Raider") || objetivo.getRolClan().equalsIgnoreCase("Guild Master"))) {
            // Buscamos si tiene raids activas
            if (personajeRepository.tieneRaidsActivas(idObjetivo)) {
                throw new RuntimeException("El personaje tiene raids activas, no puedes quitarle el rol Raider.");
            }
        }

        personajeRepository.updateRol(idObjetivo, nuevoRol);
    }
}