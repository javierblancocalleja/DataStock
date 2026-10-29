package es.iesisidra.datastock.ui;

import es.iesisidra.datastock.ficheros.ExportacionException;
import es.iesisidra.datastock.ficheros.ExportadorCatalogo;
import es.iesisidra.datastock.servicio.Inventario;
import es.iesisidra.datastock.util.ColoresConsola;
import es.iesisidra.datastock.util.LectorConsola;

import java.nio.file.Path;

/**
 * RF-FIC-05 · Presentación de la exportación del inventario.
 * <p>Solicita la ruta de destino, pide confirmación antes de sobrescribir y
 * muestra el resultado. La generación y escritura de los ficheros se delega en
 * {@link ExportadorCatalogo}.</p>
 */
public class PantallaExportacion {

    /** Formatos de exportación disponibles. */
    private enum Formato { JSON, XML }

    private final ExportadorCatalogo exportador;

    public PantallaExportacion(Inventario inventario) {
        this.exportador = new ExportadorCatalogo(inventario);
    }

    /** Opción 5.1: exporta el inventario a un fichero JSON. */
    public void exportarJson() {
        exportar(Formato.JSON);
    }

    /** Opción 5.2: exporta el inventario a un fichero XML. */
    public void exportarXml() {
        exportar(Formato.XML);
    }

    private void exportar(Formato formato) {
        System.out.println("\n===== EXPORTAR CATÁLOGO (" + formato + ") =====");

        if (!exportador.hayProductos()) {
            mostrarError("El inventario está vacío: no hay productos que exportar.");
            return;
        }

        String textoRuta = LectorConsola.leerTexto("Ruta de destino: ");
        try {
            Path destino = exportador.prepararDestino(textoRuta);

            if (exportador.existe(destino)) {
                System.out.println("El fichero ya existe: " + destino);
                if (!LectorConsola.confirmar("¿Quieres sobrescribirlo?")) {
                    System.out.println("Exportación cancelada. El fichero original no se ha modificado.");
                    return;
                }
            }

            int exportados;
            if (formato == Formato.JSON) {
                exportados = exportador.exportarJson(destino);
            } else {
                exportados = exportador.exportarXml(destino);
            }

            System.out.println(ColoresConsola.pintar("Fichero generado correctamente.", ColoresConsola.VERDE));
            System.out.println("Ruta: " + destino);
            System.out.println("Formato: " + formato);
            System.out.println("Productos exportados: " + exportados);

        } catch (ExportacionException ex) {
            mostrarError(ex.getMessage());
            System.out.println("Exportación cancelada.");
        }
    }

    private void mostrarError(String mensaje) {
        System.out.println(ColoresConsola.pintar("ERROR: " + mensaje, ColoresConsola.ROJO));
    }
}
