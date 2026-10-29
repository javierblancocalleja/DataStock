package es.iesisidra.datastock.complementarias;

import es.iesisidra.datastock.ficheros.ImportacionException;
import es.iesisidra.datastock.util.ColoresConsola;
import es.iesisidra.datastock.util.LectorConsola;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.util.List;

/**
 * RF-FIC-07 · Consultas XPath sobre un catálogo XML.
 * <p>Actividad obligatoria e independiente del menú principal: pide un fichero,
 * lo abre una sola vez y permite hacer varias consultas sobre el documento
 * abierto. La lógica XPath está en {@link ConsultorXPath}.</p>
 */
public final class XPathActividad {

    private XPathActividad() { }

    public static void main(String[] args) {
        System.out.println("\n===== CONSULTAS XPATH =====");
        String ruta = LectorConsola.leerTexto("Fichero: ");

        ConsultorXPath consultor;
        try {
            consultor = ConsultorXPath.abrir(ruta);
        } catch (ImportacionException ex) {
            error(ex.getMessage());
            return;
        }

        int opcion;
        do {
            System.out.println("\n1. Por código | 2. Por categoría | 3. Precio superior | 4. Contar | 0. Salir");
            opcion = LectorConsola.leerOpcion("Opción: ", 0, 4);
            try {
                switch (opcion) {
                    case 1 -> consultarPorCodigo(consultor);
                    case 2 -> consultarPorCategoria(consultor);
                    case 3 -> consultarPrecioSuperior(consultor);
                    case 4 -> System.out.println("Productos en el catálogo: " + consultor.contarProductos());
                    case 0 -> System.out.println("Fin de las consultas.");
                    default -> throw new IllegalStateException("Opción inesperada");
                }
            } catch (ImportacionException ex) {
                error(ex.getMessage());
            }
        } while (opcion != 0);
    }

    private static void consultarPorCodigo(ConsultorXPath consultor) throws ImportacionException {
        String codigo = LectorConsola.leerTexto("Código: ");
        if (codigo.isBlank()) {
            aviso("Debes introducir un código.");
            return;
        }
        mostrar(consultor.buscarPorCodigo(codigo));
    }

    private static void consultarPorCategoria(ConsultorXPath consultor) throws ImportacionException {
        String texto = LectorConsola.leerTexto("Categoría (entero positivo): ").trim();
        int categoria;
        try {
            categoria = Integer.parseInt(texto);
        } catch (NumberFormatException ex) {
            aviso("La categoría debe ser un número entero.");
            return;
        }
        if (categoria <= 0) {
            aviso("La categoría debe ser un entero positivo.");
            return;
        }
        mostrar(consultor.filtrarPorCategoria(categoria));
    }

    private static void consultarPrecioSuperior(ConsultorXPath consultor) throws ImportacionException {
        String texto = LectorConsola.leerTexto("Precio mínimo: ").trim().replace(',', '.');
        BigDecimal minimo;
        try {
            minimo = new BigDecimal(texto);
        } catch (NumberFormatException ex) {
            aviso("El precio no es un número válido.");
            return;
        }
        if (minimo.signum() < 0) {
            aviso("El precio mínimo no puede ser negativo.");
            return;
        }
        mostrar(consultor.filtrarPrecioSuperior(minimo));
    }

    /** Muestra una línea por producto y el total de coincidencias. */
    private static void mostrar(List<Element> productos) {
        if (productos.isEmpty()) {
            aviso("No hay coincidencias.");
            return;
        }
        for (Element producto : productos) {
            System.out.println(dato(producto, "codigo") + " " + dato(producto, "nombre")
                    + " " + dato(producto, "precio") + " €");
        }
        System.out.println("Productos encontrados: " + productos.size());
    }

    /** Texto de un campo, o «?» si el producto no lo tiene. */
    private static String dato(Element producto, String campo) {
        String valor = ConsultorXPath.textoDe(producto, campo);
        return valor == null ? "?" : valor;
    }

    private static void aviso(String mensaje) {
        System.out.println(ColoresConsola.pintar(mensaje, ColoresConsola.AMARILLO));
    }

    private static void error(String mensaje) {
        System.out.println(ColoresConsola.pintar("ERROR: " + mensaje, ColoresConsola.ROJO));
    }
}