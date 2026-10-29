package es.iesisidra.datastock.ui;

import es.iesisidra.datastock.modelo.Producto;
import es.iesisidra.datastock.servicio.ConsultaProductos;
import es.iesisidra.datastock.servicio.Inventario;
import es.iesisidra.datastock.util.ColoresConsola;
import es.iesisidra.datastock.util.LectorConsola;

import java.util.List;

/**
 * RF-FIC-04 · Presentación de las consultas de productos.
 * <p>Solicita los datos al usuario, delega la búsqueda en {@link ConsultaProductos}
 * y muestra el resultado. No contiene lógica de búsqueda.</p>
 */
public class PantallaConsultas {

    private static final int ANCHO_NOMBRE = 30;

    private final ConsultaProductos consultas;

    public PantallaConsultas(Inventario inventario) {
        this.consultas = new ConsultaProductos(inventario);
    }

    /** Opción 4.1: lista todos los productos ordenados por código. */
    public void listar() {
        System.out.println("\n===== LISTADO DE PRODUCTOS =====");
        List<Producto> productos = consultas.listarOrdenadosPorCodigo();
        if (productos.isEmpty()) {
            avisarInventarioVacio();
            return;
        }
        mostrarTabla(productos);
        System.out.println("Total: " + productos.size() + " producto(s).");
    }

    /** Opción 4.2: busca un producto por código exacto. */
    public void buscarPorCodigo() {
        System.out.println("\n===== BUSCAR POR CÓDIGO =====");
        if (consultas.estaVacio()) {
            avisarInventarioVacio();
            return;
        }
        String codigo = LectorConsola.leerTexto("Introduce el código a buscar: ");
        if (codigo.isBlank()) {
            aviso("Debes introducir un código.");
            return;
        }
        Producto producto = consultas.buscarPorCodigo(codigo);
        if (producto == null) {
            aviso("No existe ningún producto con el código «" + codigo.trim() + "».");
        } else {
            mostrarFicha(producto);
        }
    }

    /** Opción 4.3: busca productos cuyo nombre contenga un texto. */
    public void buscarPorNombre() {
        System.out.println("\n===== BUSCAR POR NOMBRE =====");
        if (consultas.estaVacio()) {
            avisarInventarioVacio();
            return;
        }
        String texto = LectorConsola.leerTexto("Texto a buscar en el nombre: ");
        if (texto.isBlank()) {
            aviso("Debes introducir un texto de búsqueda.");
            return;
        }
        List<Producto> encontrados = consultas.buscarPorNombre(texto);
        if (encontrados.isEmpty()) {
            aviso("No hay productos cuyo nombre contenga «" + texto.trim() + "».");
            return;
        }
        System.out.println("Coincidencias: " + encontrados.size());
        mostrarTabla(encontrados);
    }

    /** Muestra todos los datos de un producto. */
    private void mostrarFicha(Producto producto) {
        System.out.println("Código: " + producto.getCodigo());
        System.out.println("Nombre: " + producto.getNombre());
        System.out.println("Precio: " + precio(producto) + " | Stock: " + producto.getStock());
        System.out.println("Categoría: " + producto.getCategoriaId()
                + " | Proveedor: " + producto.getProveedorId());
    }

    /** Muestra varios productos en forma de tabla con los datos principales. */
    private void mostrarTabla(List<Producto> productos) {
        System.out.printf("%-12s %-" + ANCHO_NOMBRE + "s %12s %7s %10s %10s%n",
                "CÓDIGO", "NOMBRE", "PRECIO", "STOCK", "CATEGORÍA", "PROVEEDOR");
        for (Producto producto : productos) {
            System.out.printf("%-12s %-" + ANCHO_NOMBRE + "s %12s %7d %10d %10d%n",
                    producto.getCodigo(), abreviar(producto.getNombre()), precio(producto),
                    producto.getStock(), producto.getCategoriaId(), producto.getProveedorId());
        }
    }

    /** Precio tal y como está almacenado, seguido del símbolo del euro. */
    private String precio(Producto producto) {
        if (producto.getPrecio() == null) {
            return "—";
        }
        return producto.getPrecio().toPlainString() + " €";
    }

    /** Recorta nombres largos para que la tabla no se desalinee. */
    private String abreviar(String nombre) {
        if (nombre == null) {
            return "";
        }
        if (nombre.length() <= ANCHO_NOMBRE) {
            return nombre;
        }
        return nombre.substring(0, ANCHO_NOMBRE - 3) + "...";
    }

    private void avisarInventarioVacio() {
        aviso("El inventario está vacío. Importa productos desde un fichero JSON o XML.");
    }

    private void aviso(String mensaje) {
        System.out.println(ColoresConsola.pintar(mensaje, ColoresConsola.AMARILLO));
    }
}
