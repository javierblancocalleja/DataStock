package es.iesisidra.datastock.ficheros;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Comprobaciones previas comunes a las importaciones: la ruta no está vacía,
 * existe, es un fichero regular y se puede leer.
 */
public final class ComprobadorFichero {

    private ComprobadorFichero() { }

    /**
     * @param textoRuta ruta escrita por el usuario
     * @return la ruta ya validada
     * @throws ImportacionException si la ruta no sirve para leer un fichero
     */
    public static Path comprobarLegible(String textoRuta) throws ImportacionException {
        if (textoRuta == null || textoRuta.isBlank()) {
            throw new ImportacionException("La ruta está vacía.");
        }
        Path ruta;
        try {
            ruta = Path.of(textoRuta.trim());
        } catch (InvalidPathException ex) {
            throw new ImportacionException("La ruta no es válida: " + ex.getReason());
        }
        if (Files.notExists(ruta)) {
            throw new ImportacionException("El fichero no existe: " + ruta);
        }
        if (Files.isDirectory(ruta)) {
            throw new ImportacionException("La ruta es un directorio, no un fichero: " + ruta);
        }
        if (!Files.isRegularFile(ruta)) {
            throw new ImportacionException("La ruta no es un fichero regular o no se puede acceder a ella: " + ruta);
        }
        if (!Files.isReadable(ruta)) {
            throw new ImportacionException("No hay permisos de lectura sobre el fichero: " + ruta);
        }
        return ruta;
    }
}