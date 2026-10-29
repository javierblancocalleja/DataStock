package es.iesisidra.datastock.complementarias;

import es.iesisidra.datastock.modelo.Producto;
import es.iesisidra.datastock.servicio.Inventario;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * RF-FIC-08 · Fichero binario de registros de longitud fija (stock.bin).
 * <p>Cada registro ocupa 36 bytes:</p>
 * <pre>
 *   código  12 caracteres Java (2 bytes cada uno, relleno con espacios)  24 bytes  (desplazamiento 0)
 *   precio  double                                                         8 bytes  (desplazamiento 24)
 *   stock   int                                                            4 bytes  (desplazamiento 32)
 * </pre>
 * <p>El inicio del registro {@code n} es {@code n × 36}; su campo stock está en
 * {@code n × 36 + 32}. La consulta directa y la modificación usan {@code seek()}.</p>
 * <p>El fichero es una copia independiente: modificarlo NO cambia los objetos
 * {@link Producto} en memoria, ni al revés.</p>
 */
public class FicheroStock {

    /** Bytes de un registro completo. */
    public static final int TAM_REGISTRO = 36;
    /** Caracteres del código (24 bytes). */
    public static final int LONG_CODIGO = 12;
    private static final int DESPLAZAMIENTO_STOCK = 32;

    /** Registro leído del fichero. */
    public record Registro(String codigo, double precio, int stock) { }

    private final Path ruta;

    public FicheroStock(Path ruta) {
        this.ruta = ruta;
    }

    /**
     * Genera el fichero con una copia de los productos, ordenados por código.
     * Si ya existía, se trunca antes de escribir (la confirmación la pide quien llama).
     *
     * @return número de registros escritos
     * @throws StockBinException si no hay productos, el directorio no existe o falla la escritura
     */
    public int generar(List<Producto> productos) throws StockBinException {
        if (productos == null || productos.isEmpty()) {
            throw new StockBinException("No hay productos: no se genera el fichero.");
        }
        Path carpeta = ruta.toAbsolutePath().getParent();
        if (carpeta == null || !Files.isDirectory(carpeta)) {
            throw new StockBinException("El directorio de destino no existe: " + carpeta);
        }

        List<Producto> copia = new ArrayList<>(productos);
        copia.sort(new ComparadorPorCodigo());

        try (RandomAccessFile fichero = new RandomAccessFile(ruta.toFile(), "rw")) {
            fichero.setLength(0); // truncar antes de escribir
            for (Producto producto : copia) {
                escribirCodigo(fichero, producto.getCodigo());
                fichero.writeDouble(producto.getPrecio().doubleValue());
                fichero.writeInt(producto.getStock());
            }
        } catch (IOException ex) {
            throw new StockBinException("No se ha podido escribir el fichero: " + ex.getMessage(), ex);
        }
        return copia.size();
    }

    /** @return tamaño del fichero en bytes. */
    public long tamanoBytes() throws StockBinException {
        if (!Files.isRegularFile(ruta)) {
            throw new StockBinException("El fichero no existe: " + ruta);
        }
        try {
            return Files.size(ruta);
        } catch (IOException ex) {
            throw new StockBinException("No se ha podido leer el tamaño del fichero: " + ex.getMessage(), ex);
        }
    }

    /**
     * @return número de registros (tamaño / 36)
     * @throws StockBinException si el fichero no existe o su tamaño no es múltiplo de 36
     */
    public int numeroRegistros() throws StockBinException {
        long tamano = tamanoBytes();
        if (tamano % TAM_REGISTRO != 0) {
            throw new StockBinException("Fichero inválido: su tamaño (" + tamano
                    + " bytes) no es múltiplo de " + TAM_REGISTRO + ".");
        }
        return (int) (tamano / TAM_REGISTRO);
    }

    /** Lee directamente el registro de la posición indicada (empezando en 0) con seek(). */
    public Registro leer(int posicion) throws StockBinException {
        comprobarPosicion(posicion, numeroRegistros());
        try (RandomAccessFile fichero = new RandomAccessFile(ruta.toFile(), "r")) {
            fichero.seek((long) posicion * TAM_REGISTRO);
            return leerRegistro(fichero);
        } catch (IOException ex) {
            throw new StockBinException("No se ha podido leer el registro: " + ex.getMessage(), ex);
        }
    }

    /**
     * Cambia solo el campo stock del registro, sin reescribir el resto:
     * seek(posición × 36 + 32) y writeInt.
     *
     * @return el stock anterior
     */
    public int modificarStock(int posicion, int nuevoStock) throws StockBinException {
        comprobarPosicion(posicion, numeroRegistros());
        if (nuevoStock < 0) {
            throw new StockBinException("El stock no puede ser negativo.");
        }
        long desplazamiento = (long) posicion * TAM_REGISTRO + DESPLAZAMIENTO_STOCK;
        try (RandomAccessFile fichero = new RandomAccessFile(ruta.toFile(), "rw")) {
            fichero.seek(desplazamiento);
            int anterior = fichero.readInt();
            fichero.seek(desplazamiento);
            fichero.writeInt(nuevoStock);
            return anterior;
        } catch (IOException ex) {
            throw new StockBinException("No se ha podido modificar el stock: " + ex.getMessage(), ex);
        }
    }

    /** Lectura secuencial de todos los registros, de principio a fin. */
    public List<Registro> leerTodos() throws StockBinException {
        int total = numeroRegistros();
        List<Registro> registros = new ArrayList<>();
        try (RandomAccessFile fichero = new RandomAccessFile(ruta.toFile(), "r")) {
            for (int i = 0; i < total; i++) {
                registros.add(leerRegistro(fichero));
            }
        } catch (IOException ex) {
            throw new StockBinException("No se ha podido leer el fichero: " + ex.getMessage(), ex);
        }
        return registros;
    }

    private void comprobarPosicion(int posicion, int total) throws StockBinException {
        if (total == 0) {
            throw new StockBinException("El fichero no contiene registros.");
        }
        if (posicion < 0 || posicion >= total) {
            throw new StockBinException("Posición fuera de rango: debe estar entre 0 y " + (total - 1) + ".");
        }
    }

    /** Escribe el código con exactamente 12 caracteres, rellenando con espacios. */
    private void escribirCodigo(RandomAccessFile fichero, String codigo) throws IOException, StockBinException {
        String texto = codigo == null ? "" : codigo.trim();
        if (texto.length() > LONG_CODIGO) {
            throw new StockBinException("El código «" + texto + "» supera " + LONG_CODIGO + " caracteres.");
        }
        for (int i = 0; i < LONG_CODIGO; i++) {
            fichero.writeChar(i < texto.length() ? texto.charAt(i) : ' ');
        }
    }

    /** Lee un registro desde la posición actual del puntero del fichero. */
    private Registro leerRegistro(RandomAccessFile fichero) throws IOException {
        char[] letras = new char[LONG_CODIGO];
        for (int i = 0; i < LONG_CODIGO; i++) {
            letras[i] = fichero.readChar();
        }
        String codigo = new String(letras).stripTrailing();
        double precio = fichero.readDouble();
        int stock = fichero.readInt();
        return new Registro(codigo, precio, stock);
    }

    /** Ordena productos por código normalizado. */
    private static final class ComparadorPorCodigo implements Comparator<Producto> {
        @Override
        public int compare(Producto a, Producto b) {
            return Inventario.normalizarCodigo(a.getCodigo())
                    .compareTo(Inventario.normalizarCodigo(b.getCodigo()));
        }
    }
}
