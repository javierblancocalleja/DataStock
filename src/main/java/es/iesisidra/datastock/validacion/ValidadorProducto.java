package es.iesisidra.datastock.validacion;

import es.iesisidra.datastock.modelo.Producto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reglas de validación de un producto (apartado 6 del enunciado).
 * <p>Recibe los seis campos como texto, tal y como llegan de un fichero, para
 * que JSON y XML (RF-FIC-02 y RF-FIC-03) apliquen exactamente las mismas reglas.
 * Un campo ausente se representa con {@code null}.</p>
 * <p>El código admite letras en cualquier caso, dígitos y guiones: como la
 * unicidad no distingue mayúsculas, "tec-001" y "TEC-001" son el mismo código.</p>
 */
public final class ValidadorProducto {

    private static final Pattern PATRON_CODIGO = Pattern.compile("[A-Za-z0-9-]+");
    private static final int MAX_CODIGO = 12;
    private static final int MIN_NOMBRE = 3;
    private static final int MAX_NOMBRE = 100;

    private ValidadorProducto() { }

    /**
     * Resultado de validar un registro.
     *
     * @param producto producto construido (solo si no hay errores; si no, null)
     * @param errores  motivos de rechazo; vacío si el registro es válido
     */
    public record Resultado(Producto producto, List<String> errores) {
        public boolean esValido() { return errores.isEmpty(); }
    }

    /** Valida los seis campos y, si son correctos, construye el {@link Producto}. */
    public static Resultado validar(String codigo, String nombre, String precio,
                                    String stock, String categoriaId, String proveedorId) {
        List<String> errores = new ArrayList<>();

        String cod = codigo == null ? "" : codigo.trim();
        if (cod.isEmpty()) {
            errores.add("el código es obligatorio");
        } else if (cod.length() > MAX_CODIGO) {
            errores.add("el código no puede superar " + MAX_CODIGO + " caracteres");
        } else if (!PATRON_CODIGO.matcher(cod).matches()) {
            errores.add("el código solo admite letras, dígitos y guiones");
        }

        String nom = nombre == null ? "" : nombre.trim();
        if (nom.isEmpty()) {
            errores.add("el nombre es obligatorio");
        } else if (nom.length() < MIN_NOMBRE || nom.length() > MAX_NOMBRE) {
            errores.add("el nombre debe tener entre " + MIN_NOMBRE + " y " + MAX_NOMBRE + " caracteres");
        }

        BigDecimal pre = null;
        if (precio == null || precio.isBlank()) {
            errores.add("el precio es obligatorio");
        } else {
            try {
                pre = new BigDecimal(precio.trim());
                if (pre.scale() < 0) {
                    pre = pre.setScale(0); // "1e2" se guarda como 100, no como 1E+2
                }
                if (pre.signum() <= 0) {
                    errores.add("el precio debe ser mayor que 0");
                }
            } catch (NumberFormatException ex) {
                errores.add("el precio no es un número válido");
            }
        }

        Integer sto = leerEntero(stock, "el stock", errores);
        if (sto != null && sto < 0) {
            errores.add("el stock no puede ser negativo");
        }

        Integer cat = leerEntero(categoriaId, "categoriaId", errores);
        if (cat != null && cat <= 0) {
            errores.add("categoriaId debe ser un entero positivo");
        }

        Integer prov = leerEntero(proveedorId, "proveedorId", errores);
        if (prov != null && prov <= 0) {
            errores.add("proveedorId debe ser un entero positivo");
        }

        if (!errores.isEmpty()) {
            return new Resultado(null, errores);
        }
        return new Resultado(new Producto(cod, nom, pre, sto, cat, prov), errores);
    }

    /** Convierte un texto a entero; si falla, añade el error y devuelve null. */
    private static Integer leerEntero(String texto, String campo, List<String> errores) {
        if (texto == null || texto.isBlank()) {
            errores.add(campo + " es obligatorio");
            return null;
        }
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException ex) {
            errores.add(campo + " debe ser un número entero");
            return null;
        }
    }
}