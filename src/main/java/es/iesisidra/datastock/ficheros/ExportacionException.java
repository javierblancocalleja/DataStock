package es.iesisidra.datastock.ficheros;

/**
 * Error que impide completar una exportación: inventario vacío, destino no
 * válido, falta de permisos o fallo de escritura. El mensaje es apto para
 * mostrarse al usuario. Si se lanza, no se ha modificado ningún fichero.
 */
public class ExportacionException extends Exception {
    private static final long serialVersionUID = 1L;

    public ExportacionException(String mensaje) { super(mensaje); }

    public ExportacionException(String mensaje, Throwable causa) { super(mensaje, causa); }
}
