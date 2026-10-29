package es.iesisidra.datastock.ficheros;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import es.iesisidra.datastock.modelo.Producto;
import es.iesisidra.datastock.servicio.ConsultaProductos;
import es.iesisidra.datastock.servicio.Inventario;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/**
 * RF-FIC-05 · Exportación del inventario a JSON (Gson) y a XML (DOM).
 * <p>Ambos formatos contienen todos los productos de la colección, ordenados
 * por código, con los mismos seis campos que se leen en las importaciones y
 * codificados en UTF-8. La conversión entre formatos usa la colección Java como
 * modelo intermedio: JSON → Producto → XML y XML → Producto → JSON.</p>
 * <p>El contenido completo se genera <b>en memoria antes de abrir el fichero
 * de destino</b>: si la conversión falla, el fichero que hubiera no se toca.</p>
 */
public class ExportadorCatalogo {

    private static final String RAIZ_XML = "catalogo";
    private static final String ELEMENTO_XML = "producto";
    private static final String CLAVE_JSON = "productos";

    private final ConsultaProductos consultas;

    public ExportadorCatalogo(Inventario inventario) {
        this.consultas = new ConsultaProductos(inventario);
    }

    /** @return true si hay al menos un producto que exportar. */
    public boolean hayProductos() {
        return !consultas.estaVacio();
    }

    /**
     * Comprueba que la ruta de destino puede usarse para escribir un fichero:
     * no está vacía, su directorio existe, no es un directorio y hay permisos.
     * No crea nada.
     *
     * @return la ruta ya validada
     * @throws ExportacionException si el destino no es utilizable
     */
    public Path prepararDestino(String textoRuta) throws ExportacionException {
        if (textoRuta == null || textoRuta.isBlank()) {
            throw new ExportacionException("La ruta de destino está vacía.");
        }
        Path destino;
        try {
            destino = Path.of(textoRuta.trim());
        } catch (InvalidPathException ex) {
            throw new ExportacionException("La ruta de destino no es válida: " + ex.getReason());
        }

        Path absoluta = destino.toAbsolutePath();
        Path directorio = absoluta.getParent();
        if (directorio == null || !Files.isDirectory(directorio)) {
            throw new ExportacionException("El directorio de destino no existe: " + directorio);
        }
        if (Files.isDirectory(absoluta)) {
            throw new ExportacionException("La ruta de destino es un directorio; indica un nombre de fichero: "
                    + destino);
        }
        if (Files.exists(absoluta)) {
            if (!Files.isWritable(absoluta)) {
                throw new ExportacionException("No hay permisos de escritura sobre el fichero: " + destino);
            }
        } else if (!Files.isWritable(directorio)) {
            throw new ExportacionException("No hay permisos de escritura en el directorio de destino: "
                    + directorio);
        }
        return destino;
    }

    /** @return true si el fichero de destino ya existe (habrá que pedir confirmación). */
    public boolean existe(Path destino) {
        return Files.exists(destino);
    }

    /**
     * Exporta todos los productos a un JSON con objeto raíz {@code {"productos": [...]}}.
     *
     * @return número de productos exportados
     * @throws ExportacionException si no hay productos o falla la escritura
     */
    public int exportarJson(Path destino) throws ExportacionException {
        List<Producto> productos = productosAExportar();
        String contenido = generarJson(productos);
        escribir(destino, contenido);
        return productos.size();
    }

    /**
     * Exporta todos los productos a un XML {@code catalogo/producto/campos}, con indentación.
     *
     * @return número de productos exportados
     * @throws ExportacionException si no hay productos o falla la generación o la escritura
     */
    public int exportarXml(Path destino) throws ExportacionException {
        List<Producto> productos = productosAExportar();
        String contenido;
        try {
            contenido = generarXml(productos);
        } catch (ParserConfigurationException | TransformerException ex) {
            throw new ExportacionException("No se ha podido generar el documento XML: " + ex.getMessage(), ex);
        }
        escribir(destino, contenido);
        return productos.size();
    }

    /** Copia de los productos ordenada por código; falla si el inventario está vacío. */
    private List<Producto> productosAExportar() throws ExportacionException {
        if (consultas.estaVacio()) {
            throw new ExportacionException("El inventario está vacío: no hay productos que exportar.");
        }
        return consultas.listarOrdenadosPorCodigo();
    }

    /** Construye el texto JSON con Gson. Los datos se escriben tal cual, sin escapar &lt; ni &amp;. */
    private String generarJson(List<Producto> productos) {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        JsonObject raiz = new JsonObject();
        raiz.add(CLAVE_JSON, gson.toJsonTree(productos));
        return gson.toJson(raiz) + "\n";
    }

    /** Construye el documento DOM y lo convierte a texto XML indentado. */
    private String generarXml(List<Producto> productos)
            throws ParserConfigurationException, TransformerException {
        Document documento = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element raiz = documento.createElement(RAIZ_XML);
        documento.appendChild(raiz);

        for (Producto producto : productos) {
            Element elemento = documento.createElement(ELEMENTO_XML);
            anadirCampo(documento, elemento, "codigo", producto.getCodigo());
            anadirCampo(documento, elemento, "nombre", producto.getNombre());
            anadirCampo(documento, elemento, "precio",
                    producto.getPrecio() == null ? "" : producto.getPrecio().toPlainString());
            anadirCampo(documento, elemento, "stock", String.valueOf(producto.getStock()));
            anadirCampo(documento, elemento, "categoriaId", String.valueOf(producto.getCategoriaId()));
            anadirCampo(documento, elemento, "proveedorId", String.valueOf(producto.getProveedorId()));
            raiz.appendChild(elemento);
        }

        TransformerFactory fabrica = TransformerFactory.newInstance();
        fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Transformer transformador = fabrica.newTransformer();
        transformador.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformador.setOutputProperty(OutputKeys.INDENT, "yes");
        transformador.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        // La declaración se escribe a mano para controlar el salto de línea posterior.
        transformador.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        StringWriter salida = new StringWriter();
        transformador.transform(new DOMSource(documento), new StreamResult(salida));
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + salida.toString().trim() + "\n";
    }

    /** Añade {@code <nombre>texto</nombre>} como hijo del elemento indicado. */
    private void anadirCampo(Document documento, Element padre, String nombre, String valor) {
        Element campo = documento.createElement(nombre);
        campo.setTextContent(valor == null ? "" : valor);
        padre.appendChild(campo);
    }

    /** Escribe el texto en UTF-8, creando el fichero o sustituyendo el existente. */
    private void escribir(Path destino, String contenido) throws ExportacionException {
        try {
            Files.writeString(destino, contenido, StandardCharsets.UTF_8);
        } catch (AccessDeniedException | SecurityException ex) {
            throw new ExportacionException("No hay permisos para escribir en: " + destino, ex);
        } catch (IOException ex) {
            throw new ExportacionException("Error al escribir el fichero «" + destino + "»: " + ex.getMessage(), ex);
        }
    }
}
