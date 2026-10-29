package es.iesisidra.datastock.complementarias;

import es.iesisidra.datastock.ficheros.ImportacionException;
import es.iesisidra.datastock.ficheros.ImportadorJson;
import es.iesisidra.datastock.ficheros.ImportadorXml;
import es.iesisidra.datastock.ficheros.ResultadoImportacion;
import es.iesisidra.datastock.ficheros.SerializacionException;
import es.iesisidra.datastock.ficheros.SerializadorInventario;
import es.iesisidra.datastock.modelo.Producto;
import es.iesisidra.datastock.servicio.Inventario;
import es.iesisidra.datastock.util.ColoresConsola;
import es.iesisidra.datastock.util.LectorConsola;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * RF-FIC-08 · Acceso aleatorio a un fichero binario de stock (RandomAccessFile).
 * <p>Actividad obligatoria e independiente del menú principal. Como se ejecuta
 * en su propia JVM, no comparte la memoria de DataStock: para generar
 * {@code stock.bin} carga primero una copia de los productos desde un .dat
 * (RF-FIC-06) o desde un catálogo JSON/XML. Una vez generado, el fichero es
 * independiente de cualquier inventario.</p>
 * <p>La lógica de ficheros está en {@link FicheroStock}.</p>
 */
public final class AccesoAleatorioActividad {

    private AccesoAleatorioActividad() { }

    public static void main(String[] args) {
        System.out.println("\n===== ACCESO ALEATORIO =====");
        Path ruta = pedirRuta();
        if (ruta == null) {
            return;
        }
        FicheroStock fichero = new FicheroStock(ruta);

        int opcion;
        do {
            System.out.println("\n1. Generar fichero | 2. Consultar posición | 3. Modificar stock"
                    + " | 4. Mostrar todos | 0. Salir");
            opcion = LectorConsola.leerOpcion("Opción: ", 0, 4);
            try {
                switch (opcion) {
                    case 1 -> generar(fichero, ruta);
                    case 2 -> consultar(fichero);
                    case 3 -> modificar(fichero);
                    case 4 -> mostrarTodos(fichero);
                    case 0 -> System.out.println("Fin de la actividad.");
                    default -> throw new IllegalStateException("Opción inesperada");
                }
            } catch (StockBinException ex) {
                error(ex.getMessage());
            }
        } while (opcion != 0);
    }

    /** Pide la ruta de stock.bin; devuelve null si no es utilizable. */
    private static Path pedirRuta() {
        String texto = LectorConsola.leerTexto("Fichero: ");
        if (texto.isBlank()) {
            error("La ruta está vacía.");
            return null;
        }
        try {
            Path ruta = Path.of(texto.trim());
            if (Files.isDirectory(ruta)) {
                error("La ruta es un directorio, no un fichero: " + ruta);
                return null;
            }
            return ruta;
        } catch (InvalidPathException ex) {
            error("La ruta no es válida: " + ex.getReason());
            return null;
        }
    }

    /** Opción 1: genera stock.bin a partir de una copia de productos. */
    private static void generar(FicheroStock fichero, Path ruta) throws StockBinException {
        List<Producto> productos = cargarProductos();
        if (productos == null) {
            return; // el error ya se ha mostrado o el usuario ha cancelado
        }
        if (productos.isEmpty()) {
            aviso("No hay productos disponibles: no se genera el fichero.");
            return;
        }
        if (Files.exists(ruta) && !LectorConsola.confirmar("El fichero ya existe. ¿Sobrescribir?")) {
            aviso("Operación cancelada: el fichero original no se ha modificado.");
            return;
        }
        int total = fichero.generar(productos);
        System.out.println("Fichero generado correctamente.");
        System.out.println("Registros: " + total + " | Tamaño: " + fichero.tamanoBytes() + " bytes");
    }

    /**
     * Obtiene la copia de productos desde un .dat, un JSON o un XML.
     *
     * @return la lista (puede estar vacía), o null si hay error o se cancela
     */
    private static List<Producto> cargarProductos() {
        System.out.println("Origen de los productos:");
        System.out.println("1. Fichero .dat (inventario guardado con RF-FIC-06)");
        System.out.println("2. Catálogo JSON");
        System.out.println("3. Catálogo XML");
        System.out.println("0. Cancelar");
        int origen = LectorConsola.leerOpcion("Origen: ", 0, 3);
        if (origen == 0) {
            return null;
        }
        String ruta = LectorConsola.leerTexto("Ruta del origen: ");
        try {
            if (origen == 1) {
                return new SerializadorInventario().recuperar(ruta);
            }
            Inventario temporal = new Inventario();
            ResultadoImportacion resultado;
            if (origen == 2) {
                resultado = new ImportadorJson(temporal).importar(ruta);
            } else {
                resultado = new ImportadorXml(temporal).importar(ruta);
            }
            resultado.imprimir();
            return temporal.getProductos();
        } catch (SerializacionException | ImportacionException ex) {
            error(ex.getMessage());
            return null;
        }
    }

    /** Opción 2: consulta directa de un registro con seek(). */
    private static void consultar(FicheroStock fichero) throws StockBinException {
        int total = fichero.numeroRegistros();
        System.out.println("Registros: " + total + " | Tamaño: " + fichero.tamanoBytes() + " bytes");
        if (total == 0) {
            aviso("El fichero no contiene registros.");
            return;
        }
        Integer posicion = pedirEntero("Consultar posición (0-" + (total - 1) + "): ");
        if (posicion == null) {
            return;
        }
        System.out.println(formatear(fichero.leer(posicion)));
    }

    /** Opción 3: modifica solo el campo stock de un registro. */
    private static void modificar(FicheroStock fichero) throws StockBinException {
        int total = fichero.numeroRegistros();
        if (total == 0) {
            aviso("El fichero no contiene registros.");
            return;
        }
        Integer posicion = pedirEntero("Posición a modificar (0-" + (total - 1) + "): ");
        if (posicion == null) {
            return;
        }
        System.out.println(formatear(fichero.leer(posicion)));
        Integer nuevo = pedirEntero("Nuevo stock: ");
        if (nuevo == null) {
            return;
        }
        int anterior = fichero.modificarStock(posicion, nuevo);
        System.out.println("Actualización correcta: stock " + anterior + " → " + nuevo);
    }

    /** Opción 4: lectura secuencial de todos los registros. */
    private static void mostrarTodos(FicheroStock fichero) throws StockBinException {
        List<FicheroStock.Registro> registros = fichero.leerTodos();
        if (registros.isEmpty()) {
            aviso("El fichero no contiene registros.");
            return;
        }
        int posicion = 0;
        for (FicheroStock.Registro registro : registros) {
            System.out.println(posicion + ": " + formatear(registro));
            posicion++;
        }
        System.out.println("Registros: " + registros.size());
    }

    /** Pide un entero; si no lo es, avisa y devuelve null. */
    private static Integer pedirEntero(String mensaje) {
        String texto = LectorConsola.leerTexto(mensaje).trim();
        try {
            return Integer.valueOf(texto);
        } catch (NumberFormatException ex) {
            aviso("Debes introducir un número entero.");
            return null;
        }
    }

    private static String formatear(FicheroStock.Registro registro) {
        return registro.codigo() + " | " + String.format(Locale.ROOT, "%.2f", registro.precio())
                + " € | stock " + registro.stock();
    }

    private static void aviso(String mensaje) {
        System.out.println(ColoresConsola.pintar(mensaje, ColoresConsola.AMARILLO));
    }

    private static void error(String mensaje) {
        System.out.println(ColoresConsola.pintar("ERROR: " + mensaje, ColoresConsola.ROJO));
    }
}