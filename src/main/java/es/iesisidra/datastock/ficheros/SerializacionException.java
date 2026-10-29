package es.iesisidra.datastock.ficheros;

/**
 * Error al guardar o recuperar el inventario serializado: ruta incorrecta,
 * fichero dañado, incompatible o no fiable. Cuando se lanza al recuperar, el
 * inventario en memoria NO se ha modificado. El mensaje es apto para el usuario.
 */
public class SerializacionException extends Exception {
    private static final long serialVersionUID = 1L;

    public SerializacionException(String mensaje) { super(mensaje); }

    public SerializacionException(String mensaje, Throwable causa) { super(mensaje, causa); }
}
