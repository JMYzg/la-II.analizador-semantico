package mx.edu.analizador.semantico;

import mx.edu.analizador.lexico.TipoToken;
import mx.edu.analizador.lexico.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Error 13: llamada incorrecta a un método. C no tiene clases; lo que hace el papel de un
 * método es un miembro de un struct que es puntero a función, por ejemplo
 * "struct Calculadora { int (*sumar)(int, int); };", que se llama con calc.sumar(1, 2)
 * o con p->sumar(1, 2).
 *
 * Primero busca los structs ("struct Nombre { ... }", "typedef struct { ... } Alias" y
 * "typedef struct Nombre Alias;") y la firma de cada miembro puntero a función. Después
 * revisa cada llamada variable.miembro(...) o variable->miembro(...) sobre una variable de
 * esos structs que esté al alcance, y reporta un error si:
 * - la cantidad de argumentos no es la de parámetros (con "..." acepta más), o
 * - un argumento literal no es compatible con su parámetro: una cadena donde se espera un
 *   número, o un número donde se espera un puntero (salvo 0, el puntero nulo).
 *
 * Solo compara el tipo de los literales: si el argumento es una variable o una expresión
 * no conoce su tipo y lo da por bueno. Llamar a un miembro que no existe es el error 12.
 */
public class ErrorLlamadaMetodo {

    private enum TipoParametro { ENTERO, REAL, TEXTO, PUNTERO, DESCONOCIDO }

    /** Los parámetros de un puntero a función; variable indica que termina en "...". */
    private record Firma(List<TipoParametro> parametros, boolean variable) {
        boolean aceptaCantidad(int cantidad) {
            return variable ? cantidad >= parametros.size() : cantidad == parametros.size();
        }

        String esperados() {
            return (variable ? "al menos " : "") + parametros.size();
        }
    }

    private static final Set<String> TIPOS_ENTEROS = Set.of(
            "int", "short", "long", "char", "unsigned", "signed", "_Bool", "bool", "size_t");

    private List<Token> tokens;
    private List<ErrorSemantico> errores;

    // Nombre del struct o de su typedef -> sus miembros puntero a función, por nombre.
    private Map<String, Map<String, Firma>> structs;

    // Variables al alcance: nombre -> tipo como se escribió ("struct Calculadora" o "Consola").
    // Hay un mapa por cada bloque { } abierto; el último es el más interno.
    private List<Map<String, String>> alcances;

    // Variables declaradas entre paréntesis (parámetros de una función o el encabezado de un
    // for): pasan al bloque que se abre justo después.
    private Map<String, String> pendientes;

    public List<ErrorSemantico> analizar(List<Token> tokens) {
        this.tokens = tokens;
        this.errores = new ArrayList<>();
        this.structs = new HashMap<>();

        buscarStructs();
        if (!structs.isEmpty()) {
            revisarLlamadas();
        }
        return errores;
    }

    // ---------------------------------------------------------------- structs y sus firmas

    private void buscarStructs() {
        Map<String, String> alias = new HashMap<>();

        for (int i = 0; i < tokens.size(); i++) {
            if (!esPalabra(i, "struct")) continue;

            boolean conTypedef = esPalabra(i - 1, "typedef");
            int j = i + 1;
            String nombre = null;
            if (esIdentificador(j)) {
                nombre = tokens.get(j).lexema();
                j++;
            }

            if (esSimbolo(j, "{")) {
                int cierre = cerrar(j, "{", "}");
                Map<String, Firma> miembros = miembrosDe(j + 1, cierre);
                if (nombre != null) structs.put(nombre, miembros);
                if (conTypedef) {
                    for (String nombreAlias : declaradores(cierre + 1)) {
                        structs.put(nombreAlias, miembros);
                    }
                }
            } else if (conTypedef && nombre != null) {
                // typedef struct Nombre Alias; (el struct puede estar definido antes o después)
                for (String nombreAlias : declaradores(j)) {
                    alias.put(nombreAlias, nombre);
                }
            }
        }

        alias.forEach((nombreAlias, nombre) -> {
            if (structs.containsKey(nombre)) structs.put(nombreAlias, structs.get(nombre));
        });
    }

    /** Los nombres declarados desde i hasta el ";", como Alias y PAlias en "} Alias, *PAlias;". */
    private List<String> declaradores(int i) {
        List<String> nombres = new ArrayList<>();
        for (int j = i; j < tokens.size() && !esSimbolo(j, ";"); j++) {
            if (esIdentificador(j)) nombres.add(tokens.get(j).lexema());
        }
        return nombres;
    }

