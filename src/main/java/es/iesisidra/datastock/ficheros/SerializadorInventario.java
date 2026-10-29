package es.iesisidra.datastock.ficheros;

import es.iesisidra.datastock.modelo.Producto;
import es.iesisidra.datastock.servicio.Inventario;
import es.iesisidra.datastock.validacion.ValidadorProducto;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RF-FIC-06 · Guardar y recuperar el inventario mediante serialización Java.
 * <p>Solo contiene la lógica de ficheros: no escribe por consola, no pide
 * confirmaciones y no modifica el inventario. Quien la llame decide cuándo
 * sustituir la colección en memoria.</p>
 * <p>Seguridad: deserializar un fichero desconocido es peligroso, así que al
 * leer se aplica un filtro que solo admite las clases que DataStock escribe
 * ({@code ArrayList}, {@code Producto} y {@code BigDecimal}). Cualquier otra
 * clase hace fallar la lectura.</p>
 */
public class SerializadorInventario {

    private static final String EXTENSION = ".dat";

    /** Lista blanca de clases permitidas al deserializar; "!*" rechaza el resto. */
    private static final String FILTRO =
            "java.util.ArrayList;java.lang.Object;"   // Object: ArrayList comprueba internamente un Object[]
            + "es.iesisidra.datastock.modelo.Producto;"
            + "java.math.BigDecimal;java.math.BigInteger;java.lang.Number;"
            + "maxdepth=5;maxrefs=1000000;!*";

    /**
     * Convierte el texto escrito por el usuario en la ruta de destino.
     * Si no termina en .dat, se añade la extensión.
     *
     * @throws SerializacionException si la ruta está vacía, es inválida, es un
     *                                directorio o su carpeta no existe
     */
    public Path resolverDestino(String textoRuta) throws SerializacionException {
        if (textoRuta == null || textoRuta.isBlank()) {
            throw new SerializacionException("La ruta está vacía.");
        }
        String texto = textoRuta.trim();
        if (!texto.toLowerCase().endsWith(EXTENSION)) {
            texto = texto + EXTENSION;
        }
        Path destino;
        try {
            destino = Path.of(texto).toAbsolutePath().normalize();
        } catch (InvalidPathException ex) {
            throw new SerializacionException("La ruta no es válida: " + ex.getReason());
        }
        if (Files.isDirectory(destino)) {
            throw new SerializacionException("La ruta es un directorio, no un fichero: " + destino);
        }
        Path carpeta = destino.getParent();
        if (carpeta == null || !Files.isDirectory(carpeta)) {
            throw new SerializacionException("El directorio de destino no existe: " + carpeta);
        }
        return destino;
    }

    /**
     * Serializa la colección completa en el fichero. Escribe primero en un
     * fichero temporal de la misma carpeta y lo mueve al final, de modo que si
     * algo falla a mitad de escritura no se pierde el fichero anterior.
     *
     * @param productos productos a guardar (se guarda una copia de la lista)
     * @param destino   ruta ya resuelta con {@link #resolverDestino(String)}
     * @throws SerializacionException si no hay productos o falla la escritura
     */
    public void guardar(List<Producto> productos, Path destino) throws SerializacionException {
        if (productos == null || productos.isEmpty()) {
            throw new SerializacionException("No hay productos que guardar.");
        }
        Path temporal = null;
        try {
            temporal = Files.createTempFile(destino.getParent(), "datastock", ".tmp");
            try (ObjectOutputStream salida = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(temporal)))) {
                salida.writeObject(new ArrayList<>(productos));
            }
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            borrarSilencioso(temporal);
            throw new SerializacionException("No se ha podido escribir el fichero: " + ex.getMessage(), ex);
        }
    }

    /**
     * Lee y valida un fichero .dat generado por DataStock. No modifica nada:
     * devuelve una lista nueva que el llamador usará para sustituir el inventario.
     *
     * @throws SerializacionException si el fichero no existe, está dañado, es
     *                                incompatible, no es fiable o su contenido no es válido
     */
    public List<Producto> recuperar(String textoRuta) throws SerializacionException {
        Path ruta;
        try {
            ruta = ComprobadorFichero.comprobarLegible(textoRuta);
        } catch (ImportacionException ex) {
            throw new SerializacionException(ex.getMessage());
        }

        try (ObjectInputStream entrada = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(ruta)))) {
            entrada.setObjectInputFilter(ObjectInputFilter.Config.createFilter(FILTRO));
            Object objeto = entrada.readObject();
            return validar(objeto);

        } catch (InvalidClassException ex) {
            throw new SerializacionException("El fichero es incompatible o contiene clases no permitidas "
                    + "(no parece generado por DataStock): " + ex.getMessage(), ex);
        } catch (StreamCorruptedException | EOFException ex) {
            throw new SerializacionException("El fichero está dañado o no es un fichero .dat de DataStock.", ex);
        } catch (ClassNotFoundException ex) {
            throw new SerializacionException("El fichero contiene una clase desconocida: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new SerializacionException("No se ha podido leer el fichero: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new SerializacionException("El contenido del fichero no es correcto: " + ex, ex);
        }
    }

    /**
     * Comprueba que el objeto leído es una lista de productos válidos y sin
     * códigos repetidos. Aplica las mismas reglas que las importaciones.
     */
    private List<Producto> validar(Object objeto) throws SerializacionException {
        if (!(objeto instanceof List<?>)) {
            throw new SerializacionException("El fichero no contiene una colección de productos.");
        }
        List<?> lista = (List<?>) objeto;
        if (lista.isEmpty()) {
            throw new SerializacionException("El fichero no contiene ningún producto.");
        }

        List<Producto> validos = new ArrayList<>();
        Set<String> codigos = new HashSet<>();
        int posicion = 0;
        for (Object elemento : lista) {
            posicion++;
            if (!(elemento instanceof Producto)) {
                throw new SerializacionException("El elemento " + posicion + " del fichero no es un producto.");
            }
            Producto p = (Producto) elemento;
            String precio = p.getPrecio() == null ? null : p.getPrecio().toPlainString();
            ValidadorProducto.Resultado resultado = ValidadorProducto.validar(
                    p.getCodigo(), p.getNombre(), precio, String.valueOf(p.getStock()),
                    String.valueOf(p.getCategoriaId()), String.valueOf(p.getProveedorId()));
            if (!resultado.esValido()) {
                throw new SerializacionException("Producto " + posicion + " inválido en el fichero: "
                        + String.join("; ", resultado.errores()));
            }
            Producto valido = resultado.producto();
            if (!codigos.add(Inventario.normalizarCodigo(valido.getCodigo()))) {
                throw new SerializacionException("Código repetido en el fichero: " + valido.getCodigo());
            }
            validos.add(valido);
        }
        return validos;
    }

    private void borrarSilencioso(Path temporal) {
        if (temporal == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporal);
        } catch (IOException ignorada) {
            // Solo es un temporal: si no se puede borrar, el error importante ya se informa.
        }
    }
}
