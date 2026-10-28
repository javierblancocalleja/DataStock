package es.iesisidra.datastock.ficheros;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;
import es.iesisidra.datastock.ficheros.IncorporadorProductos.RegistroProducto;
import es.iesisidra.datastock.servicio.Inventario;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * RF-FIC-02 · Importación de un catálogo JSON.
 * <p>Pasos: (1) comprobar el fichero, (2) leer y analizar el documento completo
 * con Gson en modo estricto, (3) localizar el array {@code productos},
 * (4) convertir cada elemento en un registro y (5) delegar la validación y el
 * alta en {@link IncorporadorProductos}.</p>
 * <p>Si el documento está mal formado o su estructura general es incorrecta
 * se lanza {@link ImportacionException} <b>antes</b> de tocar el inventario.</p>
 */
public class ImportadorJson {

    private final IncorporadorProductos incorporador;

    public ImportadorJson(Inventario inventario) {
        this.incorporador = new IncorporadorProductos(inventario);
    }

    /**
     * Importa los productos válidos del fichero JSON indicado.
     *
     * @param textoRuta ruta del fichero escrita por el usuario
     * @return resumen de la importación
     * @throws ImportacionException si el fichero no se puede leer o no tiene la
     *                              estructura {@code {"productos": [ ... ]}}
     */
    public ResultadoImportacion importar(String textoRuta) throws ImportacionException {
        Path ruta = ComprobadorFichero.comprobarLegible(textoRuta);
        JsonArray array = leerArrayProductos(ruta);

        List<RegistroProducto> registros = new ArrayList<>();
        for (JsonElement elemento : array) {
            registros.add(elemento.isJsonObject() ? aRegistro(elemento.getAsJsonObject()) : null);
        }
        return incorporador.incorporar(registros);
    }

    /** Lee todo el documento y devuelve el array {@code productos} de la raíz. */
    private JsonArray leerArrayProductos(Path ruta) throws ImportacionException {
        try (Reader lector = Files.newBufferedReader(ruta, StandardCharsets.UTF_8);
             JsonReader json = new JsonReader(lector)) {

            json.setStrictness(Strictness.STRICT); // rechaza comentarios, comillas simples, etc.
            JsonElement raiz = JsonParser.parseReader(json);
            if (json.peek() != JsonToken.END_DOCUMENT) {
                throw new MalformedJsonException("hay contenido después del documento");
            }
            return extraerArray(raiz);

        } catch (JsonSyntaxException | MalformedJsonException ex) {
            throw new ImportacionException("El fichero JSON está mal formado (" + detalle(ex) + ").", ex);
        } catch (JsonParseException | IOException ex) {
            throw new ImportacionException("No se ha podido leer el fichero JSON: " + ex.getMessage(), ex);
        }
    }

    /** Extrae de Gson solo la posición del error (línea, columna y ruta JSON). */
    private static String detalle(Exception ex) {
        String mensaje = String.valueOf(ex.getMessage());
        int inicio = mensaje.indexOf("line ");
        String resto = inicio >= 0 ? mensaje.substring(inicio) : mensaje;
        return resto.lines().findFirst().orElse(resto);
    }

    private JsonArray extraerArray(JsonElement raiz) throws ImportacionException {
        if (!raiz.isJsonObject()) {
            throw new ImportacionException(
                    "Estructura incorrecta: la raíz debe ser un objeto con la clave \"productos\".");
        }
        JsonElement productos = raiz.getAsJsonObject().get("productos");
        if (productos == null || !productos.isJsonArray()) {
            throw new ImportacionException(
                    "Estructura incorrecta: falta la clave \"productos\" o no contiene un array.");
        }
        return productos.getAsJsonArray();
    }

    /** Convierte un objeto JSON en un registro de seis campos de texto. */
    private RegistroProducto aRegistro(JsonObject objeto) {
        return new RegistroProducto(
                texto(objeto, "codigo"), texto(objeto, "nombre"), texto(objeto, "precio"),
                texto(objeto, "stock"), texto(objeto, "categoriaId"), texto(objeto, "proveedorId"));
    }

    /** Devuelve el valor del campo como texto, o null si falta o es null en el JSON. */
    private String texto(JsonObject objeto, String campo) {
        JsonElement valor = objeto.get(campo);
        if (valor == null || valor.isJsonNull()) {
            return null;
        }
        return valor.isJsonPrimitive() ? valor.getAsString() : valor.toString();
    }
}