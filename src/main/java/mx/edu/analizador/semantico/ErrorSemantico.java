package mx.edu.analizador.semantico;

import mx.edu.analizador.lexico.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * Un error semántico encontrado en el código: es una fila de la tabla de errores de la interfaz.
 * linea, inicio y fin señalan el token que causó el error (columnas desde 1, fin inclusivo).
 */
public record ErrorSemantico(String nombre, String descripcion, int linea, int inicio, int fin) {

    /**
     * Busca todos los errores semánticos en los tokens. Cada error del catálogo tiene su
     * propia clase; al implementar uno nuevo, agrégalo aquí.
     */
    public static List<ErrorSemantico> buscarErrores(List<Token> tokens) {
        List<ErrorSemantico> errores = new ArrayList<>();
        errores.addAll(new ErrorContinue().analizar(tokens));      // 9 - Uso incorrecto de continue
        errores.addAll(new ErrorLlamadaMetodo().analizar(tokens)); // 13 - Llamada incorrecta a un método
        return errores;
    }
}
