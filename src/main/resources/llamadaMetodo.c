#include <stdio.h>

/* En C los "métodos" son miembros de un struct que apuntan a una función */

int sumar(int a, int b) { return a + b; }
double dividir(double a, double b) { return a / b; }
void limpiar(void) { }
void saludar(const char *nombre) { printf("Hola, %s\n", nombre); }
void registrar(const char *formato, ...) { }

struct Calculadora {
    int (*sumar)(int, int);
    double (*dividir)(double a, double b);
    void (*limpiar)(void);
};

typedef struct {
    void (*saludar)(const char *nombre);
    void (*registrar)(const char *formato, ...);
} Consola;

int main(void) {
    struct Calculadora calc = { sumar, dividir, limpiar };
    struct Calculadora *p = &calc;
    Consola consola = { saludar, registrar };

    calc.sumar(2, 3);                   /* correcto */
    calc.sumar(2);                      /* e1 falta un argumento */
    calc.limpiar(5);                    /* e2 limpiar no recibe argumentos */
    calc.dividir(8, 2.5);               /* correcto: el entero se convierte a double */
    calc.dividir(8.0, 2.0, 3.0);        /* e3 sobra un argumento */
    p->sumar(1, 2, 3);                  /* e4 sobra un argumento */
    p->limpiar();                       /* correcto */
    consola.saludar("hola");            /* correcto */
    consola.saludar(7);                 /* e5 se esperaba una cadena */
    consola.registrar("%d %d", 1, 2);   /* correcto: parámetros variables */
    consola.registrar();                /* e6 falta el formato */
    return 0;
}
