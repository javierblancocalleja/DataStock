package es.iesisidra.datastock.servicio;

import es.iesisidra.datastock.modelo.Producto;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Estado común del inventario durante la ejecución de UT1.
 * <p>Contiene la colección compartida y las reglas básicas de alta: el código
 * es único sin distinguir mayúsculas ni espacios extremos. La importación, la
 * búsqueda, la exportación y la persistencia se implementan en otras clases.</p>
 */
public class Inventario {
    private final List<Producto> productos = new ArrayList<>();

    /**
     * Obtiene la colección que comparten las opciones del menú.
     * <p>ATENCIÓN: la lista es modificable. Para añadir productos se recomienda
     * usar {@link #agregar(Producto)}, que respeta la regla de códigos únicos.</p>
     */
    public List<Producto> getProductos() { return productos; }

    /**
     * Normaliza un código para compararlo: elimina espacios extremos y
     * lo pasa a mayúsculas, de modo que "tec-001 " y "TEC-001" son el mismo.
     */
    public static String normalizarCodigo(String codigo) {
        return codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
    }

    /** @return true si ya hay un producto con ese código (sin distinguir mayúsculas). */
    public boolean contieneCodigo(String codigo) {
        String clave = normalizarCodigo(codigo);
        
        for (Producto p : productos) {
            if (normalizarCodigo(p.getCodigo()).equals(clave)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Añade un producto solo si su código no existe todavía.
     * No sustituye ni modifica los productos ya almacenados.
     *
     * @return true si se ha añadido; false si era nulo o su código está repetido
     */
    public boolean agregar(Producto producto) {
        if (producto == null || contieneCodigo(producto.getCodigo())) {
            return false;
        }
        return productos.add(producto);
    }
}