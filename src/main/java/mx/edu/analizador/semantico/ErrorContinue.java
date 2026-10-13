package mx.edu.analizador.semantico;

import mx.edu.analizador.lexico.TipoToken;
import mx.edu.analizador.lexico.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * Error 9: uso incorrecto de continue. En C, continue solo es válido dentro del cuerpo
 * de un ciclo (for, while o do-while); un switch o un if no cuentan como ciclo.
 *
 * Recorre los tokens sentencia por sentencia, recordando si la sentencia actual está
 * dentro de un ciclo. El cuerpo de un ciclo es la sentencia que sigue a su encabezado,
 * con o sin llaves, así que también cubre casos como "for (;;) if (a) { x++; continue; }"
 * o "do continue; while (x);".
 */
public class ErrorContinue {

    private List<Token> tokens;
    private List<ErrorSemantico> errores;

    public List<ErrorSemantico> analizar(List<Token> tokens) {
        this.tokens = tokens;
        this.errores = new ArrayList<>();

        int i = 0;
        while (i < tokens.size()) {
            // Una } sin su { no pertenece a ninguna sentencia: se salta.
            if (esSimbolo(i, "}")) {
                i++;
            } else {
                i = sentencia(i, false);
            }
        }
        return errores;
    }

    /** Recorre la sentencia que empieza en i y devuelve la posición siguiente a ella. */
    private int sentencia(int i, boolean enCiclo) {
        if (i >= tokens.size()) return i;

        if (esSimbolo(i, "{")) {
            return bloque(i + 1, enCiclo);
        }
        if (esPalabra(i, "for") || esPalabra(i, "while")) {
            return sentencia(saltarParentesis(i + 1), true);
        }
        if (esPalabra(i, "do")) {
            int j = sentencia(i + 1, true);
            if (esPalabra(j, "while")) j = saltarParentesis(j + 1);
            if (esSimbolo(j, ";")) j++;
            return j;
        }
        if (esPalabra(i, "if")) {
            int j = sentencia(saltarParentesis(i + 1), enCiclo);
            if (esPalabra(j, "else")) j = sentencia(j + 1, enCiclo);
            return j;
        }
        if (esPalabra(i, "switch")) {
            return sentencia(saltarParentesis(i + 1), enCiclo);
        }
        if (esPalabra(i, "case") || esPalabra(i, "default")) {
            return saltarEtiqueta(i);
        }
        if (tokens.get(i).tipo() == TipoToken.IDENTIFICADOR && esOperador(i + 1, ":")) {
            return i + 2; // etiqueta de goto
        }
        if (esPalabra(i, "continue") && !enCiclo) {
            Token token = tokens.get(i);
            errores.add(new ErrorSemantico(
                    "Uso incorrecto de continue",
                    "Solo se puede usar dentro de un ciclo (for, while o do-while)",
                    token.linea(), token.inicio(), token.fin()));
        }
        return sentenciaSimple(i, enCiclo);
    }

    /** Recorre las sentencias de un bloque que empieza después de su {, y salta su }. */
    private int bloque(int i, boolean enCiclo) {
        while (i < tokens.size() && !esSimbolo(i, "}")) {
            i = sentencia(i, enCiclo);
        }
        return Math.min(i + 1, tokens.size());
    }

    /**
     * Expresiones, declaraciones, return, break, etc.: avanza hasta el ; que las cierra.
     * Si encuentra un { (cuerpo de una función, struct o inicializador) recorre ese bloque,
     * para revisar lo que hay dentro, y sigue. Se detiene sin consumir una } que cierre
     * el bloque de afuera.
     */
    private int sentenciaSimple(int i, boolean enCiclo) {
        int parentesis = 0;
        int j = i;
        while (j < tokens.size()) {
            if (esSimbolo(j, "(")) {
                parentesis++;
            } else if (esSimbolo(j, ")")) {
                parentesis--;
            } else if (esSimbolo(j, "{")) {
                j = bloque(j + 1, enCiclo);
                continue;
            } else if (esSimbolo(j, "}")) {
                return j;
            } else if (esSimbolo(j, ";") && parentesis <= 0) {
                return j + 1;
            }
            j++;
        }
        return j;
    }

    /** Si en i hay un (, devuelve la posición siguiente a su ) correspondiente. */
    private int saltarParentesis(int i) {
        if (!esSimbolo(i, "(")) return i;
        int parentesis = 0;
        for (int j = i; j < tokens.size(); j++) {
            if (esSimbolo(j, "(")) parentesis++;
            if (esSimbolo(j, ")")) parentesis--;
            if (parentesis == 0) return j + 1;
        }
        return tokens.size();
    }

    /** Salta "case expr:" o "default:" y devuelve la posición siguiente al ":". */
    private int saltarEtiqueta(int i) {
        for (int j = i + 1; j < tokens.size(); j++) {
            if (esOperador(j, ":")) return j + 1;
            if (esSimbolo(j, ";") || esSimbolo(j, "{") || esSimbolo(j, "}")) return j;
        }
        return tokens.size();
    }

    private boolean esPalabra(int i, String lexema) {
        return es(i, TipoToken.PALABRA_RESERVADA, lexema);
    }

    private boolean esSimbolo(int i, String lexema) {
        return es(i, TipoToken.SIMBOLO, lexema);
    }

    private boolean esOperador(int i, String lexema) {
        return es(i, TipoToken.OPERADOR, lexema);
    }

    private boolean es(int i, TipoToken tipo, String lexema) {
        return i < tokens.size() && tokens.get(i).tipo() == tipo && tokens.get(i).lexema().equals(lexema);
    }
}
