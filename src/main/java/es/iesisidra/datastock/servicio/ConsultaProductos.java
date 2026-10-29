package es.iesisidra.datastock.servicio;

import es.iesisidra.datastock.modelo.Producto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * RF-FIC-04 · Consultas sobre los productos cargados en memoria.
 * <p>Esta clase solo contiene la lógica de búsqueda: no lee ficheros, no
 * escribe por consola y <b>nunca modifica</b> la colección del inventario
 * (las listas que devuelve son copias).</p>
 */
public class ConsultaProductos {

    private final Inventario inventario;

    public ConsultaProductos(Inventario inventario) {
        this.inventario = inventario;
    }

    /** @return true si no hay ningún producto cargado en memoria. */
    public boolean estaVacio() {
        return inventario.getProductos().isEmpty();
    }

    /** @return copia de todos los productos, ordenada por código (sin distinguir mayúsculas). */
    public List<Producto> listarOrdenadosPorCodigo() {
        List<Producto> copia = new ArrayList<>(inventario.getProductos());
        copia.sort(new ComparadorPorCodigo());
        return copia;
    }

    /**
     * Busca un producto por código exacto, ignorando mayúsculas y espacios extremos.
     *
     * @return el producto, o {@code null} si no existe o el código está vacío
     */
    public Producto buscarPorCodigo(String codigo) {
        String buscado = Inventario.normalizarCodigo(codigo);
        if (buscado.isEmpty()) {
            return null;
        }
        for (Producto producto : inventario.getProductos()) {
            if (Inventario.normalizarCodigo(producto.getCodigo()).equals(buscado)) {
                return producto;
            }
        }
        return null;
    }

    /**
     * Busca los productos cuyo nombre contiene el texto indicado, sin distinguir
     * mayúsculas de minúsculas.
     *
     * @return coincidencias ordenadas por código; lista vacía si no hay ninguna o el texto está vacío
     */
    public List<Producto> buscarPorNombre(String fragmento) {
        List<Producto> encontrados = new ArrayList<>();
        if (fragmento == null || fragmento.isBlank()) {
            return encontrados;
        }
        String buscado = fragmento.trim().toLowerCase(Locale.ROOT);
        for (Producto producto : inventario.getProductos()) {
            String nombre = producto.getNombre();
            if (nombre != null && nombre.toLowerCase(Locale.ROOT).contains(buscado)) {
                encontrados.add(producto);
            }
        }
        encontrados.sort(new ComparadorPorCodigo());
        return encontrados;
    }

    /** Ordena productos por código normalizado (mayúsculas, sin espacios extremos). */
    private static final class ComparadorPorCodigo implements Comparator<Producto> {
        @Override
        public int compare(Producto a, Producto b) {
            return Inventario.normalizarCodigo(a.getCodigo())
                    .compareTo(Inventario.normalizarCodigo(b.getCodigo()));
        }
    }
}
