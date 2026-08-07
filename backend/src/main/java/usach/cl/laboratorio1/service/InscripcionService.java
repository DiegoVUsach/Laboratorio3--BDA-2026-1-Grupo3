package usach.cl.laboratorio1.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import usach.cl.laboratorio1.repository.InscripcionRepository;
import usach.cl.laboratorio1.repository.PersonajeRepository;
import usach.cl.laboratorio1.repository.RaidRepository;
import usach.cl.laboratorio1.tablas.InscripcionRaid;
import usach.cl.laboratorio1.tablas.Personaje;
import usach.cl.laboratorio1.tablas.Raid;

@Service
public class InscripcionService {

    @Autowired
    private InscripcionRepository inscripcionRepository;

    @Autowired
    private PersonajeRepository personajeRepository;

    @Autowired
    private RaidRepository raidRepository;

    // Inscribir un personaje a una raid (Req 2 + Req 5).
    // Validaciones en Java:
    // 1. El personaje debe pertenecer al usuario del JWT
    // 2. La raid debe existir y estar en estado PROGRAMADA
    // Validacion en PostgreSQL (automatica):
    // 3. El Trigger trg_validar_item_level verifica el nivel de equipo
    //    Si no cumple, PostgreSQL lanza excepcion y el INSERT se cancela.
public void anotarPersonaje(InscripcionRaid inscripcion, String usernameAuth) {
    // 1. Validar propiedad del personaje
    if (!personajeRepository.perteneceAUsuario(inscripcion.getIdPersonaje(), usernameAuth)) {
        throw new RuntimeException("El personaje no pertenece a tu cuenta.");
    }

    // 2. Validar existencia y estado de la Raid
    Raid raid = raidRepository.findById(inscripcion.getIdRaid());
    if (raid == null || !"PROGRAMADA".equals(raid.getEstado())) {
        throw new RuntimeException("La Raid no existe o ya no acepta inscripciones.");
    }

    // 3. NUEVA: Validar Clase y Rol (Normalizado para evitar errores de mayúsculas/tildes)
    Personaje p = personajeRepository.findById(inscripcion.getIdPersonaje());
    validarClaseYRol(p.getClase(), inscripcion.getRolEnRaid());

    // 4. Validar que haya cupos disponibles para el rol seleccionado
    String rolEnRaid = inscripcion.getRolEnRaid();
    if (rolEnRaid != null) {
        int inscritos = inscripcionRepository.contarInscritosPorRol(
                inscripcion.getIdRaid(), rolEnRaid);
        
        // Usamos la normalización aquí también para el switch de cupos por seguridad
        int cupoMaximo = switch (normalizarTexto(rolEnRaid)) {
            case "TANQUE" -> raid.getTanques();
            case "HEALER" -> raid.getHealers();
            case "DPS" -> raid.getDps();
            default -> Integer.MAX_VALUE;
        };

        if (inscritos >= cupoMaximo) {
            throw new RuntimeException("No hay cupos disponibles para el rol " + rolEnRaid + ".");
        }
    }

    // 5. Ejecución del INSERT (El Trigger de iLvl actuará aquí en la DB)
    // Gracias al ON CONFLICT en el repo, si ya existe, se actualizará el rol.
    // Auto-inscripcion: el jugador se anota solo, queda confirmado.
    inscripcion.setConfirmado(true);
    inscripcionRepository.inscribir(inscripcion);
}

    // Confirmar asistencia de un jugador (Req 2).
    // Solo el Guild Master puede confirmar.
    // Validaciones:
    // 1. El ejecutor pertenece al usuario del JWT
    // 2. El ejecutor tiene rol "Guild Master"
    public void confirmarAsistencia(Integer idInscripcion, Integer idEjecutor,
                                    String usernameAuth) {
        if (!personajeRepository.perteneceAUsuario(idEjecutor, usernameAuth)) {
            throw new RuntimeException("No tienes permiso sobre el personaje ejecutor.");
        }

        Integer idDueno = inscripcionRepository.obtenerPersonajeDeInscripcion(idInscripcion);
        if (!idEjecutor.equals(idDueno)) {
            throw new RuntimeException("Solo puedes confirmar tu propia asistencia.");
        }

        inscripcionRepository.confirmar(idInscripcion);
    }

private void validarClaseYRol(String claseRaw, String rolRaw) {
        // Normalización: "Paladín" -> "PALADIN", "Chaman" -> "CHAMAN"
        String clase = normalizarTexto(claseRaw);
        String rol = normalizarTexto(rolRaw);

        boolean esValido = switch (clase) {
            case "MAGO", "BRUJO" -> rol.equals("DPS") || rol.equals("HEALER");
            case "SACERDOTE" -> rol.equals("HEALER");
            case "PICARO", "CAZADOR" -> rol.equals("DPS");
            case "PALADIN" -> rol.equals("TANQUE"); // Según tu requerimiento específico
            case "CHAMAN", "DRUIDA" -> rol.equals("HEALER");
            case "GUERRERO" -> rol.equals("TANQUE") || rol.equals("DPS");
            default -> true; // Clases no listadas o desconocidas
        };

        if (!esValido) {
            throw new RuntimeException("Error: La clase " + claseRaw + " no está autorizada para el rol " + rolRaw);
        }
    }

    // Método de utilidad para ignorar mayúsculas y acentos
    private String normalizarTexto(String texto) {
        if (texto == null) return "";
        return texto.toUpperCase()
                .replace("Á", "A")
                .replace("É", "E")
                .replace("Í", "I")
                .replace("Ó", "O")
                .replace("Ú", "U")
                .trim();
    }
}