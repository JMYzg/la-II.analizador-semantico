package mx.edu.analizador.semantico;

import mx.edu.analizador.lexico.TipoToken;
import mx.edu.analizador.lexico.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ErrorArgumentosFuncion {

    private List<Token> tokens;
    private List<ErrorSemantico> errores;

    // Nombre de función -> cantidad de parámetros obligatorios.
    private Map<String, Integer> cantidades;

    // Nombres de funciones que permiten argumentos adicionales.
    private Set<String> variables;

    // Posiciones que pertenecen a declaraciones, no a llamadas.
    private Set<Integer> declaraciones;

    private static final Set<String> TIPOS = Set.of(
            "void", "int", "char", "short", "long",
            "float", "double", "signed", "unsigned",
            "_Bool", "bool"
    );

    private static final Set<String> MODIFICADORES = Set.of(
            "const", "volatile", "static", "extern", "inline"
    );

    public List<ErrorSemantico> analizar(List<Token> tokens) {
        this.tokens = tokens;
        errores = new ArrayList<>();
        cantidades = new HashMap<>();
        variables = new HashSet<>();
        declaraciones = new HashSet<>();

        buscarFunciones();
        revisarLlamadas();

        return errores;
    }

    private void buscarFunciones() {
        int llaves = 0;

        for (int i = 0; i < tokens.size(); i++) {
            if (es(i, "{")) {
                llaves++;
            } else if (es(i, "}")) {
                llaves--;
            }

            if (llaves != 0 || !esIdentificador(i) || !es(i + 1, "(")) {
                continue;
            }

            if (!tieneTipoAntes(i)) {
                continue;
            }

            int fin = cerrarParentesis(i + 1);

            if (fin == -1 || (!es(fin + 1, ";") && !es(fin + 1, "{"))) {
                continue;
            }

            for (int j = i; j <= fin; j++) {
                declaraciones.add(j);
            }

            // Dejamos () sin comprobar; para cero usamos (void).
            if (fin == i + 2) {
                i = fin;
                continue;
            }

            String nombre = tokens.get(i).lexema();
            List<String> parametros = separar(i + 2, fin);

            int cantidad = parametros.size();
            boolean variable = parametros.contains("...");

            if (parametros.size() == 1 && parametros.get(0).equals("void")) {
                cantidad = 0;
            }

            if (variable) {
                cantidad--;
                variables.add(nombre);
            } else {
                variables.remove(nombre);
            }

            cantidades.put(nombre, cantidad);
            i = fin;
        }
    }

    private boolean tieneTipoAntes(int nombre) {
        boolean encontroTipo = false;

        for (int i = nombre - 1; i >= 0; i--) {
            String texto = tokens.get(i).lexema();

            if (texto.equals(";") || texto.equals("{") || texto.equals("}")
                    || texto.stripLeading().startsWith("#")) {
                break;
            }

            if (TIPOS.contains(texto)) {
                encontroTipo = true;
            } else if (!MODIFICADORES.contains(texto) && !texto.equals("*")) {
                return false;
            }
        }

        return encontroTipo;
    }

    private void revisarLlamadas() {
        int llaves = 0;

        for (int i = 0; i < tokens.size(); i++) {
            if (es(i, "{")) {
                llaves++;
            } else if (es(i, "}")) {
                llaves--;
            }

            if (llaves <= 0 || declaraciones.contains(i)) {
                continue;
            }

            if (!esIdentificador(i) || !es(i + 1, "(")) {
                continue;
            }

            // Estos casos pertenecen al analizador de métodos.
            if (es(i - 1, ".") || es(i - 1, "->")) {
                continue;
            }

            Token funcion = tokens.get(i);
            String nombre = funcion.lexema();

            if (!cantidades.containsKey(nombre)) {
                continue;
            }

            int fin = cerrarParentesis(i + 1);

            if (fin == -1) {
                continue;
            }

            int recibidos = separar(i + 2, fin).size();
            int esperados = cantidades.get(nombre);

            boolean incorrecto = variables.contains(nombre)
                    ? recibidos < esperados
                    : recibidos != esperados;

            if (incorrecto) {
                String cantidadEsperada =
                        (variables.contains(nombre) ? "al menos " : "")
                        + esperados;

                errores.add(new ErrorSemantico(
                        "Argumentos incorrectos en una función",
                        "La función " + nombre + " espera "
                                + cantidadEsperada
                                + " argumento(s) y se llamó con " + recibidos,
                        funcion.linea(),
                        funcion.inicio(),
                        funcion.fin()
                ));
            }
        }
    }

    // Separa solo por las comas que no están dentro de otra expresión.
    private List<String> separar(int inicio, int fin) {
        List<String> partes = new ArrayList<>();

        if (inicio >= fin) {
            return partes;
        }

        int nivel = 0;
        StringBuilder parte = new StringBuilder();

        for (int i = inicio; i < fin; i++) {
            String texto = tokens.get(i).lexema();

            if (texto.equals("(") || texto.equals("[") || texto.equals("{")) {
                nivel++;
            } else if (texto.equals(")") || texto.equals("]")
                    || texto.equals("}")) {
                nivel--;
            }

            if (texto.equals(",") && nivel == 0) {
                partes.add(parte.toString());
                parte.setLength(0);
            } else {
                parte.append(texto);
            }
        }

        partes.add(parte.toString());
        return partes;
    }

    private int cerrarParentesis(int inicio) {
        int nivel = 0;

        for (int i = inicio; i < tokens.size(); i++) {
            if (es(i, "(")) {
                nivel++;
            } else if (es(i, ")")) {
                nivel--;

                if (nivel == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    private boolean esIdentificador(int i) {
        return i >= 0 && i < tokens.size()
                && tokens.get(i).tipo() == TipoToken.IDENTIFICADOR;
    }

    private boolean es(int i, String texto) {
        return i >= 0 && i < tokens.size()
                && tokens.get(i).lexema().equals(texto);
    }
}