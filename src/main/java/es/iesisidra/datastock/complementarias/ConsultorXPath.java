package es.iesisidra.datastock.complementarias;

import es.iesisidra.datastock.ficheros.ComprobadorFichero;
import es.iesisidra.datastock.ficheros.ImportacionException;
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
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * RF-FIC-07 · Consultas XPath sobre un catálogo XML ya abierto.
 * <p>Abre el documento con DOM en modo seguro (sin DTD ni entidades externas)
 * y permite hacer varias consultas sobre él sin importar nada al inventario.
 * No escribe por consola: devuelve los elementos {@code producto} encontrados.</p>
 * <p>Seguridad XPath: el texto del usuario nunca se concatena sin control.
 * El código se busca seleccionando nodos y comparando en Java; la categoría
 * y el precio llegan ya convertidos a número, por lo que solo pueden aportar
 * dígitos, punto y signo a la expresión.</p>
 */
public class ConsultorXPath {

    private static final String RAIZ = "catalogo";
    private static final String FEATURE_SIN_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";

    private final Document documento;
    private final XPath xpath;

    private ConsultorXPath(Document documento, XPath xpath) {
        this.documento = documento;
        this.xpath = xpath;
    }

    /**
     * Abre un XML con raíz {@code catalogo} y prepara las consultas.
     *
     * @throws ImportacionException si el fichero no existe, no se puede leer,
     *                              está mal formado, tiene DTD o su raíz no es catalogo
     */
    public static ConsultorXPath abrir(String textoRuta) throws ImportacionException {
        Path ruta = ComprobadorFichero.comprobarLegible(textoRuta);
        Document documento = leerDocumento(ruta);

        String raiz = documento.getDocumentElement().getTagName();
        if (!RAIZ.equals(raiz)) {
            throw new ImportacionException("Estructura incorrecta: la raíz debe ser <" + RAIZ
                    + "> y es <" + raiz + ">.");
        }
        try {
            XPathFactory fabrica = XPathFactory.newInstance();
            fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return new ConsultorXPath(documento, fabrica.newXPath());
        } catch (XPathFactoryConfigurationException ex) {
            throw new ImportacionException("No se ha podido configurar XPath de forma segura.", ex);
        }
    }

    /** Busca un producto por código (sin distinguir mayúsculas ni espacios extremos). */
    public List<Element> buscarPorCodigo(String codigo) throws ImportacionException {
        List<Element> encontrados = new ArrayList<>();
        String buscado = Inventario.normalizarCodigo(codigo);
        if (buscado.isEmpty()) {
            return encontrados;
        }
        // La expresión es fija; la comparación con el texto del usuario se hace en Java.
        for (Element producto : seleccionar("/catalogo/producto")) {
            String actual = textoDe(producto, "codigo");
            if (actual != null && Inventario.normalizarCodigo(actual).equals(buscado)) {
                encontrados.add(producto);
            }
        }
        return encontrados;
    }

    /** Productos cuya categoriaId es la indicada (entero ya validado). */
    public List<Element> filtrarPorCategoria(int categoriaId) throws ImportacionException {
        return seleccionar("/catalogo/producto[categoriaId=" + categoriaId + "]");
    }

    /** Productos con precio estrictamente mayor que el valor (BigDecimal ya validado). */
    public List<Element> filtrarPrecioSuperior(BigDecimal minimo) throws ImportacionException {
        return seleccionar("/catalogo/producto[precio > " + minimo.toPlainString() + "]");
    }

    /** Cuenta los elementos producto, aunque alguno tenga datos incorrectos. */
    public int contarProductos() throws ImportacionException {
        try {
            Double total = (Double) xpath.evaluate("count(/catalogo/producto)", documento, XPathConstants.NUMBER);
            return total.intValue();
        } catch (XPathExpressionException ex) {
            throw new ImportacionException("Error al evaluar la consulta XPath: " + ex.getMessage(), ex);
        }
    }

    /** Devuelve el texto (sin espacios extremos) del hijo directo indicado, o null si no existe. */
    public static String textoDe(Element producto, String nombre) {
        NodeList hijos = producto.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            Node nodo = hijos.item(i);
            if (nodo.getNodeType() == Node.ELEMENT_NODE && nombre.equals(nodo.getNodeName())) {
                return nodo.getTextContent().trim();
            }
        }
        return null;
    }

    /** Evalúa una expresión que devuelve nodos y los pasa a una lista de elementos. */
    private List<Element> seleccionar(String expresion) throws ImportacionException {
        List<Element> elementos = new ArrayList<>();
        try {
            NodeList nodos = (NodeList) xpath.evaluate(expresion, documento, XPathConstants.NODESET);
            for (int i = 0; i < nodos.getLength(); i++) {
                Node nodo = nodos.item(i);
                if (nodo.getNodeType() == Node.ELEMENT_NODE) {
                    elementos.add((Element) nodo);
                }
            }
        } catch (XPathExpressionException ex) {
            throw new ImportacionException("Error al evaluar la consulta XPath: " + ex.getMessage(), ex);
        }
        return elementos;
    }

    /** Analiza el fichero con DOM seguro; rechaza DTD y XML mal formado. */
    private static Document leerDocumento(Path ruta) throws ImportacionException {
        try (InputStream entrada = Files.newInputStream(ruta)) {
            DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
            fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            fabrica.setFeature(FEATURE_SIN_DOCTYPE, true);
            fabrica.setFeature("http://xml.org/sax/features/external-general-entities", false);
            fabrica.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            fabrica.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            fabrica.setXIncludeAware(false);
            fabrica.setExpandEntityReferences(false);
            fabrica.setCoalescing(true);
            fabrica.setIgnoringComments(true);
            DocumentBuilder constructor = fabrica.newDocumentBuilder();
            constructor.setErrorHandler(new ManejadorErrores());
            return constructor.parse(entrada);

        } catch (SAXParseException ex) {
            String mensaje = ex.getMessage();
            if (mensaje != null && mensaje.contains("disallow-doctype-decl")) {
                throw new ImportacionException("El documento XML contiene una declaración DOCTYPE/DTD, "
                        + "que no se admite por seguridad.", ex);
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

    /** Convierte errores del analizador en excepciones y evita que escriba en consola. */
    private static final class ManejadorErrores implements ErrorHandler {
        @Override public void warning(SAXParseException ex) { }
        @Override public void error(SAXParseException ex) throws SAXException { throw ex; }
        @Override public void fatalError(SAXParseException ex) throws SAXException { throw ex; }
    }
}
