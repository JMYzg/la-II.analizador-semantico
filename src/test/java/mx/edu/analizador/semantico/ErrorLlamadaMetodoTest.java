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

    private static final String CLASE = "class Calc { public: int sumar(int a, int b); "
            + "void limpiar(); double dividir(double a, double b = 1.0); "
            + "void saludar(const char *nombre); void nada(void); }; ";

    private List<ErrorSemantico> analizar(String codigo) {
        return new ErrorLlamadaMetodo().analizar(new Lexer().analizar(codigo));
    }

    private List<ErrorSemantico> analizarLlamadas(String instrucciones) {
        return analizar(CLASE + "void f() { Calc c; " + instrucciones + " }");
    }

    private void assertSinErrores(String instrucciones) {
        assertTrue(analizarLlamadas(instrucciones).isEmpty(), instrucciones);
    }

    private void assertUnError(String instrucciones) {
        assertEquals(1, analizarLlamadas(instrucciones).size(), instrucciones);
    }

    @Test
    void llamadasCorrectas() {
        assertSinErrores("c.sumar(1, 2); c.limpiar(); c.nada(); c.saludar(\"hola\");");
        assertSinErrores("c.dividir(4.0); c.dividir(4.0, 2.0); c.sumar(-1, +2);");
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
        assertUnError("c.nada(1);");
        assertUnError("c.sumar(1, 2, 3);");
    }

    @Test
    void tipoDeArgumentoIncorrecto() {
        assertUnError("c.sumar(\"uno\", 2);");
        assertUnError("c.sumar(1, \"dos\");");
        assertUnError("c.saludar(5);");
        assertUnError("c.saludar('a');");
    }

    @Test
    void conversionesPermitidas() {
        assertSinErrores("c.sumar(1, 2.5);");
        assertSinErrores("c.sumar('a', 2);");
        assertSinErrores("c.dividir(3);");
        assertSinErrores("c.saludar(0);");
    }

    @Test
    void parametroConValorPorDefecto() {
        assertSinErrores("c.dividir(1.0); c.dividir(1.0, 2.0);");
        assertUnError("c.dividir();");
        assertUnError("c.dividir(1.0, 2.0, 3.0);");
    }

    @Test
    void sobrecargaDeMetodos() {
        String clase = "class A { public: void f(int x); void f(int x, int y); }; ";
        assertTrue(analizar(clase + "void g() { A a; a.f(1); a.f(1, 2); }").isEmpty());
        assertEquals(1, analizar(clase + "void g() { A a; a.f(1, 2, 3); }").size());
        assertEquals(1, analizar(clase + "void g() { A a; a.f(); }").size());
    }

    @Test
    void parametrosVariables() {
        String clase = "class A { public: void log(const char *fmt, ...); }; ";
        assertTrue(analizar(clase + "void g() { A a; a.log(\"x\"); a.log(\"x\", 1, 2, 3); }").isEmpty());
        assertEquals(1, analizar(clase + "void g() { A a; a.log(); }").size());
    }

    @Test
    void objetoConPuntero() {
        String clase = "class A { public: void m(); }; ";
        assertEquals(1, analizar(clase + "void g() { A *p; p->m(1); }").size());
        assertTrue(analizar(clase + "void g() { A *p; p->m(); }").isEmpty());
    }

    @Test
    void objetoRecibidoComoParametro() {
        String clase = "class A { public: void m(); }; ";
        assertEquals(1, analizar(clase + "void g(A obj) { obj.m(1); }").size());
    }

    @Test
    void structTambienCuenta() {
        assertEquals(1, analizar("struct P { void m(int a); }; void g() { struct P p; p.m(); }").size());
    }

    @Test
    void comasDentroDeOtraLlamadaNoSeCuentan() {
        assertSinErrores("c.sumar(g(1, 2), 3);");
        assertUnError("c.sumar(g(1, 2));");
    }

    @Test
    void metodoInexistenteNoEsEsteError() {
        assertSinErrores("c.noExiste(1, 2, 3);");
    }

    @Test
    void objetoNoDeclaradoOSinClaseSeIgnora() {
        assertSinErrores("x.sumar(1); int n = 3; n.sumar(1);");
        assertTrue(analizar("void f() { x.sumar(1); }").isEmpty());
    }

    @Test
    void atributosYConstructoresNoSonMetodos() {
        String clase = "class A { public: int x; A(int v); ~A(); int y = 3; void m(); }; ";
        assertTrue(analizar(clase + "void g() { A a; a.m(); a.x = 5; }").isEmpty());
    }

    @Test
    void cuerpoDeMetodoEnLaClase() {
        String clase = "class A { public: int doble(int n) { return n * 2; } void m(); }; ";
        assertTrue(analizar(clase + "void g() { A a; a.doble(4); a.m(); }").isEmpty());
        assertEquals(1, analizar(clase + "void g() { A a; a.doble(); }").size());
    }

    @Test
    void reportaLaPosicionDelMetodo() {
        ErrorSemantico error = analizar("class A { public: void m(); };\nvoid f() {\n    A a;\n    a.m(1);\n}")
                .getFirst();
        assertEquals("Llamada incorrecta a un método", error.nombre());
        assertEquals(4, error.linea());
        assertEquals(7, error.inicio());
        assertEquals(7, error.fin());
    }

    @Test
    void variosErrores() {
        assertEquals(3, analizarLlamadas("c.sumar(1); c.limpiar(2); c.saludar(3);").size());
    }

    @Test
    void archivoDeEjemplo() throws IOException {
        String codigo;
        try (InputStream entrada = getClass().getResourceAsStream("/llamadaMetodo.c")) {
            codigo = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<Integer> lineas = analizar(codigo).stream().map(ErrorSemantico::linea).toList();
        assertEquals(List.of(16, 17, 19, 21, 23), lineas);
    }

    @Test
    void buscarErroresIncluyeLlamadaMetodo() {
        List<ErrorSemantico> errores = ErrorSemantico.buscarErrores(
                new Lexer().analizar("class A { public: void m(); }; void f() { A a; a.m(1); }"));
        assertEquals(1, errores.size());
        assertEquals("Llamada incorrecta a un método", errores.getFirst().nombre());
    }
}