    /** Los miembros puntero a función entre las llaves de un struct. */
    private Map<String, Firma> miembrosDe(int desde, int hasta) {
        Map<String, Firma> miembros = new HashMap<>();
        int i = desde;
        while (i < hasta) {
            int fin = finDeMiembro(i, hasta);
            // Forma de un puntero a función: tipo ( * nombre ) ( parámetros ) ;
            for (int k = i; k + 4 < fin; k++) {
                if (esSimbolo(k, "{")) break; // struct anidado: sus miembros son de otro struct
                if (esSimbolo(k, "(") && esOperador(k + 1, "*") && esIdentificador(k + 2)
                        && esSimbolo(k + 3, ")") && esSimbolo(k + 4, "(")) {
                    int cierre = cerrar(k + 4, "(", ")");
                    miembros.put(tokens.get(k + 2).lexema(), crearFirma(k + 5, cierre));
                    break;
                }
            }
            i = fin;
        }
        return miembros;
    }

    /** Devuelve la posición siguiente al ";" que cierra el miembro que empieza en i. */
    private int finDeMiembro(int i, int hasta) {
        for (int j = i; j < hasta; j++) {
            if (esSimbolo(j, "{")) j = cerrar(j, "{", "}");
            else if (esSimbolo(j, ";")) return j + 1;
        }
        return hasta;
    }

    private Firma crearFirma(int desde, int hasta) {
        List<TipoParametro> parametros = new ArrayList<>();
        boolean variable = false;
        for (List<Token> parametro : separarPorComas(desde, hasta)) {
            if (parametro.size() == 1 && parametro.getFirst().lexema().equals("...")) {
                variable = true;
            } else if (!(parametro.size() == 1 && parametro.getFirst().lexema().equals("void"))) {
                parametros.add(tipoDeParametro(parametro));
            }
        }
        return new Firma(parametros, variable);
    }

    private TipoParametro tipoDeParametro(List<Token> parametro) {
        boolean puntero = false;
        Set<String> palabras = new LinkedHashSet<>();
        for (Token token : parametro) {
            if (token.tipo() == TipoToken.OPERADOR && token.lexema().equals("*")) puntero = true;
            if (token.tipo() == TipoToken.SIMBOLO && token.lexema().equals("[")) puntero = true;
            if (token.tipo() == TipoToken.PALABRA_RESERVADA || token.tipo() == TipoToken.IDENTIFICADOR) {
                palabras.add(token.lexema());
            }
        }

        if (puntero) {
            return palabras.contains("char") ? TipoParametro.TEXTO : TipoParametro.PUNTERO;
        }
        if (palabras.contains("struct") || palabras.contains("union")) {
            return TipoParametro.DESCONOCIDO;
        }
        if (palabras.contains("float") || palabras.contains("double")) {
            return TipoParametro.REAL;
        }
        if (palabras.stream().anyMatch(TIPOS_ENTEROS::contains)) {
            return TipoParametro.ENTERO;
        }
        return TipoParametro.DESCONOCIDO;
    }

    // ---------------------------------------------------------------- variables y llamadas

    private void revisarLlamadas() {
        alcances = new ArrayList<>();
        alcances.add(new HashMap<>());
        pendientes = new HashMap<>();
        int parentesis = 0;

        for (int i = 0; i < tokens.size(); i++) {
            if (esSimbolo(i, "(")) {
                parentesis++;
            } else if (esSimbolo(i, ")")) {
                parentesis--;
            } else if (esSimbolo(i, "{")) {
                alcances.add(new HashMap<>(pendientes));
                pendientes.clear();
            } else if (esSimbolo(i, "}")) {
                if (alcances.size() > 1) alcances.removeLast();
            } else if (esSimbolo(i, ";") && parentesis <= 0) {
                pendientes.clear(); // eran los parámetros de un prototipo, sin cuerpo
            }

            registrarVariables(i, parentesis > 0);
            revisarLlamada(i);
        }
    }

    /**
     * Si en i empieza una declaración de variables de un struct conocido ("struct Nombre a, *b"
     * o "Alias a"), las registra en el alcance actual, o en pendientes si está entre paréntesis.
     */
    private void registrarVariables(int i, boolean entreParentesis) {
        String tipo;
        int j;
        if (esPalabra(i, "struct") && esIdentificador(i + 1)
                && structs.containsKey(tokens.get(i + 1).lexema()) && !esSimbolo(i + 2, "{")) {
            tipo = "struct " + tokens.get(i + 1).lexema();
            j = i + 2;
        } else if (esIdentificador(i) && structs.containsKey(tokens.get(i).lexema())
                && !esPalabra(i - 1, "struct") && !esAcceso(i - 1)) {
            tipo = tokens.get(i).lexema();
            j = i + 1;
        } else {
            return;
        }

        Map<String, String> destino = entreParentesis ? pendientes : alcances.getLast();
        while (true) {
            while (esOperador(j, "*")) j++;
            if (!esIdentificador(j) || esSimbolo(j + 1, "(")) return; // no es variable, o es función
            destino.put(tokens.get(j).lexema(), tipo);

            // Entre paréntesis cada parámetro trae su propio tipo: no hay más declaradores.
            if (entreParentesis) return;
            j = siguienteDeclarador(j + 1);
            if (j < 0) return;
        }
    }

