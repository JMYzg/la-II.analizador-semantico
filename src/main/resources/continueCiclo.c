#include <stdio.h>

int main(void) {
    int i;
    int x = 0;

    continue;                       /* e1 continue suelto en main */

    for (i = 0; i < 10; i++) {
        if (i % 2 == 0) continue;   /* correcto */
        printf("%d\n", i);
    }

    while (i > 0) {
        switch (i) {
            case 1:
                continue;           /* correcto el switch está dentro de un while */
            default:
                break;
        }
        i--;
    }

    do {
        i++;
        if (i < 3) continue;        /* correcto */
    } while (i < 5);

    for (i = 0; i < 3; i++)
        continue;                   /* correcto for con cuerpo de una sola sentencia */

    if (i > 0) {
        continue;                   /* e2 if sin ciclo */
    }

    switch (i) {
        case 0:
            continue;               /* e3 switch sin ciclo */
    }

    while (x < 10)
        continue;                   /* correcto while de una sola línea sin llaves */

    return 0;
}

void otra(void) {
    continue;                       /* e4 continue suelto en otra función */
}
