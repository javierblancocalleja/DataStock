package es.iesisidra.datastock.complementarias;

/**
 * Error al generar, leer o modificar stock.bin: fichero inexistente, tamaño
 * inválido, posición fuera de rango, stock negativo o fallo de lectura/escritura.
 * El mensaje es apto para mostrarse al usuario.
 */
public class StockBinException extends Exception {
    private static final long serialVersionUID = 1L;

    public StockBinException(String mensaje) { super(mensaje); }

    public StockBinException(String mensaje, Throwable causa) { super(mensaje, causa); }
}