    /** Salta arreglos e inicializadores; si sigue una ",", devuelve la posición siguiente. */
    private int siguienteDeclarador(int i) {
        int nivel = 0;
        for (int j = i; j < tokens.size(); j++) {
            if (esSimbolo(j, "(") || esSimbolo(j, "[") || esSimbolo(j, "{")) nivel++;
            else if (esSimbolo(j, ")") || esSimbolo(j, "]") || esSimbolo(j, "}")) nivel--;
            else if (nivel == 0 && esSimbolo(j, ",")) return j + 1;
            else if (nivel <= 0 && esSimbolo(j, ";")) return -1;
            if (nivel < 0) return -1;
        }
        return -1;
    }

    /** Revisa la llamada variable.miembro(...) o variable->miembro(...) que empiece en i. */
    private void revisarLlamada(int i) {
        if (!esIdentificador(i) || esAcceso(i - 1)) return;
        String tipo = tipoDeVariable(tokens.get(i).lexema());
        if (tipo == null) return;

        int j = i + 1;
        while (esSimbolo(j, "[")) j = cerrar(j, "[", "]") + 1; // arreglo de structs: ops[0].f()
        if (!esAcceso(j) || !esIdentificador(j + 1) || !esSimbolo(j + 2, "(")) return;

        Token metodo = tokens.get(j + 1);
        Firma firma = structs.get(tipo.replaceFirst("^struct ", "")).get(metodo.lexema());
        if (firma == null) return; // no existe o no es puntero a función: no es este error

        int cierre = cerrar(j + 2, "(", ")");
        List<List<Token>> argumentos = separarPorComas(j + 3, cierre);

        if (!firma.aceptaCantidad(argumentos.size())) {
            reportar(metodo, "El método " + metodo.lexema() + " de " + tipo + " espera "
                    + firma.esperados() + " argumento(s) y se llamó con " + argumentos.size());
            return;
        }
        for (int k = 0; k < firma.parametros().size(); k++) {
            if (!esCompatible(firma.parametros().get(k), argumentos.get(k))) {
                reportar(metodo, "El argumento " + (k + 1) + " de " + metodo.lexema()
                        + " no coincide con el tipo del parámetro declarado en " + tipo);
                return;
            }
        }
    }

    private String tipoDeVariable(String nombre) {
        for (int k = alcances.size() - 1; k >= 0; k--) {
            String tipo = alcances.get(k).get(nombre);
            if (tipo != null) return tipo;
        }
        return null;
    }

    private void reportar(Token metodo, String descripcion) {
        errores.add(new ErrorSemantico("Llamada incorrecta a un método", descripcion,
                metodo.linea(), metodo.inicio(), metodo.fin()));
    }

    // ---------------------------------------------------------------- tipos de los argumentos

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

    /** Si el argumento es un literal (con signo opcional), su tipo; si no, null. */
    private TipoToken tipoDeLiteral(List<Token> argumento) {
        List<Token> partes = argumento;
        if (partes.size() == 2 && partes.getFirst().tipo() == TipoToken.OPERADOR
                && (partes.getFirst().lexema().equals("-") || partes.getFirst().lexema().equals("+"))) {
            partes = partes.subList(1, 2);
        }
        if (partes.size() != 1) return null;

        TipoToken tipo = partes.getFirst().tipo();
        boolean esLiteral = tipo == TipoToken.CONSTANTE_ENTERA || tipo == TipoToken.CONSTANTE_REAL
                || tipo == TipoToken.CONSTANTE_CARACTER || tipo == TipoToken.CADENA;
        return esLiteral ? tipo : null;
    }

    private boolean esCero(List<Token> argumento) {
        return argumento.size() == 1 && argumento.getFirst().tipo() == TipoToken.CONSTANTE_ENTERA
                && argumento.getFirst().lexema().equals("0");
    }

    // ---------------------------------------------------------------- utilidades

    /** Separa los tokens entre desde y hasta por las comas que no están dentro de (), [] o {}. */
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

    /** Devuelve la posición del símbolo que cierra al que abre en i. */
    private int cerrar(int i, String abre, String cierra) {
        int nivel = 0;
        for (int j = i; j < tokens.size(); j++) {
            if (esSimbolo(j, abre)) nivel++;
            if (esSimbolo(j, cierra)) nivel--;
            if (nivel == 0) return j;
        }
        return tokens.size();
    }

    private boolean esAcceso(int i) {
        return esOperador(i, ".") || esOperador(i, "->");
    }

    private boolean esIdentificador(int i) {
        return i >= 0 && i < tokens.size() && tokens.get(i).tipo() == TipoToken.IDENTIFICADOR;
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
        return i >= 0 && i < tokens.size() && tokens.get(i).tipo() == tipo && tokens.get(i).lexema().equals(lexema);
    }
}
