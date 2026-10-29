package es.iesisidra.datastock.ui;

import es.iesisidra.datastock.ficheros.SerializacionException;
import es.iesisidra.datastock.ficheros.SerializadorInventario;
import es.iesisidra.datastock.modelo.Producto;
import es.iesisidra.datastock.servicio.Inventario;
import es.iesisidra.datastock.util.ColoresConsola;
import es.iesisidra.datastock.util.LectorConsola;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * RF-FIC-06 · Presentación de guardar y recuperar el inventario.
 * <p>Pide datos y confirmaciones al usuario y delega el trabajo con ficheros
 * en {@link SerializadorInventario}.</p>
 */
public class PantallaSerializacion {

    private final Inventario inventario;
    private final SerializadorInventario serializador = new SerializadorInventario();

    public PantallaSerializacion(Inventario inventario) {
        this.inventario = inventario;
    }

    /** Opción 6.1: guarda el inventario completo en un fichero .dat. */
    public void guardar() {
        System.out.println("\n===== GUARDAR INVENTARIO =====");
        if (inventario.getProductos().isEmpty()) {
            aviso("El inventario está vacío: no hay nada que guardar.");
            return;
        }
        try {
            String texto = LectorConsola.leerTexto("Ruta de destino (.dat): ");
            Path destino = serializador.resolverDestino(texto);
            System.out.println("Fichero de destino: " + destino);

            if (Files.exists(destino) && !LectorConsola.confirmar("El fichero ya existe. ¿Sobrescribir?")) {
                aviso("Operación cancelada: el fichero original no se ha modificado.");
                return;
            }
            serializador.guardar(inventario.getProductos(), destino);
            System.out.println("Inventario guardado correctamente.");
            System.out.println("Productos guardados: " + inventario.getProductos().size());
        } catch (SerializacionException ex) {
            error(ex.getMessage());
        }
    }

    /** Opción 6.2: recupera el inventario desde un .dat, sustituyendo el actual. */
    public void recuperar() {
        System.out.println("\n===== RECUPERAR INVENTARIO =====");
        String texto = LectorConsola.leerTexto("Ruta: ");
        try {
            // Se lee y valida TODO el fichero antes de tocar la memoria.
            List<Producto> recuperados = serializador.recuperar(texto);

            int actuales = inventario.getProductos().size();
            if (actuales > 0) {
                System.out.println("Actualmente hay " + actuales + " productos en memoria.");
                System.out.println("La recuperación sustituirá el inventario actual.");
                if (!LectorConsola.confirmar("¿Continuar?")) {
                    aviso("Operación cancelada: se conserva el inventario actual.");
                    return;
                }
            }
            inventario.getProductos().clear();
            inventario.getProductos().addAll(recuperados);
            System.out.println("Inventario recuperado correctamente.");
            System.out.println("Productos disponibles: " + inventario.getProductos().size());
        } catch (SerializacionException ex) {
            error(ex.getMessage());
            System.out.println("El inventario en memoria no ha cambiado.");
        }
    }

    private void aviso(String mensaje) {
        System.out.println(ColoresConsola.pintar(mensaje, ColoresConsola.AMARILLO));
    }

    private void error(String mensaje) {
        System.out.println(ColoresConsola.pintar("ERROR: " + mensaje, ColoresConsola.ROJO));
    }
}
