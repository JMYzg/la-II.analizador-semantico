package mx.edu.analizador.semantico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import mx.edu.analizador.lexico.Lexer;
import org.junit.jupiter.api.Test;

class ErrorContinueTest {

    private List<ErrorSemantico> analizar(String codigo) {
        return new ErrorContinue().analizar(new Lexer().analizar(codigo));
    }

    private void assertSinErrores(String codigo) {
        assertTrue(analizar(codigo).isEmpty(), codigo);
    }

    private void assertUnError(String codigo) {
        assertEquals(1, analizar(codigo).size(), codigo);
    }

    @Test
    void continueDentroDeCiclosConLlaves() {
        assertSinErrores("void f() { for (;;) { continue; } }");
        assertSinErrores("void f() { while (x) { if (y) continue; } }");
        assertSinErrores("void f() { do { continue; } while (x); }");
    }

    @Test
    void continueDentroDeCiclosSinLlaves() {
        assertSinErrores("void f() { for (;;) continue; }");
        assertSinErrores("void f() { while (x) continue; }");
        assertSinErrores("void f() { do continue; while (x); }");
        assertSinErrores("void f() { for (;;) while (x) continue; }");
    }

    @Test
    void cuerpoSinLlavesConBloqueDentro() {
        assertSinErrores("void f() { for (;;) if (a) { x++; continue; } }");
        assertSinErrores("void f() { for (;;) if (a) x = 1; else { y = 2; continue; } }");
    }

    @Test
    void switchDentroDeCicloEsValido() {
        assertSinErrores("void f() { while (x) { switch (x) { case 1: continue; default: break; } } }");
    }

    @Test
    void continueFueraDeCiclo() {
        assertUnError("void f() { continue; }");
        assertUnError("void f() { if (x) { continue; } }");
        assertUnError("void f() { switch (x) { case 0: continue; } }");
    }

    @Test
    void continueDespuesDeQueTerminaElCiclo() {
        assertUnError("void f() { for (;;) x++; continue; }");
        assertUnError("void f() { while (x) { x--; } continue; }");
        assertUnError("void f() { do { x++; } while (x); continue; }");
    }

    @Test
    void continueEnOtraFuncion() {
        assertUnError("void f() { for (;;) { } } void g() { continue; }");
    }

    @Test
    void ignoraInicializadoresYStructs() {
        assertSinErrores("struct P { int x; }; int a[] = {1, 2}; void f() { for (;;) { int b[] = {3}; continue; } }");
    }

    @Test
    void reportaLaPosicionDelContinue() {
        ErrorSemantico error = analizar("void f() {\n    continue;\n}").getFirst();
        assertEquals("Uso incorrecto de continue", error.nombre());
        assertEquals(2, error.linea());
        assertEquals(5, error.inicio());
        assertEquals(12, error.fin());
    }

    @Test
    void archivoDeEjemplo() throws IOException {
        String codigo;
        try (InputStream entrada = getClass().getResourceAsStream("/continueCiclo.c")) {
            codigo = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<Integer> lineas = analizar(codigo).stream().map(ErrorSemantico::linea).toList();
        assertEquals(List.of(7, 33, 38, 48), lineas);
    }

    @Test
    void buscarErroresIncluyeContinue() {
        assertEquals(1, ErrorSemantico.buscarErrores(new Lexer().analizar("void f() { continue; }")).size());
    }
}
