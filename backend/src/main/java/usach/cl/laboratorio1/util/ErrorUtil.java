package usach.cl.laboratorio1.util;

/**
 * Extrae el mensaje util de una excepcion. Spring envuelve los errores de
 * PostgreSQL (por ejemplo los RAISE EXCEPTION de nuestros triggers y
 * procedimientos) dentro de excepciones como PreparedStatementCallback, cuyo
 * texto no le sirve al usuario. Aqui bajamos hasta la causa mas profunda y,
 * si viene de plpgsql, nos quedamos solo con la primera linea del mensaje.
 */
public final class ErrorUtil {

    private ErrorUtil() { }

    public static String msg(Throwable e) {
        Throwable actual = e;
        while (actual.getCause() != null && actual.getCause() != actual) {
            actual = actual.getCause();
        }
        String texto = actual.getMessage();
        if (texto == null || texto.isBlank()) {
            texto = (e.getMessage() == null ? "Error inesperado." : e.getMessage());
        }
        // Quedarnos con la primera linea (PostgreSQL agrega Detail/Where/Context)
        int corte = texto.indexOf('\n');
        if (corte > 0) {
            texto = texto.substring(0, corte);
        }
        texto = texto.replace("ERROR:", "").trim();
        return texto.isEmpty() ? "Error inesperado." : texto;
    }
}
