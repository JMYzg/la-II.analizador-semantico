package mx.edu.analizador.interfaz;

import mx.edu.analizador.LectorC;
import mx.edu.analizador.lexico.Lexer;
import mx.edu.analizador.lexico.Token;
//9999999999999999999999999999999999
import mx.edu.analizador.semantico.ErrorContinue;
import mx.edu.analizador.semantico.ErrorSemantico;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Ventana principal del analizador: muestra la tabla de símbolos a la izquierda
 * (una fila por cada Token) y el código del archivo .c a la derecha.
 */
public class VentanaPrincipal extends JFrame {

    // Casos de errores semánticos. Por ahora solo se muestran: su análisis es otro entregable.
    private static final String[] ERRORES_SEMANTICOS = {
            "1 - Variable no declarada",
            "2 - Variable fuera de alcance (scope)",
            "3 - Asignación de tipos incompatibles",
            "4 - Argumentos incorrectos en una función",
            "5 - Tipos incorrectos en una función",
            "6 - Función no declarada",
            "7 - Uso incorrecto de return",
            "8 - Uso incorrecto de break",
            "9 - Uso incorrecto de continue",
            "10 - Conversión de tipos de control",
            "11 - Operación entre tipos incompatibles",
            "12 - Miembro inexistente de una clase",
            "13 - Llamada incorrecta a un método",
            "14 - Objeto de tipo incorrecto",
            "16 - Redefinición / conflicto de identificadores",
            "17 - Referencia incorrecta",
            "18 - Uso incorrecto de const",
            "19 - Modificar una expresión no modificable",
            "20 - Acceso a arreglo con tipo incorrecto"
    };

    private final DefaultTableModel modelo;
    private final JTextArea areaCodigo;
    private final JLabel etiquetaArchivo;
    //99999999999999999999999999para los errores
    private final DefaultTableModel modeloErrores;

    public VentanaPrincipal() {
        setTitle("Analizador Léxico - Tabla de Símbolos");
        setSize(1100, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Botón para abrir el archivo .c y combo de errores semánticos
        JButton botonAbrir = new JButton("Abrir archivo .c");
        botonAbrir.addActionListener(e -> elegirArchivo());

        etiquetaArchivo = new JLabel("Ningún archivo seleccionado");

        JComboBox<String> comboErrores = new JComboBox<>(ERRORES_SEMANTICOS);
        comboErrores.setPreferredSize(new Dimension(380, 28));
        comboErrores.setMaximumRowCount(12);

        JPanel panelSuperior = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
        panelSuperior.add(botonAbrir);
        panelSuperior.add(etiquetaArchivo);
        panelSuperior.add(comboErrores);
        add(panelSuperior, BorderLayout.NORTH);

        // Tabla de símbolos
        String[] columnas = {"Lexema", "Tipo", "Línea", "Inicio", "Fin"};
        modelo = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable tabla = new JTable(modelo);
        tabla.setRowHeight(22);
        tabla.setAutoCreateRowSorter(true);
        tabla.getColumnModel().getColumn(0).setPreferredWidth(150);
        tabla.getColumnModel().getColumn(1).setPreferredWidth(150);
        tabla.getColumnModel().getColumn(2).setPreferredWidth(60);
        tabla.getColumnModel().getColumn(3).setPreferredWidth(60);
        tabla.getColumnModel().getColumn(4).setPreferredWidth(60);

        // Área donde se muestra el código C
        areaCodigo = new JTextArea();
        areaCodigo.setEditable(false);
        areaCodigo.setFont(new Font("Monospaced", Font.PLAIN, 13));

        // Tabla a la izquierda y código a la derecha
        JSplitPane divisor = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(tabla),
                new JScrollPane(areaCodigo)
        );
        divisor.setDividerLocation(550);
        divisor.setResizeWeight(0.5);
        add(divisor, BorderLayout.CENTER);

        //999999999999999999999999999999999999999999999 Errores semánticos encontrados en el archivo

        // tabla de errores semanticos
        String[] columnasErrores = {"Error", "Descripción", "Línea", "Inicio", "Fin"};
        modeloErrores = new DefaultTableModel(columnasErrores, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable tablaErrores = new JTable(modeloErrores);
        tablaErrores.setRowHeight(22);
        tablaErrores.getColumnModel().getColumn(0).setPreferredWidth(180);
        tablaErrores.getColumnModel().getColumn(1).setPreferredWidth(400);
        tablaErrores.getColumnModel().getColumn(2).setPreferredWidth(60);
        tablaErrores.getColumnModel().getColumn(3).setPreferredWidth(60);
        tablaErrores.getColumnModel().getColumn(4).setPreferredWidth(60);

        JScrollPane panelErrores = new JScrollPane(tablaErrores);
        panelErrores.setPreferredSize(new Dimension(0, 140));
        panelErrores.setBorder(BorderFactory.createTitledBorder("Errores semánticos"));
        add(panelErrores, BorderLayout.SOUTH);

        ///////////////999999999999
    }

    private void elegirArchivo() {
        JFileChooser selector = new JFileChooser();
        selector.setFileFilter(new FileNameExtensionFilter("Código C (*.c)", "c"));

        File ejemplos = new File("src/main/resources");
        if (ejemplos.isDirectory()) {
            selector.setCurrentDirectory(ejemplos);
        }

        if (selector.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            mostrarArchivo(selector.getSelectedFile());
        }
    }

    // Lee el archivo, muestra su código y llena la tabla con sus tokens
    private void mostrarArchivo(File archivo) {
        modelo.setRowCount(0);
        areaCodigo.setText(""); //9999999999999999999999999999999
        modeloErrores.setRowCount(0);
        etiquetaArchivo.setText(archivo.getName());

        try {
            String codigo = LectorC.leer(archivo.getPath());
            areaCodigo.setText(codigo);
            areaCodigo.setCaretPosition(0);

            List<Token> tokens = new Lexer().analizar(codigo);
            for (Token token : tokens) {
                modelo.addRow(new Object[]{
                        token.lexema(),
                        token.tipo(),
                        token.linea(),
                        token.inicio(),
                        token.fin()
                });
            }

            // Error 99999999999999999999999999999999
            // Error 9 - Uso incorrecto de continue
            List<ErrorSemantico> errores = new ErrorContinue().analizar(tokens);
            for (int i = 0; i < errores.size(); i++) {
                ErrorSemantico error = errores.get(i);
                modeloErrores.addRow(new Object[]{
                        error.getNombre(),
                        error.getDescripcion(),
                        error.getLinea(),
                        error.getInicio(),
                        error.getFin()
                });

            }///////////////////////////
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error al leer el archivo: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        } catch (IllegalStateException e) {
            JOptionPane.showMessageDialog(this, "Error léxico: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}