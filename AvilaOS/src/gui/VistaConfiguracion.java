/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package gui;

import enums.PoliticaDisco;
import enums.PoliticaPlanificacion;
import io.ConfiguracionSimulacion;
import io.GestorJSON;
import java.awt.*;
import java.io.File;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 *
 * @author Usuario
 */
public class VistaConfiguracion extends javax.swing.JFrame {

    private static final Color COLOR_BG = new Color(18, 24, 38);
    private static final Color COLOR_PANEL_BG = new Color(28, 36, 54);
    private static final Color COLOR_CARD_BG = new Color(38, 48, 70);
    private static final Color COLOR_TEXT_MAIN = new Color(240, 244, 250);
    private static final Color COLOR_ACCENT_BLUE = new Color(51, 153, 255);
    private static final Color COLOR_ACCENT_GREEN = new Color(46, 204, 113);
    private static final Color COLOR_ACCENT_RED = new Color(231, 76, 60);

    private ConfiguracionSimulacion.ArchivoSistemaArchivosJSON datosFS;
    private ConfiguracionSimulacion.ArchivoProcesosJSON datosProcesos;

    private JLabel lblEstadoFS;
    private JLabel lblEstadoProcesos;
    private JButton btnIniciarSimulacion;

    private JTextField txtTotalBloques;
    private JTextField txtSwapBloques;
    private JTextField txtCabezalInicial;
    private JTextField txtVelocidadCabezal;
    private JComboBox<PoliticaDisco> cbPoliticaDisco;

    private JTextField txtDuracionCiclo;
    private JTextField txtRamTotal;
    private JComboBox<PoliticaPlanificacion> cbPoliticaCPU;
    private JTextField txtQuantum;

    private JTable tblPreviewFS;
    private DefaultTableModel modelPreviewFS;
    private JTable tblPreviewProcesos;
    private DefaultTableModel modelPreviewProcesos;

    public VistaConfiguracion() {
        initComponents();
        getContentPane().removeAll();
        initCustomUI();
    }

    private void initCustomUI() {
        setTitle("ÁvilaOS - Vista 1: Configuración de Sistema de Archivos, Disco y Procesos");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1060, 720);
        setLocationRelativeTo(null);
        getContentPane().setBackground(COLOR_BG);
        getContentPane().setLayout(new BorderLayout(12, 12));

