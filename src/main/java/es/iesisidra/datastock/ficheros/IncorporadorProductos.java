package es.iesisidra.datastock.ficheros;

import es.iesisidra.datastock.modelo.Producto;
import es.iesisidra.datastock.servicio.Inventario;
import es.iesisidra.datastock.validacion.ValidadorProducto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parte común de las importaciones JSON y XML: recibe los registros ya leídos
 * del fichero (como texto), valida cada uno, rechaza los duplicados y añade
 * los correctos al inventario.
 * <p>Los productos se añaden todos al final, cuando ya se han revisado todos
 * los registros. La colección existente nunca se vacía ni se modifica.</p>
 */
public class IncorporadorProductos {

    /** Registro tal como aparece en el fichero, con los seis campos en texto (null si falta). */
    public record RegistroProducto(String codigo, String nombre, String precio,
                                   String stock, String categoriaId, String proveedorId) { }

    private final Inventario inventario;

    public IncorporadorProductos(Inventario inventario) {
        this.inventario = inventario;
    }

    /**
     * @param registros registros del fichero en orden; un elemento {@code null}
     *                  indica un registro con estructura inválida (p. ej. no era un objeto)
     * @return resumen con importados, rechazados y motivos
     */
    public ResultadoImportacion incorporar(List<RegistroProducto> registros) {
        ResultadoImportacion resultado = new ResultadoImportacion();
        List<Producto> aceptados = new ArrayList<>();
        Set<String> codigosDelFichero = new HashSet<>();

        int posicion = 0;
        for (RegistroProducto registro : registros) {
            posicion++;

            if (registro == null) {
                resultado.registrarRechazo("Producto " + posicion + ": el registro no tiene la estructura esperada.");
                continue;
            }

            String etiqueta = etiqueta(posicion, registro.codigo());
            ValidadorProducto.Resultado validacion = ValidadorProducto.validar(
                    registro.codigo(), registro.nombre(), registro.precio(),
                    registro.stock(), registro.categoriaId(), registro.proveedorId());

            if (!validacion.esValido()) {
                resultado.registrarRechazo(etiqueta + ": " + String.join("; ", validacion.errores()));
                continue;
            }

            Producto producto = validacion.producto();
            if (inventario.contieneCodigo(producto.getCodigo())) {
                resultado.registrarRechazo(etiqueta + ": código duplicado (ya existe en el inventario)");
            } else if (!codigosDelFichero.add(Inventario.normalizarCodigo(producto.getCodigo()))) {
                resultado.registrarRechazo(etiqueta + ": código duplicado (repetido en el fichero)");
            } else {
                aceptados.add(producto);
            }
        }

        for (Producto producto : aceptados) {
            inventario.agregar(producto);
        }
        resultado.registrarImportados(aceptados.size());
        return resultado;
    }

    private static String etiqueta(int posicion, String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return "Producto " + posicion;
        }
        return "Producto " + posicion + " (" + codigo.trim() + ")";
    }
}