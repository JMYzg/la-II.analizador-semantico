#include <stdio.h>

class Calculadora {
public:
    int sumar(int a, int b);
    double dividir(double a, double b = 1.0);
    void limpiar();
    void saludar(const char *nombre);
};

int main(void) {
    Calculadora calc;
    Calculadora *p;

    calc.sumar(2, 3);
    calc.sumar(2);
    calc.limpiar(5);
    calc.dividir(8.0);
    calc.dividir(8.0, 2.0, 3.0);
    calc.saludar("hola");
    calc.saludar(7);
    p->limpiar();
    p->sumar(1, 2, 3);
    calc.noExiste(1);
    return 0;
}
