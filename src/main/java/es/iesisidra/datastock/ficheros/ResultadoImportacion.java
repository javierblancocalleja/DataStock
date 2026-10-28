package es.iesisidra.datastock.ficheros;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resumen de una importación (JSON o XML): cuántos productos se han importado,
 * cuántos se han rechazado y por qué.
 */
public class ResultadoImportacion {
    private int importados;
    private final List<String> incidencias = new ArrayList<>();

    /** Anota un registro rechazado con su motivo. */
    public void registrarRechazo(String motivo) { incidencias.add(motivo); }

    /** Anota cuántos productos se han añadido al inventario. */
    public void registrarImportados(int cantidad) { importados = cantidad; }

    public int getImportados() { return importados; }

    public int getRechazados() { return incidencias.size(); }

    public int getEncontrados() { return importados + incidencias.size(); }

    public List<String> getIncidencias() { return Collections.unmodifiableList(incidencias); }

    /** Muestra el resumen por consola con el formato del enunciado. */
    public void imprimir() {
        System.out.println("Productos encontrados: " + getEncontrados());
        System.out.println("Importados: " + importados + " | Rechazados: " + getRechazados());
        if (incidencias.isEmpty()) {
            System.out.println("Importación finalizada.");
            return;
        }
        System.out.println("INCIDENCIAS:");
        for (String incidencia : incidencias) {
            System.out.println("- " + incidencia);
        }
        System.out.println("Importación finalizada con incidencias.");
    }
}