        JPanel pnlCarga = new JPanel(new GridLayout(1, 2, 12, 0));
        pnlCarga.setBackground(COLOR_BG);
        pnlCarga.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));

        JPanel cardFS = crearTarjeta("1. JSON Sistema de Archivos y Disco (Obligatorio)");
        JButton btnCargarFS = crearBoton("Cargar JSON Sistema de Archivos", COLOR_ACCENT_BLUE);
        lblEstadoFS = new JLabel("Estado: Pendiente por cargar");
        lblEstadoFS.setForeground(COLOR_ACCENT_RED);
        lblEstadoFS.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnCargarFS.addActionListener(e -> accionCargarFS());
        cardFS.add(btnCargarFS);
        cardFS.add(Box.createVerticalStrut(6));
        cardFS.add(lblEstadoFS);

        JPanel cardProc = crearTarjeta("2. JSON Procesos y Configuración (Obligatorio)");
        JButton btnCargarProc = crearBoton("Cargar JSON Procesos y Config", COLOR_ACCENT_BLUE);
        lblEstadoProcesos = new JLabel("Estado: Pendiente por cargar");
        lblEstadoProcesos.setForeground(COLOR_ACCENT_RED);
        lblEstadoProcesos.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnCargarProc.addActionListener(e -> accionCargarProcesos());
        cardProc.add(btnCargarProc);
        cardProc.add(Box.createVerticalStrut(6));
        cardProc.add(lblEstadoProcesos);

        pnlCarga.add(cardFS);
        pnlCarga.add(cardProc);
        getContentPane().add(pnlCarga, BorderLayout.NORTH);

        JPanel pnlCentro = new JPanel(new BorderLayout(10, 10));
        pnlCentro.setBackground(COLOR_BG);
        pnlCentro.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));

        JPanel pnlParams = new JPanel(new GridLayout(1, 2, 12, 0));
        pnlParams.setOpaque(false);

        JPanel pnlDiscoParams = crearTarjeta("Parámetros del Disco Simulado y Swap");
        pnlDiscoParams.setLayout(new GridLayout(5, 2, 8, 6));
        txtTotalBloques = crearCampoTexto("200");
        txtSwapBloques = crearCampoTexto("100");
        txtCabezalInicial = crearCampoTexto("50");
        txtVelocidadCabezal = crearCampoTexto("1");
        cbPoliticaDisco = new JComboBox<>(PoliticaDisco.values());
        estilarCombo(cbPoliticaDisco);

        agregarFilaForm(pnlDiscoParams, "Total de Bloques del Disco:", txtTotalBloques);
        agregarFilaForm(pnlDiscoParams, "Tamaño Área Swap (bloques):", txtSwapBloques);
        agregarFilaForm(pnlDiscoParams, "Posición Inicial del Cabezal:", txtCabezalInicial);
        agregarFilaForm(pnlDiscoParams, "Velocidad Cabezal (pistas/tick):", txtVelocidadCabezal);
        agregarFilaForm(pnlDiscoParams, "Política Inicial de Disco:", cbPoliticaDisco);

        JPanel pnlCompParams = crearTarjeta("Parámetros del Computador y Reloj");
        pnlCompParams.setLayout(new GridLayout(4, 2, 8, 6));
        txtDuracionCiclo = crearCampoTexto("300");
        txtRamTotal = crearCampoTexto("1024");
        cbPoliticaCPU = new JComboBox<>(PoliticaPlanificacion.values());
        estilarCombo(cbPoliticaCPU);
        txtQuantum = crearCampoTexto("2");

        agregarFilaForm(pnlCompParams, "Duración del Ciclo (ms):", txtDuracionCiclo);
        agregarFilaForm(pnlCompParams, "Capacidad RAM (unidades/bloques):", txtRamTotal);
        agregarFilaForm(pnlCompParams, "Política Planificación CPU:", cbPoliticaCPU);
        agregarFilaForm(pnlCompParams, "Quantum (Round Robin):", txtQuantum);

        pnlParams.add(pnlDiscoParams);
        pnlParams.add(pnlCompParams);
        pnlCentro.add(pnlParams, BorderLayout.NORTH);

        JPanel pnlTablas = new JPanel(new GridLayout(2, 1, 0, 8));
        pnlTablas.setOpaque(false);

        modelPreviewFS = new DefaultTableModel(
            new Object[]{"Ruta", "Tipo", "Bloque Inicio", "Cant. Bloques", "Dueño", "Permisos"}, 0
        );
        tblPreviewFS = new JTable(modelPreviewFS);
        estilarTabla(tblPreviewFS);
        JScrollPane spFS = new JScrollPane(tblPreviewFS);
        spFS.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(COLOR_CARD_BG), "Vista Previa: Sistema de Archivos",
            0, 0, new Font("Segoe UI", Font.BOLD, 12), COLOR_ACCENT_BLUE));

        modelPreviewProcesos = new DefaultTableModel(
            new Object[]{"Nombre", "Memoria", "Deadline", "Instrucciones", "Operación E/S", "Ruta Objetivo"}, 0
        );
        tblPreviewProcesos = new JTable(modelPreviewProcesos);
        estilarTabla(tblPreviewProcesos);
        JScrollPane spProc = new JScrollPane(tblPreviewProcesos);
        spProc.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(COLOR_CARD_BG), "Vista Previa: Procesos de E/S Cargados",
            0, 0, new Font("Segoe UI", Font.BOLD, 12), COLOR_ACCENT_BLUE));

        pnlTablas.add(spFS);
        pnlTablas.add(spProc);
        pnlCentro.add(pnlTablas, BorderLayout.CENTER);
        getContentPane().add(pnlCentro, BorderLayout.CENTER);

        JPanel pnlFooter = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 12));
        pnlFooter.setBackground(COLOR_PANEL_BG);

        btnIniciarSimulacion = new JButton("Iniciar Simulación de ÁvilaOS") {
    @Override
    public void setEnabled(boolean b) {
        super.setEnabled(b);
        if (b) {
            setBackground(COLOR_ACCENT_GREEN);
            setForeground(Color.BLACK); // O Color.WHITE según prefieras
        } else {
            setBackground(new Color(60, 68, 82));   // Fondo gris oscuro apagado
            setForeground(new Color(140, 148, 160)); // Texto gris claro
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
        g2.dispose();
        super.paintComponent(g);
    }
};
btnIniciarSimulacion.setContentAreaFilled(false);
btnIniciarSimulacion.setBorderPainted(false);
btnIniciarSimulacion.setFocusPainted(false);
btnIniciarSimulacion.setFont(new Font("Segoe UI", Font.BOLD, 12));
btnIniciarSimulacion.setPreferredSize(new Dimension(300, 38));
btnIniciarSimulacion.setEnabled(false);
btnIniciarSimulacion.addActionListener(e -> accionIniciarSimulacion());

        pnlFooter.add(btnIniciarSimulacion);
        getContentPane().add(pnlFooter, BorderLayout.SOUTH);
    }

    private void accionCargarFS() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Seleccionar JSON del Sistema de Archivos (.json)");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File archivo = chooser.getSelectedFile();
            try {
                GestorJSON gestor = new GestorJSON();
                datosFS = gestor.cargarSistemaArchivosJSON(archivo.getAbsolutePath());

                txtTotalBloques.setText(String.valueOf(datosFS.diskConfig.totalBlocks));
                txtCabezalInicial.setText(String.valueOf(datosFS.diskConfig.initialHead));

                modelPreviewFS.setRowCount(0);
                if (datosFS.fileSystem != null) {
                    for (ConfiguracionSimulacion.NodoFSJSON n : datosFS.fileSystem) {
                        if (n != null) {
                            modelPreviewFS.addRow(new Object[]{
                                n.path, n.type,
                                (n.startBlock != null) ? n.startBlock : "-",
                                (n.blocks != null) ? n.blocks : "-",
                                n.owner, n.permissions
                            });
                        }
                    }
                }

                lblEstadoFS.setText("Cargado: " + archivo.getName());
                lblEstadoFS.setForeground(COLOR_ACCENT_GREEN);
                verificarHabilitacionInicio();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al leer JSON de Sistema de Archivos:\n" + ex.getMessage(),
                    "Error JSON", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void accionCargarProcesos() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Seleccionar JSON de Procesos y Configuración (.json)");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File archivo = chooser.getSelectedFile();
            try {
                GestorJSON gestor = new GestorJSON();
                datosProcesos = gestor.cargarProcesosJSON(archivo.getAbsolutePath());

                txtDuracionCiclo.setText(String.valueOf(datosProcesos.globalConfig.cycleDurationMs));
                txtSwapBloques.setText(String.valueOf(datosProcesos.globalConfig.swapSizeBlocks));
                if (datosProcesos.globalConfig.headSpeed != null) {
                    txtVelocidadCabezal.setText(String.valueOf(datosProcesos.globalConfig.headSpeed));
                }

                if (datosProcesos.globalConfig.initialDiskPolicy != null) {
                    String polNorm = datosProcesos.globalConfig.initialDiskPolicy.trim().toUpperCase().replace("-", "_");
                    try {
                        cbPoliticaDisco.setSelectedItem(PoliticaDisco.valueOf(polNorm));
                    } catch (IllegalArgumentException ignored) {}
                }

                txtRamTotal.setText(String.valueOf(datosProcesos.computer.ramCapacity));
                txtQuantum.setText(String.valueOf(datosProcesos.computer.quantum));
                if (datosProcesos.computer.policy != null) {
                    String cpuPol = datosProcesos.computer.policy.trim().toUpperCase();
                    try {
                        cbPoliticaCPU.setSelectedItem(PoliticaPlanificacion.valueOf(cpuPol));
                    } catch (IllegalArgumentException ignored) {}
                }

                modelPreviewProcesos.setRowCount(0);
                if (datosProcesos.processes != null) {
                    for (ConfiguracionSimulacion.ProcessJSON p : datosProcesos.processes) {
                        if (p != null) {
                            modelPreviewProcesos.addRow(new Object[]{
                                p.name, p.memory, p.deadline, p.instructions, p.operation, p.targetPath
                            });
                        }
                    }
                }

                lblEstadoProcesos.setText("Cargado: " + archivo.getName());
                lblEstadoProcesos.setForeground(COLOR_ACCENT_GREEN);
                verificarHabilitacionInicio();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al leer JSON de Procesos:\n" + ex.getMessage(),
                    "Error JSON", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void verificarHabilitacionInicio() {
        btnIniciarSimulacion.setEnabled(datosFS != null && datosProcesos != null);
    }

    private void accionIniciarSimulacion() {
        if (datosFS == null || datosProcesos == null) {
            JOptionPane.showMessageDialog(this,
                "Debe cargar obligatoriamente ambos archivos JSON antes de iniciar.",
                "Archivos Faltantes", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!ValidadorCampos.validarEnteroRango(txtTotalBloques, "Total de Bloques", 20, 2000)) return;
        int totalBloques = Integer.parseInt(txtTotalBloques.getText().trim());

        if (!ValidadorCampos.validarEnteroRango(txtSwapBloques, "Tamaño Área Swap", 1, totalBloques - 10)) return;
        if (!ValidadorCampos.validarEnteroRango(txtCabezalInicial, "Posición Inicial del Cabezal", 0, totalBloques - 1)) return;
        if (!ValidadorCampos.validarEnteroRango(txtVelocidadCabezal, "Velocidad del Cabezal", 1, totalBloques)) return;
        if (!ValidadorCampos.validarEnteroRango(txtDuracionCiclo, "Duración del Ciclo (ms)", 50, 10000)) return;
        if (!ValidadorCampos.validarEnteroRango(txtRamTotal, "Capacidad RAM", 16, 65536)) return;
        if (!ValidadorCampos.validarEnteroRango(txtQuantum, "Quantum", 0, 100)) return;

        datosFS.diskConfig.totalBlocks = totalBloques;
        datosFS.diskConfig.initialHead = Integer.parseInt(txtCabezalInicial.getText().trim());

        datosProcesos.globalConfig.swapSizeBlocks = Integer.parseInt(txtSwapBloques.getText().trim());
        datosProcesos.globalConfig.cycleDurationMs = Integer.parseInt(txtDuracionCiclo.getText().trim());
        datosProcesos.globalConfig.initialDiskPolicy = ((PoliticaDisco) cbPoliticaDisco.getSelectedItem()).name();

        datosProcesos.computer.ramCapacity = Integer.parseInt(txtRamTotal.getText().trim());
        datosProcesos.computer.policy = ((PoliticaPlanificacion) cbPoliticaCPU.getSelectedItem()).name();
        datosProcesos.computer.quantum = Integer.parseInt(txtQuantum.getText().trim());

        ConfiguracionSimulacion configUnificada = new ConfiguracionSimulacion();
        configUnificada.datosFS = datosFS;
        configUnificada.datosProcesos = datosProcesos;
        configUnificada.velocidadCabezal = Integer.parseInt(txtVelocidadCabezal.getText().trim());

        VistaMonitoreo monitoreo = new VistaMonitoreo(configUnificada);
        monitoreo.setVisible(true);
    }

    private JPanel crearTarjeta(String titulo) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(COLOR_PANEL_BG);
        p.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(COLOR_CARD_BG), titulo,
            0, 0, new Font("Segoe UI", Font.BOLD, 12), COLOR_ACCENT_BLUE));
        return p;
    }

    private void agregarFilaForm(JPanel panel, String etiqueta, JComponent campo) {
        JLabel lbl = new JLabel(etiqueta);
        lbl.setForeground(COLOR_TEXT_MAIN);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        panel.add(lbl);
        panel.add(campo);
    }

    private JTextField crearCampoTexto(String valorInicial) {
        JTextField txt = new JTextField(valorInicial);
        txt.setBackground(new Color(15, 20, 30));
        txt.setForeground(COLOR_TEXT_MAIN);
        txt.setCaretColor(COLOR_ACCENT_BLUE);
        txt.setFont(new Font("Segoe UI", Font.BOLD, 12));
        txt.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(COLOR_CARD_BG, 1),
            BorderFactory.createEmptyBorder(3, 6, 3, 6)
        ));
        return txt;
    }

    private void estilarCombo(JComboBox<?> combo) {
        combo.setBackground(new Color(15, 20, 30));
        combo.setForeground(COLOR_TEXT_MAIN);
        combo.setFont(new Font("Segoe UI", Font.BOLD, 12));
    }

    private JButton crearBoton(String texto, Color bg) {
        JButton btn = new JButton(texto);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setFocusPainted(false);
        return btn;
    }

    private void estilarTabla(JTable tabla) {
        tabla.setBackground(new Color(22, 30, 44));
        tabla.setForeground(COLOR_TEXT_MAIN);
        tabla.setGridColor(COLOR_CARD_BG);
        tabla.setRowHeight(22);
        tabla.getTableHeader().setBackground(COLOR_PANEL_BG);
        tabla.getTableHeader().setForeground(COLOR_ACCENT_BLUE);
        tabla.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // </editor-fold>
@SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 1024, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 720, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            ex.printStackTrace();
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(() -> new VistaConfiguracion().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}

