package es.iesisidra.datastock.ficheros;

import es.iesisidra.datastock.ficheros.IncorporadorProductos.RegistroProducto;
import es.iesisidra.datastock.servicio.Inventario;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * RF-FIC-03 · Importación de un catálogo XML mediante DOM.
 * <p>Pasos: (1) comprobar el fichero, (2) analizarlo con DOM usando una
 * configuración segura (sin DTD ni entidades externas, para evitar XXE),
 * (3) comprobar que la raíz es {@code catalogo}, (4) leer los seis campos de
 * cada elemento {@code producto} y (5) delegar la validación y el alta en
 * {@link IncorporadorProductos}, la misma clase que usa la importación JSON.</p>
 * <p>Si el documento está mal formado, contiene un DTD o su raíz no es
 * {@code catalogo}, se lanza {@link ImportacionException} sin modificar el inventario.</p>
 */
public class ImportadorXml {

    private static final String RAIZ = "catalogo";
    private static final String ELEMENTO_PRODUCTO = "producto";
    private static final String FEATURE_SIN_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";

    private final IncorporadorProductos incorporador;

    public ImportadorXml(Inventario inventario) {
        this.incorporador = new IncorporadorProductos(inventario);
    }

    /**
     * Importa los productos válidos del fichero XML indicado.
     *
     * @param textoRuta ruta del fichero escrita por el usuario
     * @return resumen de la importación
     * @throws ImportacionException si el fichero no se puede leer, está mal formado,
     *                              contiene un DTD o su raíz no es {@code catalogo}
     */
    public ResultadoImportacion importar(String textoRuta) throws ImportacionException {
        Path ruta = ComprobadorFichero.comprobarLegible(textoRuta);
        Document documento = leerDocumento(ruta);

        Element raiz = documento.getDocumentElement();
        if (!RAIZ.equals(raiz.getTagName())) {
            throw new ImportacionException("Estructura incorrecta: la raíz debe ser <" + RAIZ
                    + "> y es <" + raiz.getTagName() + ">.");
        }

        List<RegistroProducto> registros = new ArrayList<>();
        NodeList hijos = raiz.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            Node nodo = hijos.item(i);
            if (nodo.getNodeType() == Node.ELEMENT_NODE && ELEMENTO_PRODUCTO.equals(nodo.getNodeName())) {
                registros.add(aRegistro((Element) nodo));
            }
        }
        return incorporador.incorporar(registros);
    }

    /** Analiza el fichero completo con DOM. Si algo falla, no se ha tocado el inventario. */
    private Document leerDocumento(Path ruta) throws ImportacionException {
        try (InputStream entrada = Files.newInputStream(ruta)) {
            DocumentBuilder constructor = crearConstructorSeguro();
            constructor.setErrorHandler(new ManejadorErrores());
            return constructor.parse(entrada);

        } catch (SAXParseException ex) {
            if (esErrorDeDoctype(ex)) {
                throw new ImportacionException("El documento XML contiene una declaración DOCTYPE/DTD, "
                        + "que no se admite por seguridad. No se ha importado ningún producto.", ex);
            }
            throw new ImportacionException("El fichero XML está mal formado (línea " + ex.getLineNumber()
                    + ", columna " + ex.getColumnNumber() + ").", ex);
        } catch (SAXException ex) {
            throw new ImportacionException("No se ha podido interpretar el fichero XML.", ex);
        } catch (ParserConfigurationException ex) {
            throw new ImportacionException("No se ha podido configurar el analizador XML de forma segura: "
                    + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new ImportacionException("No se ha podido leer el fichero XML: " + ex.getMessage(), ex);
        }
    }

    /**
     * Crea un analizador DOM que rechaza cualquier DOCTYPE y no resuelve
     * entidades externas (protección frente a ataques XXE).
     */
    private DocumentBuilder crearConstructorSeguro() throws ParserConfigurationException {
        DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
        fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        fabrica.setFeature(FEATURE_SIN_DOCTYPE, true);
        fabrica.setFeature("http://xml.org/sax/features/external-general-entities", false);
        fabrica.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        fabrica.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        fabrica.setXIncludeAware(false);
        fabrica.setExpandEntityReferences(false);
        fabrica.setCoalescing(true);        // une el texto y las secciones CDATA
        fabrica.setIgnoringComments(true);  // los comentarios no forman parte de los datos
        return fabrica.newDocumentBuilder();
    }

    /**
     * El mensaje del analizador depende del idioma, pero siempre menciona la
     * característica desactivada, por lo que se usa ese texto para reconocerlo.
     */
    private boolean esErrorDeDoctype(SAXParseException ex) {
        String mensaje = ex.getMessage();
        return mensaje != null && mensaje.contains("disallow-doctype-decl");
    }

    /** Lee los seis campos de un elemento {@code producto} como texto. */
    private RegistroProducto aRegistro(Element producto) {
        return new RegistroProducto(
                textoDelHijo(producto, "codigo"), textoDelHijo(producto, "nombre"),
                textoDelHijo(producto, "precio"), textoDelHijo(producto, "stock"),
                textoDelHijo(producto, "categoriaId"), textoDelHijo(producto, "proveedorId"));
    }

    /**
     * Devuelve el texto del primer hijo directo con ese nombre, o null si el
     * elemento no existe. Un elemento vacío ({@code <codigo/>}) devuelve "".
     */
    private String textoDelHijo(Element padre, String nombre) {
        NodeList hijos = padre.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            Node nodo = hijos.item(i);
            if (nodo.getNodeType() == Node.ELEMENT_NODE && nombre.equals(nodo.getNodeName())) {
                return nodo.getTextContent();
            }
        }
        return null;
    }

    /**
     * Manejador de errores del analizador: ignora los avisos y convierte los
     * errores en excepciones. Evita además que el analizador escriba por su cuenta
     * mensajes "[Fatal Error]" en la consola.
     */
    private static final class ManejadorErrores implements ErrorHandler {
        @Override
        public void warning(SAXParseException ex) { }

        @Override
        public void error(SAXParseException ex) throws SAXException { throw ex; }

        @Override
        public void fatalError(SAXParseException ex) throws SAXException { throw ex; }
    }
}