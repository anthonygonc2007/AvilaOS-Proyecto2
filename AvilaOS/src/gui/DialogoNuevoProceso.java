/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package gui;
import enums.ModoUsuario;
import enums.ProcesoTipo;
import enums.TipoOperacionIO;
import java.awt.*;
import javax.swing.*;
import kernel.Computador;
import kernel.Pcb;
import kernel.SistemaArchivos;

/**
 *
 * @author Usuario
 */
public class DialogoNuevoProceso {
    private static int contadorManual = 1;
    private static int contadorRandom = 1;

    /**
     * Abre un diálogo modal para crear y encolar un proceso nuevo en tiempo de ejecución.
     */
    public static Pcb mostrarDialogoManual(Component parent, Computador computador, SistemaArchivos fs, ModoUsuario modoUsuario, String usuarioActivo) {
        JTextField txtNombre = new JTextField("Proc_Manual_" + contadorManual);
        JComboBox<String> cbOperacion = new JComboBox<>(new String[]{
            "SIN_IO (CPU_BOUND)", "LEER", "CREAR", "ACTUALIZAR", "ELIMINAR"
        });
        JTextField txtRuta = new JTextField("/usuarios/notas.txt");
        JTextField txtTamBloques = new JTextField("4");
        JTextField txtNuevoNombre = new JTextField("renombrado.txt");
        JTextField txtMemoria = new JTextField("32");
        JTextField txtPrioridad = new JTextField("2");
        JTextField txtDeadline = new JTextField("500");
        JTextField txtInstrucciones = new JTextField("2");

        JPanel panel = new JPanel(new GridLayout(9, 2, 6, 6));
        panel.add(new JLabel("Nombre del Proceso:"));
        panel.add(txtNombre);
        panel.add(new JLabel("Tipo / Operación E/S:"));
        panel.add(cbOperacion);
        panel.add(new JLabel("Ruta Objetivo (FS):"));
        panel.add(txtRuta);
        panel.add(new JLabel("Bloques (solo CREAR, 0=Dir):"));
        panel.add(txtTamBloques);
        panel.add(new JLabel("Nuevo Nombre (solo ACTUALIZAR):"));
        panel.add(txtNuevoNombre);
        panel.add(new JLabel("Memoria RAM (bloques):"));
        panel.add(txtMemoria);
        panel.add(new JLabel("Prioridad (1=Alta .. 10=Baja):"));
        panel.add(txtPrioridad);
        panel.add(new JLabel("Deadline (ciclos):"));
        panel.add(txtDeadline);
        panel.add(new JLabel("Instrucciones CPU:"));
        panel.add(txtInstrucciones);

        int res = JOptionPane.showConfirmDialog(
            parent, panel, "Añadir Proceso en Ejecución",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        );

        if (res != JOptionPane.OK_OPTION) {
            return null;
        }

        if (!ValidadorCampos.validarTextoNoVacio(txtNombre, "Nombre")) return null;
        if (!ValidadorCampos.validarEnteroRango(txtMemoria, "Memoria RAM", 1, computador.getMemoriaRAM().getCapacidadTotal())) return null;
        if (!ValidadorCampos.validarEnteroRango(txtPrioridad, "Prioridad", 1, 20)) return null;
        if (!ValidadorCampos.validarEnteroRango(txtDeadline, "Deadline", 10, 100000)) return null;
        if (!ValidadorCampos.validarEnteroRango(txtInstrucciones, "Instrucciones", 1, 1000)) return null;

        String nombre = txtNombre.getText().trim();
        int mem = Integer.parseInt(txtMemoria.getText().trim());
        int prio = Integer.parseInt(txtPrioridad.getText().trim());
        long deadline = Long.parseLong(txtDeadline.getText().trim());
        int inst = Integer.parseInt(txtInstrucciones.getText().trim());
        String opSeleccionada = (String) cbOperacion.getSelectedItem();

        if ("SIN_IO (CPU_BOUND)".equals(opSeleccionada)) {
            Pcb pcb = new Pcb(nombre, computador.getId(), ProcesoTipo.CPU_BOUND, prio, mem, deadline, inst);
            contadorManual++;
            computador.admitirProceso(pcb);
            return pcb;
        }

        if (!ValidadorCampos.validarTextoNoVacio(txtRuta, "Ruta Objetivo")) return null;
        int tamBloques = 1;
        if ("CREAR".equals(opSeleccionada)) {
            if (!ValidadorCampos.validarEnteroRango(txtTamBloques, "Bloques a Crear", 0, 200)) return null;
            tamBloques = Integer.parseInt(txtTamBloques.getText().trim());
        }

        TipoOperacionIO opIO = TipoOperacionIO.valueOf(opSeleccionada);
        Pcb pcb = new Pcb(nombre, computador.getId(), ProcesoTipo.IO_BOUND, prio, mem, deadline, inst);
        String error = fs.prepararOperacionCRUD(
            pcb, opIO, txtRuta.getText().trim(), tamBloques,
            txtNuevoNombre.getText().trim(), modoUsuario, usuarioActivo
        );

        if (error != null) {
            JOptionPane.showMessageDialog(parent, error, "Operación Rechazada", JOptionPane.WARNING_MESSAGE);
            return null;
        }

        contadorManual++;
        computador.admitirProceso(pcb);
        return pcb;
    }

