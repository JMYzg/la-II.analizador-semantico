package mx.edu.analizador.semantico;

import mx.edu.analizador.lexico.TipoToken;
import mx.edu.analizador.lexico.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ErrorLlamadaMetodo {
    private enum TipoParametro { ENTERO, REAL, TEXTO, PUNTERO, DESCONOCIDO }

    private record Parametro(TipoParametro tipo, boolean tieneDefecto) {}

    private record Metodo(String nombre, List<Parametro> parametros, boolean variable) {
        int minimo() {
            return (int) parametros.stream().filter(p -> !p.tieneDefecto()).count();
        }

        int maximo() {
            return parametros.size();
        }

        boolean aceptaCantidad(int cantidad) {
            return cantidad >= minimo() && (variable || cantidad <= maximo());
        }
    }

    private List<Token> tokens;
    private List<ErrorSemantico> errores;
    private Map<String, List<Metodo>> clases;
    private Map<String, String> objetos;

    public List<ErrorSemantico> analizar(List<Token> tokens) {
        this.tokens = tokens;
        this.errores = new ArrayList<>();
        this.clases = new HashMap<>();
        this.objetos = new HashMap<>();

        buscarClases();
        if (!clases.isEmpty()) {
            revisarLlamadas();
        }
        return errores;
    }

        private void buscarClases() {
        for (int i = 0; i + 1 < tokens.size(); i++) {
            if (!esInicioDeClase(i) || tokens.get(i + 1).tipo() != TipoToken.IDENTIFICADOR) continue;

            String nombre = tokens.get(i + 1).lexema();
            int llave = buscarLlaveDeClase(i + 2);
            if (llave < 0) continue;

            int cierre = cerrarLlave(llave);
            clases.computeIfAbsent(nombre, k -> new ArrayList<>())
                    .addAll(metodosDe(nombre, llave + 1, cierre));
        }
    }

    private boolean esInicioDeClase(int i) {
        Token token = tokens.get(i);
        boolean palabra = token.tipo() == TipoToken.IDENTIFICADOR || token.tipo() == TipoToken.PALABRA_RESERVADA;
        return palabra && (token.lexema().equals("class") || token.lexema().equals("struct"));
    }

        private int buscarLlaveDeClase(int i) {
        for (int j = i; j < tokens.size(); j++) {
            if (esSimbolo(j, "{")) return j;
            if (esSimbolo(j, ";") || esSimbolo(j, "(") || esSimbolo(j, ")")
                    || esSimbolo(j, "}") || esOperador(j, "=")) {
                return -1;
            }
        }
        return -1;
    }

        private List<Metodo> metodosDe(String clase, int desde, int hasta) {
        List<Metodo> metodos = new ArrayList<>();
        int i = desde;
        while (i < hasta) {
            if (esEtiquetaDeAcceso(i)) {
                i += 2;
                continue;
            }
            int fin = finDeMiembro(i, hasta);
            Metodo metodo = comoMetodo(clase, i, fin);
            if (metodo != null) metodos.add(metodo);
            i = fin;
        }
        return metodos;
    }

    private boolean esEtiquetaDeAcceso(int i) {
        if (i >= tokens.size() || tokens.get(i).tipo() != TipoToken.IDENTIFICADOR) return false;
        String lexema = tokens.get(i).lexema();
        return (lexema.equals("public") || lexema.equals("private") || lexema.equals("protected"))
                && esOperador(i + 1, ":");
    }

        private int finDeMiembro(int i, int hasta) {
        boolean inicializador = false;
        int parentesis = 0;
        int j = i;
        while (j < hasta) {
            if (esSimbolo(j, "(")) {
                parentesis++;
            } else if (esSimbolo(j, ")")) {
                parentesis--;
            } else if (esOperador(j, "=") && parentesis == 0) {
                inicializador = true;
            } else if (esSimbolo(j, ";") && parentesis <= 0) {
                return j + 1;
            } else if (esSimbolo(j, "{") && parentesis <= 0) {
                int cierre = cerrarLlave(j);
                if (!inicializador) {
                    int siguiente = cierre + 1;
                    if (esSimbolo(siguiente, ";")) siguiente++;
                    return Math.min(siguiente, hasta);
                }
                j = cierre;
            }
            j++;
        }
        return hasta;
    }

        private Metodo comoMetodo(String clase, int desde, int hasta) {
        for (int j = desde; j < hasta; j++) {
            if (esOperador(j, "=")) return null;
            if (!esSimbolo(j, "(")) continue;

            if (j == desde || tokens.get(j - 1).tipo() != TipoToken.IDENTIFICADOR) return null;
            if (esOperador(j + 1, "*")) return null;

            String nombre = tokens.get(j - 1).lexema();
            if (nombre.equals(clase)) return null;

            int cierre = cerrarParentesis(j);
            if (cierre >= hasta) return null;
            return crearMetodo(nombre, j + 1, cierre);
        }
        return null;
    }

    private Metodo crearMetodo(String nombre, int desde, int hasta) {
        List<Parametro> parametros = new ArrayList<>();
        boolean variable = false;
        for (List<Token> parametro : separarPorComas(desde, hasta)) {
            if (parametro.size() == 1 && parametro.get(0).lexema().equals("...")) {
                variable = true;
            } else if (parametro.size() == 1 && parametro.get(0).lexema().equals("void")) {
                continue;
            } else {
                parametros.add(crearParametro(parametro));
            }
        }
        return new Metodo(nombre, parametros, variable);
    }

    private Parametro crearParametro(List<Token> parametro) {
        boolean tieneDefecto = false;
        boolean puntero = false;
        Set<String> palabras = new LinkedHashSet<>();

        for (Token token : parametro) {
            if (token.tipo() == TipoToken.OPERADOR && token.lexema().equals("=")) {
                tieneDefecto = true;
                break;
            }
            if (token.tipo() == TipoToken.OPERADOR && token.lexema().equals("*")) puntero = true;
            if (token.tipo() == TipoToken.SIMBOLO && token.lexema().equals("[")) puntero = true;
            if (token.tipo() == TipoToken.PALABRA_RESERVADA || token.tipo() == TipoToken.IDENTIFICADOR) {
                palabras.add(token.lexema());
            }
        }

        TipoParametro tipo;
        if (palabras.contains("string")) {
            tipo = TipoParametro.TEXTO;
        } else if (puntero) {
            tipo = palabras.contains("char") ? TipoParametro.TEXTO : TipoParametro.PUNTERO;
        } else if (palabras.contains("float") || palabras.contains("double")) {
            tipo = TipoParametro.REAL;
        } else if (palabras.stream().anyMatch(p -> Set.of("int", "short", "long", "char", "unsigned",
                "signed", "_Bool", "bool", "size_t").contains(p))) {
            tipo = TipoParametro.ENTERO;
        } else {
            tipo = TipoParametro.DESCONOCIDO;
        }
        return new Parametro(tipo, tieneDefecto);
    }

    private void revisarLlamadas() {
        for (int i = 0; i < tokens.size(); i++) {
            registrarObjeto(i);
            if (esLlamada(i)) revisarLlamada(i);
        }
    }

        private void registrarObjeto(int i) {
        Token tipo = tokens.get(i);
        if (tipo.tipo() != TipoToken.IDENTIFICADOR || !clases.containsKey(tipo.lexema())) return;
        if (i > 0 && (esOperador(i - 1, ".") || esOperador(i - 1, "->"))) return;

        int j = i + 1;
        while (esOperador(j, "*") || esOperador(j, "&")) j++;
        if (j >= tokens.size() || tokens.get(j).tipo() != TipoToken.IDENTIFICADOR) return;

        boolean esDeclaracion = esSimbolo(j + 1, ";") || esSimbolo(j + 1, ",") || esSimbolo(j + 1, "(")
                || esSimbolo(j + 1, "[") || esSimbolo(j + 1, ")") || esOperador(j + 1, "=");
        if (esDeclaracion) {
            objetos.put(tokens.get(j).lexema(), tipo.lexema());
        }
    }

        private boolean esLlamada(int i) {
        Token objeto = tokens.get(i);
        if (objeto.tipo() != TipoToken.IDENTIFICADOR || !objetos.containsKey(objeto.lexema())) return false;
        if (i > 0 && (esOperador(i - 1, ".") || esOperador(i - 1, "->"))) return false;
        return (esOperador(i + 1, ".") || esOperador(i + 1, "->"))
                && i + 2 < tokens.size() && tokens.get(i + 2).tipo() == TipoToken.IDENTIFICADOR
                && esSimbolo(i + 3, "(");
    }

    private void revisarLlamada(int i) {
        String clase = objetos.get(tokens.get(i).lexema());
        Token metodo = tokens.get(i + 2);

        List<Metodo> candidatos = clases.get(clase).stream()
                .filter(m -> m.nombre().equals(metodo.lexema()))
                .toList();
        if (candidatos.isEmpty()) return;

        int cierre = cerrarParentesis(i + 3);
        if (cierre >= tokens.size()) return;

        List<List<Token>> argumentos = separarPorComas(i + 4, cierre);
        for (Metodo candidato : candidatos) {
            if (aceptaArgumentos(candidato, argumentos)) return;
        }

        errores.add(new ErrorSemantico(
                "Llamada incorrecta a un método",
                descripcion(clase, metodo, candidatos, argumentos.size()),
                metodo.linea(), metodo.inicio(), metodo.fin()));
    }

    private boolean aceptaArgumentos(Metodo metodo, List<List<Token>> argumentos) {
        if (!metodo.aceptaCantidad(argumentos.size())) return false;
        for (int k = 0; k < argumentos.size(); k++) {
            TipoParametro tipo = k < metodo.parametros().size()
                    ? metodo.parametros().get(k).tipo()
                    : TipoParametro.DESCONOCIDO;
            if (!esCompatible(tipo, argumentos.get(k))) return false;
        }
        return true;
    }

        private boolean esCompatible(TipoParametro tipo, List<Token> argumento) {
        TipoToken literal = tipoDeLiteral(argumento);
        if (literal == null) return true;

        return switch (tipo) {
            case ENTERO, REAL -> literal != TipoToken.CADENA;
            case TEXTO -> literal == TipoToken.CADENA || esCero(argumento);
            case PUNTERO -> esCero(argumento);
            case DESCONOCIDO -> true;
        };
    }

    private TipoToken tipoDeLiteral(List<Token> argumento) {
        List<Token> partes = argumento;
        if (partes.size() == 2 && partes.get(0).tipo() == TipoToken.OPERADOR
                && (partes.get(0).lexema().equals("-") || partes.get(0).lexema().equals("+"))) {
            partes = partes.subList(1, 2);
        }
        if (partes.size() != 1) return null;

        TipoToken tipo = partes.get(0).tipo();
        boolean esLiteral = tipo == TipoToken.CONSTANTE_ENTERA || tipo == TipoToken.CONSTANTE_REAL
                || tipo == TipoToken.CONSTANTE_CARACTER || tipo == TipoToken.CADENA;
        return esLiteral ? tipo : null;
    }

    private boolean esCero(List<Token> argumento) {
        return argumento.size() == 1 && argumento.get(0).tipo() == TipoToken.CONSTANTE_ENTERA
                && argumento.get(0).lexema().equals("0");
    }

    private String descripcion(String clase, Token metodo, List<Metodo> candidatos, int recibidos) {
        boolean cantidadCorrecta = candidatos.stream().anyMatch(m -> m.aceptaCantidad(recibidos));
        if (cantidadCorrecta) {
            return "Algún argumento de " + metodo.lexema() + " no coincide con el tipo del parámetro en la clase "
                    + clase;
        }

        Set<String> esperados = new LinkedHashSet<>();
        for (Metodo m : candidatos) {
            if (m.variable()) {
                esperados.add("al menos " + m.minimo());
            } else if (m.minimo() == m.maximo()) {
                esperados.add(String.valueOf(m.minimo()));
            } else {
                esperados.add("de " + m.minimo() + " a " + m.maximo());
            }
        }
        return "El método " + metodo.lexema() + " de la clase " + clase + " espera "
                + String.join(" o ", esperados) + " argumento(s) y se llamó con " + recibidos;
    }

        private List<List<Token>> separarPorComas(int desde, int hasta) {
        List<List<Token>> partes = new ArrayList<>();
        if (desde >= hasta) return partes;

        List<Token> actual = new ArrayList<>();
        int nivel = 0;
        for (int j = desde; j < hasta; j++) {
            Token token = tokens.get(j);
            if (token.tipo() == TipoToken.SIMBOLO) {
                switch (token.lexema()) {
                    case "(", "[", "{" -> nivel++;
                    case ")", "]", "}" -> nivel--;
                    case "," -> {
                        if (nivel == 0) {
                            partes.add(actual);
                            actual = new ArrayList<>();
                            continue;
                        }
                    }
                    default -> { }
                }
            }
            actual.add(token);
        }
        partes.add(actual);
        return partes;
    }

        private int cerrarParentesis(int i) {
        int nivel = 0;
        for (int j = i; j < tokens.size(); j++) {
            if (esSimbolo(j, "(")) nivel++;
            if (esSimbolo(j, ")")) nivel--;
            if (nivel == 0) return j;
        }
        return tokens.size();
    }

        private int cerrarLlave(int i) {
        int nivel = 0;
        for (int j = i; j < tokens.size(); j++) {
            if (esSimbolo(j, "{")) nivel++;
            if (esSimbolo(j, "}")) nivel--;
            if (nivel == 0) return j;
        }
        return tokens.size();
    }

    private boolean esSimbolo(int i, String lexema) {
        return es(i, TipoToken.SIMBOLO, lexema);
    }

    private boolean esOperador(int i, String lexema) {
        return es(i, TipoToken.OPERADOR, lexema);
    }

    private boolean es(int i, TipoToken tipo, String lexema) {
        return i >= 0 && i < tokens.size() && tokens.get(i).tipo() == tipo && tokens.get(i).lexema().equals(lexema);
    }
}
