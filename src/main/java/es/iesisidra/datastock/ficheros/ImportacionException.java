package es.iesisidra.datastock.ficheros;

/**
 * Error que impide realizar una importación completa: fichero inaccesible,
 * documento mal formado o estructura general incorrecta. Cuando se lanza,
 * el inventario NO se ha modificado. El mensaje es apto para mostrarse al usuario.
 */
public class ImportacionException extends Exception {
    private static final long serialVersionUID = 1L;

    public ImportacionException(String mensaje) { super(mensaje); }

    public ImportacionException(String mensaje, Throwable causa) { super(mensaje, causa); }
}