    /**
     * Genera e inserta un proceso aleatorio válido en colaNuevos.
     */
    public static Pcb generarProcesoAleatorio(Computador computador, SistemaArchivos fs,
                                              ModoUsuario modoUsuario, String usuarioActivo) {
        String[] rutasLectura = {
            "/sistema/config.sys", "/sistema/boot.bin", "/sistema/core.dll",
            "/sistema/script.py", "/usuarios/video.mp4", "/usuarios/data.csv"
        };

        int idx = (int) (Math.random() * (rutasLectura.length + 2));
        int mem = 16 + ((int) (Math.random() * 4)) * 16; // 16, 32, 48 o 64
        int prio = 1 + (int) (Math.random() * 5);
        long deadline = 300 + (int) (Math.random() * 500);
        int inst = 2 + (int) (Math.random() * 4);
        String nombre = "Rand_" + (contadorRandom++);

        Pcb pcb = new Pcb(nombre, computador.getId(), ProcesoTipo.IO_BOUND, prio, mem, deadline, inst);

        if (idx < rutasLectura.length) {
            String ruta = rutasLectura[idx];
            String err = fs.prepararOperacionCRUD(pcb, TipoOperacionIO.LEER, ruta, 1, null, modoUsuario, usuarioActivo);
            if (err == null) {
                computador.admitirProceso(pcb);
                return pcb;
            }
        } else {
            String rutaNueva = "/usuarios/auto_" + contadorRandom + ".dat";
            int bloques = 2 + (int) (Math.random() * 4);
            String err = fs.prepararOperacionCRUD(pcb, TipoOperacionIO.CREAR, rutaNueva, bloques, null, modoUsuario, usuarioActivo);
            if (err == null) {
                computador.admitirProceso(pcb);
                return pcb;
            }
        }

        // proceso CPU_BOUND si no hubo espacio en disco
        Pcb cpuPcb = new Pcb(nombre + "_CPU", computador.getId(), ProcesoTipo.CPU_BOUND, prio, mem, deadline, inst);
        computador.admitirProceso(cpuPcb);
        return cpuPcb;
    }

    /**
     * Muestra en un cuadro de diálogo todos los campos del PCB
     */
    public static void mostrarDetallePCB(Component parent, Pcb p) {
        if (p == null) return;
        String info =
            "=== PCB Avila OS ===\n\n" +
            "ID Proceso:           " + p.getId() + "\n" +
            "Nombre:               " + p.getNombre() + "\n" +
            "Estado (7 Estados):   " + p.getEstado() + "\n" +
            "Tipo:                 " + p.getTipo() + "\n" +
            "Prioridad:            " + p.getPrioridad() + "\n" +
            "Memoria Asignada:     " + p.getMemoriaAsignada() + " bloques\n" +
            "Program Counter (PC): " + p.getProgramCounter() + "\n" +
            "MAR:                  " + p.getMemoryAddressRegister() + "\n" +
            "Instr. Restantes:     " + p.getTiempoRestanteEjecucion() + "\n" +
            "Deadline Restante:    " + p.getDeadline() + "\n" +
            "Tiempo en Modo SO:    " + p.getTiempoModoSO() + " ciclos\n" +
            "Motivo de Bloqueo:    " + (p.getMotivoBloqueo().isEmpty() ? "Ninguno" : p.getMotivoBloqueo()) + "\n" +
            "----------------------------------------------\n" +
            "Operación E/S:        " + (p.getOperacionIO() != null ? p.getOperacionIO() : "N/A") + "\n" +
            "Ruta Objetivo:        " + (p.getRutaArchivo().isEmpty() ? "N/A" : p.getRutaArchivo()) + "\n" +
            "Bloque Objetivo:      " + p.getBloqueObjetivo() + "\n" +
            "Cantidad de Bloques:  " + p.getCantidadBloques();

        JOptionPane.showMessageDialog(parent, info, "Detalle PCB - " + p.getId(), JOptionPane.INFORMATION_MESSAGE);
    }
}
