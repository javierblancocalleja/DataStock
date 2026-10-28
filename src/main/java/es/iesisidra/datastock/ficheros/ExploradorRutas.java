package es.iesisidra.datastock.ficheros;

import es.iesisidra.datastock.util.ColoresConsola;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * RF-FIC-01 · Exploración de rutas, ficheros y directorios.
 * Recibe el texto de una ruta, comprueba si existe y muestra información
 * básica: si es un fichero (nombre, ruta absoluta y tamaño) o un directorio
 * (contenido inmediato, sin recorrer subdirectorios).
 */
public final class ExploradorRutas {

    private ExploradorRutas() { }

    /**
     * Explora la ruta indicada y muestra el resultado por consola.
     *
     * @param textoRuta ruta escrita por el usuario (puede ser vacía o inválida)
     */
    public static void explorar(String textoRuta) {
        if (textoRuta == null || textoRuta.isBlank()) {
            mostrarError("La ruta está vacía. Introduce una ruta válida.");
            return;
        }

        Path ruta;
        try {
            ruta = Path.of(textoRuta.trim());
        } catch (InvalidPathException ex) {
            mostrarError("La ruta no es válida: " + ex.getReason());
            return;
        }

        try {
            if (Files.notExists(ruta)) {
                mostrarError("La ruta no existe: " + ruta);
            } else if (Files.isRegularFile(ruta)) {
                mostrarFichero(ruta);
            } else if (Files.isDirectory(ruta)) {
                mostrarDirectorio(ruta);
            } else {
                mostrarError("La ruta no es un fichero ni un directorio normal: " + ruta);
            }
        } catch (AccessDeniedException | SecurityException ex) {
            mostrarError("Sin permisos para acceder a: " + ruta);
        } catch (IOException ex) {
            mostrarError("Error de entrada/salida al acceder a «" + ruta + "»: " + ex.getMessage());
        }
    }

    /** Muestra nombre, ruta absoluta y tamaño de un fichero. */
    private static void mostrarFichero(Path fichero) throws IOException {
        System.out.println("La ruta corresponde a un FICHERO.");
        System.out.println("Nombre:        " + fichero.getFileName());
        System.out.println("Ruta absoluta: " + fichero.toAbsolutePath().normalize());
        System.out.println("Tamaño:        " + Files.size(fichero) + " bytes");
    }

    /** Lista los ficheros/direcotrios inmediatos de un directorio y muestra los totales. */
    private static void mostrarDirectorio(Path directorio) throws IOException {
        List<Path> hijos = leerHijos(directorio);

        if (hijos.isEmpty()) {
            System.out.println("El directorio está vacío.");
            System.out.println("Total ficheros: 0 | Directorios: 0");
            return;
        }

        int ficheros = 0;
        int directorios = 0;
        for (Path hijo : hijos) {
            String nombre = hijo.getFileName().toString();
            if (Files.isDirectory(hijo)) {
                directorios++;
                System.out.println("[DIR] " + nombre);
            } else if (Files.isRegularFile(hijo)) {
                ficheros++;
                System.out.println("[FICHERO] " + nombre + " " + describirTamano(hijo));
            } else {
                System.out.println("[OTRO] " + nombre);
            }
        }
        System.out.println("Total ficheros: " + ficheros + " | Directorios: " + directorios);
    }

    /** Lee los elementos inmediatos del directorio */
    private static List<Path> leerHijos(Path directorio) throws IOException {
        List<Path> hijos = new ArrayList<>();
        try (DirectoryStream<Path> flujo = Files.newDirectoryStream(directorio)) {
            for (Path hijo : flujo) {
                hijos.add(hijo);
            }
        } catch (DirectoryIteratorException ex) {
            throw ex.getCause();
        }
        return hijos;
    }

    /** Devuelve el tamaño del fichero en bytes, o un aviso si no puede leerse. */
    private static String describirTamano(Path fichero) {
        try {
            return Files.size(fichero) + " bytes";
        } catch (IOException ex) {
            return "(tamaño no disponible)";
        }
    }

    private static void mostrarError(String mensaje) {
        System.out.println(ColoresConsola.pintar("ERROR: " + mensaje, ColoresConsola.ROJO));
    }
}