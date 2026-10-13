package mx.edu.analizador.semantico;

import mx.edu.analizador.lexico.Token;

import java.util.ArrayList;
import java.util.List;

public class ErrorSemantico {
    private final String nombre;
    private final String descripcion;
    private final int linea;
    private final int inicio;
    private final int fin;

    public ErrorSemantico(String nombre, String descripcion, int linea, int inicio, int fin) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.linea = linea;
        this.inicio = inicio;
        this.fin = fin;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public int getLinea() {
        return linea;
    }

    public int getInicio() {
        return inicio;
    }

    public int getFin() {
        return fin;
    }

    /*@Override
    public String toString() {
        return nombre + " en línea " + linea + ", columna " + inicio;
    }*/

    //Busca los errores con las otras clases (por ahora, solo el error 9)
    public static List<ErrorSemantico> buscarErrores(List<Token> tokens) {
        List<ErrorSemantico> errores = new ArrayList<>();
        errores.addAll(new ErrorContinue().analizar(tokens));
        return errores;
    }
}
