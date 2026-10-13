package mx.edu.analizador.semantico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import mx.edu.analizador.lexico.Lexer;
import org.junit.jupiter.api.Test;

class ErrorLlamadaMetodoTest {

    private static final String STRUCT = "struct Calc { int x; int (*sumar)(int a, int b); "
            + "void (*limpiar)(void); double (*dividir)(double, double); "
            + "void (*saludar)(const char *nombre); void (*usar)(void *datos); "
            + "void (*log)(const char *fmt, ...); }; ";

    private List<ErrorSemantico> analizar(String codigo) {
        return new ErrorLlamadaMetodo().analizar(new Lexer().analizar(codigo));
    }

    private List<ErrorSemantico> analizarLlamadas(String instrucciones) {
        return analizar(STRUCT + "void f(void) { struct Calc c; " + instrucciones + " }");
    }

    private void assertSinErrores(String instrucciones) {
        assertTrue(analizarLlamadas(instrucciones).isEmpty(), instrucciones);
    }

    private void assertUnError(String instrucciones) {
        assertEquals(1, analizarLlamadas(instrucciones).size(), instrucciones);
    }

    @Test
    void llamadasCorrectas() {
        assertSinErrores("c.sumar(1, 2); c.limpiar(); c.dividir(1.0, 2.0); c.saludar(\"hola\");");
        assertSinErrores("c.sumar(-1, +2); c.sumar(x, y + 1); c.usar(&x);");
    }

    @Test
    void faltanArgumentos() {
        assertUnError("c.sumar(1);");
        assertUnError("c.sumar();");
        assertUnError("c.saludar();");
    }

    @Test
    void sobranArgumentos() {
        assertUnError("c.limpiar(5);");
        assertUnError("c.sumar(1, 2, 3);");
    }

    @Test
    void tipoDeArgumentoIncorrecto() {
        assertUnError("c.sumar(\"uno\", 2);");
        assertUnError("c.sumar(1, \"dos\");");
        assertUnError("c.saludar(5);");
        assertUnError("c.saludar('a');");
        assertUnError("c.usar(3);");
    }

    @Test
    void conversionesPermitidas() {
        assertSinErrores("c.sumar(1, 2.5);");
        assertSinErrores("c.sumar('a', 2);");
        assertSinErrores("c.dividir(3, 4);");
        assertSinErrores("c.saludar(0); c.usar(0);");
    }

    @Test
    void parametrosVariables() {
        assertSinErrores("c.log(\"x\"); c.log(\"%d %d\", 1, 2);");
        assertUnError("c.log();");
    }

    @Test
    void llamadaConFlechaDesdePuntero() {
        assertEquals(1, analizar(STRUCT + "void f(void) { struct Calc *p; p->sumar(1); }").size());
        assertTrue(analizar(STRUCT + "void f(void) { struct Calc *p; p->sumar(1, 2); }").isEmpty());
    }

    @Test
    void structRecibidoComoParametro() {
        assertEquals(1, analizar(STRUCT + "void f(struct Calc *c) { c->limpiar(1); }").size());
        assertEquals(1, analizar(STRUCT + "void f(int n, struct Calc c) { c.limpiar(n); }").size());
    }

    @Test
    void typedefDeStruct() {
        String anonimo = "typedef struct { void (*m)(int); } T; ";
        assertEquals(1, analizar(anonimo + "void f(void) { T t; t.m(); }").size());

        String conNombre = "struct S { void (*m)(int); }; typedef struct S T; ";
        assertEquals(1, analizar(conNombre + "void f(void) { T t; t.m(1, 2); }").size());

        String antesDelStruct = "typedef struct S T; struct S { void (*m)(int); }; ";
        assertEquals(1, analizar(antesDelStruct + "void f(void) { T t; t.m(); }").size());
    }

    @Test
    void variasVariablesEnUnaDeclaracion() {
        assertEquals(2, analizar(STRUCT + "void f(void) { struct Calc a = {0}, *b; a.limpiar(1); b->limpiar(2); }").size());
    }

    @Test
    void arregloDeStructs() {
        assertEquals(1, analizar(STRUCT + "void f(void) { struct Calc ops[2]; ops[0].sumar(1); }").size());
    }

    @Test
    void respetaElAlcanceDeLasVariables() {
        // c es struct Calc solo dentro de f; en g es un int, así que no se revisa.
        assertTrue(analizar(STRUCT + "void f(void) { struct Calc c; } void g(void) { c.sumar(1); }").isEmpty());
        assertTrue(analizar(STRUCT + "void f(struct Calc c); void g(void) { c.sumar(1); }").isEmpty());
    }

    @Test
    void comasDentroDeOtraLlamadaNoSeCuentan() {
        assertSinErrores("c.sumar(g(1, 2), 3);");
        assertUnError("c.sumar(g(1, 2));");
    }

    @Test
    void miembrosQueNoSonPunteroAFuncionNoSeRevisan() {
        assertSinErrores("c.x = 5; c.noExiste(1, 2, 3);");
    }

    @Test
    void funcionesNormalesYVariablesSinStructSeIgnoran() {
        assertTrue(analizar("int sumar(int a, int b); void f(void) { sumar(1); x.sumar(1); }").isEmpty());
    }

    @Test
    void sintaxisDeCMasMasNoSeReconoce() {
        assertTrue(analizar("class A { public: void m(); }; void f() { A a; a.m(1); }").isEmpty());
    }

    @Test
    void reportaLaPosicionDelMetodo() {
        ErrorSemantico error = analizar("struct A { void (*m)(void); };\nvoid f(void) {\n    struct A a;\n    a.m(1);\n}")
                .getFirst();
        assertEquals("Llamada incorrecta a un método", error.nombre());
        assertEquals(4, error.linea());
        assertEquals(7, error.inicio());
        assertEquals(7, error.fin());
        assertTrue(error.descripcion().contains("espera 0 argumento(s) y se llamó con 1"));
    }

    @Test
    void archivoDeEjemplo() throws IOException {
        String codigo;
        try (InputStream entrada = getClass().getResourceAsStream("/llamadaMetodo.c")) {
            codigo = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<Integer> lineas = analizar(codigo).stream().map(ErrorSemantico::linea).toList();
        assertEquals(List.of(28, 29, 31, 32, 35, 37), lineas);
    }

    @Test
    void buscarErroresIncluyeLlamadaMetodo() {
        List<ErrorSemantico> errores = ErrorSemantico.buscarErrores(
                new Lexer().analizar("struct A { void (*m)(void); }; void f(void) { struct A a; a.m(1); }"));
        assertEquals(1, errores.size());
        assertEquals("Llamada incorrecta a un método", errores.getFirst().nombre());
    }
}
