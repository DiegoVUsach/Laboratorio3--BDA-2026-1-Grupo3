package usach.cl.laboratorio1.service;

import usach.cl.laboratorio1.repository.ClanesRepository;
import usach.cl.laboratorio1.repository.PersonajeRepository;
import usach.cl.laboratorio1.tablas.Clanes;
import usach.cl.laboratorio1.tablas.Personaje;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClanesService {

    @Autowired
    private ClanesRepository clanesRepository;

    @Autowired
    private PersonajeRepository personajeRepository;

    public List<Clanes> findAll() {
        return clanesRepository.findAll();
    }

    public Clanes findById(Integer id) {
        return clanesRepository.findById(id);
    }

    public void save(Clanes clan) {
        clanesRepository.save(clan);
    }
    public void update(Clanes clan) {
        clanesRepository.update(clan);
    }

    // Eliminar un clan. Solo el lider puede hacerlo.
    // Valida que el usuario del JWT sea dueño del personaje lider.
    public void delete(String usernameAuth, Integer idClan) {
        Clanes clan = clanesRepository.findById(idClan);
        if (clan == null) throw new RuntimeException("Clan no encontrado.");
        if (clan.getIdLider() == null) throw new RuntimeException("El clan no tiene lider asignado.");
        if (!personajeRepository.perteneceAUsuario(clan.getIdLider(), usernameAuth)) {
            throw new RuntimeException("Solo el lider del clan puede disolverlo.");
        }
        // Disolver via SP: baja los roles de los miembros y borra en una transaccion.
        clanesRepository.disolverClan(idClan, clan.getIdLider());
    }

    // --- Ciclo de vida del clan ---
    public void fundarClan(Integer idPersonaje, String nombreClan) {
        clanesRepository.fundarClan(idPersonaje, nombreClan);
    }
    public void unirseClan(Integer idPersonaje, Integer idClan) {
        clanesRepository.unirseClan(idPersonaje, idClan);
    }
    public void salirClan(Integer idPersonaje) {
        clanesRepository.salirClan(idPersonaje);
    }
    public void disolverClan(Integer idClan, Integer idEjecutor) {
        clanesRepository.disolverClan(idClan, idEjecutor);
    }

    // Transferencia de liderazgo (Req 2 + Req 6).
    // Este metodo tiene MUCHA validacion porque es una operacion sensible:
    // 1. Verifica que el clan existe
    // 2. Verifica que el ejecutor ES el lider actual
    // 3. Verifica que el ejecutor pertenece al usuario del JWT (seguridad)
    // 4. Verifica que el nuevo lider pertenece al mismo clan
    // 5. Ejecuta el UPDATE (que dispara el Trigger de auditoria)
    // 6. Ajusta los roles: el antiguo lider baja a Member, el nuevo sube a GM
    public void transferLeadership(String usernameAuth, Integer idClan,
                                   Integer idCurrentLeader, Integer idNewLeader) {
        Clanes clan = clanesRepository.findById(idClan);
        if (clan == null) {
            throw new RuntimeException("Clan no encontrado.");
        }

        if (!clan.getIdLider().equals(idCurrentLeader)) {
            throw new RuntimeException("El personaje ejecutor no es el lider actual.");
        }

        // Seguridad JWT: el personaje del lider actual debe pertenecer
        // al usuario que hizo la peticion (el del token)
        if (!personajeRepository.perteneceAUsuario(idCurrentLeader, usernameAuth)) {
            throw new RuntimeException("No tienes permiso sobre este personaje.");
        }

        Personaje newLeaderObj = personajeRepository.findById(idNewLeader);
        if (newLeaderObj == null || !newLeaderObj.getIdClan().equals(idClan)) {
            throw new RuntimeException("El nuevo lider debe pertenecer al mismo clan.");
        }

        // Este update dispara el TRIGGER trg_auditoria_liderazgo
        clan.setIdLider(idNewLeader);
        clanesRepository.update(clan);

        // El GM saliente queda como Raider (no Member) para mantener sus inscripciones activas.
        // El nuevo GM sube de Raider a Guild Master — el trigger permite esta promocion.
        personajeRepository.updateRol(idCurrentLeader, "Raider");
        personajeRepository.updateRol(idNewLeader, "Guild Master");
    }
}