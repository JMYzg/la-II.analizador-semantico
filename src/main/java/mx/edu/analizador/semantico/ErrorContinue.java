package mx.edu.analizador.semantico;

import mx.edu.analizador.lexico.TipoToken;
import mx.edu.analizador.lexico.Token;

import java.util.ArrayList;
import java.util.List;

public class ErrorContinue {

    public List<ErrorSemantico> analizar(List<Token> tokens) {
        List<ErrorSemantico> errores = new ArrayList<>();

        // si la llave es de un ciclo, false si es de un if normal
        List<Boolean> llaves = new ArrayList<>();
        boolean ciPendiente = false;


        int finciclosinllave = -1;

        for (int i = 0; i < tokens.size(); i++) {


            if (finciclosinllave != -1 && i > finciclosinllave) {
                finciclosinllave = -1;
            }

            Token token = tokens.get(i);

            // System.out.println("token actual: " + token.lexema());

            if (Palabra(token, "while") || Palabra(token, "for") || Palabra(token, "do")) {
                ciPendiente = true;
            }

            if (Palabra(token, "while") || Palabra(token, "for")) {

                int parentesis = 0;

                for (int j = i + 1; j < tokens.size(); j++) {

                    if (Simbolo(tokens.get(j), "(")) {
                        parentesis++;
                    }

                    if (Simbolo(tokens.get(j), ")")) {
                        parentesis--;
                    }

                    if (parentesis == 0) {

                        if (j + 1 < tokens.size()) {

                            Token siguiente = tokens.get(j + 1);

                            if (Simbolo(siguiente, "{")) {
                                // System.out.println("ciclo con llaves");
                            } else {
                                // System.out.println("ciclo sin llaves");

                                finciclosinllave = j + 1;
                                while (finciclosinllave < tokens.size() && !Simbolo(tokens.get(finciclosinllave), ";")) {
                                    finciclosinllave++;
                                }

                                ciPendiente = false;
                            }
                        }

                        break;
                    }
                }
            }
            else if (Simbolo(token, "{")) {
                llaves.add(ciPendiente);
                ciPendiente = false;
            }
            else if (Simbolo(token, "}")) {
                if (llaves.size() > 0) {
                    llaves.remove(llaves.size() - 1);
                }
            }
            else if (Palabra(token, "continue")) {

                if (!CicloAbierto(llaves) && !(finciclosinllave >= 0 && i <= finciclosinllave)) {
                    errores.add(new ErrorSemantico(
                            "Uso incorrecto de continue",
                            "Solo se puede usar dentro de un ciclo",
                            token.linea(), token.inicio(), token.fin()));
                }
            }
        }
        return errores;
    }

    private boolean CicloAbierto(List<Boolean> llaves) {
        for (int i = 0; i < llaves.size(); i++) {
            if (llaves.get(i)) {
                return true;
            }
        }
        return false;
    }

    private boolean Palabra(Token token, String lexema) {
        return token.tipo() == TipoToken.PALABRA_RESERVADA && token.lexema().equals(lexema);
    }

    private boolean Simbolo(Token token, String lexema) {
        return token.tipo() == TipoToken.SIMBOLO && token.lexema().equals(lexema);
    }
}