/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package gui;

import core.RelojGlobal;
import enums.ModoEjecucion;
import enums.ModoUsuario;
import enums.PoliticaDisco;
import enums.PoliticaPlanificacion;
import enums.ProcesoTipo;
import enums.TipoOperacionIO;
import estructuras.Cola;
import estructuras.ListaEnlazada;
import interfaces.IClockObserver;
import interfaces.IPoliticaDisco;
import io.ConfiguracionSimulacion;
import io.GestorJSON;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import kernel.*;
import metricas.GestorMetricas;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 *
 * @author Usuario
 */

public class VistaMonitoreo extends javax.swing.JFrame implements IClockObserver {
    
    private static final Color COLOR_BG = new Color(18, 24, 38);
    private static final Color COLOR_PANEL_BG = new Color(28, 36, 54);
    private static final Color COLOR_CARD_BG = new Color(38, 48, 70);
    private static final Color COLOR_TEXT_MAIN = new Color(240, 244, 250);
    private static final Color COLOR_TEXT_MUTED = new Color(160, 175, 200);
    private static final Color COLOR_ACCENT_BLUE = new Color(51, 153, 255);
    private static final Color COLOR_ACCENT_GREEN = new Color(46, 204, 113);
    private static final Color COLOR_ACCENT_ORANGE = new Color(230, 126, 34);
    private static final Color COLOR_ACCENT_PURPLE = new Color(155, 89, 182);
    
// Núcleo del Sistema Operativo (1 único Computador + Disco + SistemaArchivos)
    private ConfiguracionSimulacion configuracionActual;
    private Computador computador;
    private Disco disco;
    private SistemaArchivos sistemaArchivos;
    private GestorMetricas gestorMetricasGlobal;

    // Estado de seguimiento para Bitácora en tiempo real
    private int ultimoCabezalRegistrado = -1;
    private int ultimasCompletadasDisco = 0;
    private int ultimosSuspendidosCount = 0;

    // Controles Superiores y Telemetría
    private JLabel lblRelojCiclos;
    private JLabel lblEstadoSimulacion;
    private JLabel lblCpuInfo;
    private JLabel lblRamInfo;
    private JLabel lblSwapInfo;
    private JLabel lblCabezalInfo;

    private JButton btnIniciar;
    private JButton btnPausar;
    private JSpinner spinnerDuracionCiclo;
    private JComboBox<ModoUsuario> cbModoUsuario;
    private JComboBox<String> cbUsuarioActivo;
    private JComboBox<PoliticaPlanificacion> cbPoliticaCPU;
    private JComboBox<PoliticaDisco> cbPoliticaDisco;

    // Paneles Especializados (Días 2 y 3)
    private PanelSistemaArchivos panelFS;
    private PanelMapaDisco panelDisco;

    // Tablas de las 6 Colas de Procesos
    private DefaultTableModel modelNuevos;
    private DefaultTableModel modelListos;
    private DefaultTableModel modelBloqueados;
    private DefaultTableModel modelListosSusp;
    private DefaultTableModel modelBloqSusp;
    private DefaultTableModel modelTerminados;

    // Métricas en vivo (Disco, Swap y CPU)
    private JLabel lblDesplazamientoTotal;
    private JLabel lblSolicitudesAtendidas;
    private JLabel lblEsperaPromedioES;
    private JLabel lblRespuestaPromedioES;
    private JLabel lblUtilizacionCPU;
    private JLabel lblThroughput;
    private JLabel lblDeadlines;
    private JLabel lblIntegridadFS;

    // Gráficas JFreeChart (Día 4)
    private XYSeries serieCabezal;
    private DefaultCategoryDataset datasetPoliticas;

    // Bitácora de Eventos (Issue #14)
    private JTextArea txtLogEventos;

    public VistaMonitoreo() {
        initCustomUI();
    }

    public VistaMonitoreo(ConfiguracionSimulacion config) {
        this();
        inicializarSimulacion(config);
    }

    private void initCustomUI() {
        setTitle("ÁvilaOS - Dashboard de Simulación: Sistema de Archivos, Disco y Planificación");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1420, 880);
        setLocationRelativeTo(null);
        getContentPane().setBackground(COLOR_BG);
        getContentPane().setLayout(new BorderLayout(8, 8));

        // 1. HEADER SUPERIOR: CONTROLES, SELECTORES EN CALIENTE Y BARRA DE TELEMETRÍA
        getContentPane().add(crearPanelSuperior(), BorderLayout.NORTH);

        // 2. PANEL CENTRAL: PESTAÑAS DE TRABAJO
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(COLOR_PANEL_BG);
        tabs.setForeground(COLOR_TEXT_MAIN);
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 12));

        // Pestaña 1: Explorador de Archivos (JTree + Tabla Asignación) y Mapa de Bloques del Disco
        JSplitPane splitFSyDisco = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitFSyDisco.setResizeWeight(0.45);
        splitFSyDisco.setBackground(COLOR_BG);
        splitFSyDisco.setBorder(null);

        panelFS = new PanelSistemaArchivos();
        panelDisco = new PanelMapaDisco();
        splitFSyDisco.setLeftComponent(panelFS);
        splitFSyDisco.setRightComponent(panelDisco);
        tabs.addTab("Sistema de Archivos y Mapa de Disco", splitFSyDisco);

        // Pestaña 2: Las 6 Colas de Procesos (incluyendo Swap: Listo/Susp y Bloqueado/Susp)
        tabs.addTab("Colas de Procesos y Swap", crearPanelColas());

        // Pestaña 3: Métricas en Vivo y Gráficas JFreeChart (Día 4)
        tabs.addTab("Métricas de Disco, Cabezal y Políticas", crearPanelMetricasYGraficas());

        getContentPane().add(tabs, BorderLayout.CENTER);

        // 3. PANEL INFERIOR: BITÁCORA DE EVENTOS + BARRA DE ESTADO
        getContentPane().add(crearPanelInferiorLog(), BorderLayout.SOUTH);
    }

    private JPanel crearPanelSuperior() {
        JPanel contenedor = new JPanel(new BorderLayout(0, 4));
        contenedor.setBackground(COLOR_BG);

        // Fila 1: Botones de ejecución y selectores en tiempo real
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(COLOR_PANEL_BG);
        header.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JPanel pnlBotones = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        pnlBotones.setOpaque(false);

        btnIniciar = crearBoton("Iniciar", COLOR_ACCENT_GREEN);
        btnPausar = crearBoton("Pausar", COLOR_ACCENT_ORANGE);
        JButton btnReiniciar = crearBoton("Reiniciar", COLOR_CARD_BG);
        JButton btnProcManual = crearBoton("Proceso Manual", COLOR_ACCENT_PURPLE);
        JButton btnProcRandom = crearBoton("Proceso Aleatorio", COLOR_ACCENT_PURPLE);
        JButton btnExportar = crearBoton("Exportar JSON", COLOR_ACCENT_BLUE);

        btnIniciar.addActionListener(e -> {
            RelojGlobal.iniciar();
            lblEstadoSimulacion.setText("Estado: Simulación en ejecución...");
            lblEstadoSimulacion.setForeground(COLOR_ACCENT_GREEN);
            registrarLog(">>> RelojGlobal INICIADO.");
        });

        btnPausar.addActionListener(e -> {
            RelojGlobal.pausar();
            lblEstadoSimulacion.setText("Estado: Simulación Pausada.");
            lblEstadoSimulacion.setForeground(COLOR_ACCENT_ORANGE);
            registrarLog(">>> RelojGlobal PAUSADO en ciclo " + RelojGlobal.getCicloActual() + ".");
        });

        btnReiniciar.addActionListener(e -> {
            if (configuracionActual != null) {
                inicializarSimulacion(configuracionActual);
                registrarLog(">>> Simulación REINICIADA a Ciclo 0.");
            }
        });

        btnProcManual.addActionListener(e -> crearProcesoManual());
        btnProcRandom.addActionListener(e -> crearProcesoAleatorio());
        btnExportar.addActionListener(e -> exportarEstadoJSON());

        pnlBotones.add(btnIniciar);
        pnlBotones.add(btnPausar);
        pnlBotones.add(btnReiniciar);
        pnlBotones.add(btnProcManual);
        pnlBotones.add(btnProcRandom);
        pnlBotones.add(btnExportar);

        // Selectores en caliente (ModoUsuario, Usuario activo, Política CPU, Política Disco, Velocidad reloj)
        JPanel pnlControles = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        pnlControles.setOpaque(false);

        cbModoUsuario = new JComboBox<>(ModoUsuario.values());
        estilarCombo(cbModoUsuario);

        cbUsuarioActivo = new JComboBox<>(new String[]{"admin", "user1", "user2"});
        estilarCombo(cbUsuarioActivo);

        Runnable actualizarContextoUsuario = () -> {
            ModoUsuario modo = (ModoUsuario) cbModoUsuario.getSelectedItem();
            String user = (String) cbUsuarioActivo.getSelectedItem();
            if (modo == ModoUsuario.ADMINISTRADOR) {
                cbUsuarioActivo.setSelectedItem("admin");
                user = "admin";
            } else if ("admin".equals(user)) {
                cbUsuarioActivo.setSelectedItem("user1");
                user = "user1";
            }
            if (panelFS != null) {
                panelFS.setContextoUsuario(modo, user);
            }
            registrarLog("Contexto de seguridad cambiado -> Rol: " + modo + " | Usuario: " + user);
        };
        cbModoUsuario.addActionListener(e -> actualizarContextoUsuario.run());
        cbUsuarioActivo.addActionListener(e -> {
            if (panelFS != null) {
                panelFS.setContextoUsuario((ModoUsuario) cbModoUsuario.getSelectedItem(), (String) cbUsuarioActivo.getSelectedItem());
            }
        });

        cbPoliticaCPU = new JComboBox<>(PoliticaPlanificacion.values());
        estilarCombo(cbPoliticaCPU);
        cbPoliticaCPU.addActionListener(e -> {
            if (computador != null) {
                PoliticaPlanificacion pol = (PoliticaPlanificacion) cbPoliticaCPU.getSelectedItem();
                computador.setPoliticaPlanificacion(pol, computador.getQuantum());
                registrarLog("Política de CPU cambiada en caliente a: " + pol);
            }
        });

        cbPoliticaDisco = new JComboBox<>(PoliticaDisco.values());
        estilarCombo(cbPoliticaDisco);
        cbPoliticaDisco.addActionListener(e -> {
            if (disco != null) {
                PoliticaDisco pol = (PoliticaDisco) cbPoliticaDisco.getSelectedItem();
                disco.setPolitica(instanciarPoliticaDisco(pol));
                registrarLog("Política de Disco cambiada en caliente a: " + pol);
            }
        });

        spinnerDuracionCiclo = new JSpinner(new SpinnerNumberModel(300, 50, 5000, 50));
        spinnerDuracionCiclo.setPreferredSize(new Dimension(65, 25));
        spinnerDuracionCiclo.addChangeListener(e -> {
            int ms = (int) spinnerDuracionCiclo.getValue();
            RelojGlobal.setDuracionCicloMs(ms);
            registrarLog("Velocidad del reloj ajustada a " + ms + " ms/ciclo.");
        });

        pnlControles.add(crearLabelMuted("Rol:"));
        pnlControles.add(cbModoUsuario);
        pnlControles.add(crearLabelMuted("User:"));
        pnlControles.add(cbUsuarioActivo);
        pnlControles.add(crearLabelMuted("CPU:"));
        pnlControles.add(cbPoliticaCPU);
        pnlControles.add(crearLabelMuted("Disco:"));
        pnlControles.add(cbPoliticaDisco);
        pnlControles.add(crearLabelMuted("ms:"));
        pnlControles.add(spinnerDuracionCiclo);

        header.add(pnlBotones, BorderLayout.WEST);
        header.add(pnlControles, BorderLayout.EAST);

        // Fila 2: Barra rápida de telemetría del Hardware
        JPanel barraTelemetria = new JPanel(new GridLayout(1, 5, 10, 0));
        barraTelemetria.setBackground(COLOR_CARD_BG);
        barraTelemetria.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        lblRelojCiclos = crearLabelTelemetria("Ciclo Global: 0", COLOR_ACCENT_BLUE);
        lblCpuInfo = crearLabelTelemetria("CPU: IDLE (Modo USUARIO)", COLOR_ACCENT_GREEN);
        lblRamInfo = crearLabelTelemetria("RAM: 0 / 0 MB", COLOR_TEXT_MAIN);
        lblSwapInfo = crearLabelTelemetria("Swap: 0 / 0 bloques", COLOR_ACCENT_ORANGE);
        lblCabezalInfo = crearLabelTelemetria("Cabezal: Bloque 0 (↑)", COLOR_TEXT_MAIN);

        barraTelemetria.add(lblRelojCiclos);
        barraTelemetria.add(lblCpuInfo);
        barraTelemetria.add(lblRamInfo);
        barraTelemetria.add(lblSwapInfo);
        barraTelemetria.add(lblCabezalInfo);

        contenedor.add(header, BorderLayout.NORTH);
        contenedor.add(barraTelemetria, BorderLayout.SOUTH);
        return contenedor;
    }

    private JPanel crearPanelColas() {
        JPanel pnl = new JPanel(new GridLayout(2, 3, 8, 8));
        pnl.setBackground(COLOR_BG);
        pnl.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        modelNuevos = new DefaultTableModel(new Object[]{"ID", "Nombre", "Op. E/S", "Mem", "Deadline"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        modelListos = new DefaultTableModel(new Object[]{"ID", "Nombre", "Op. E/S", "Prio", "Deadline"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        modelBloqueados = new DefaultTableModel(new Object[]{"ID", "Nombre", "Motivo Bloqueo", "Archivo", "Deadline"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        modelListosSusp = new DefaultTableModel(new Object[]{"ID", "Nombre", "Op. E/S", "Mem", "Deadline"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        modelBloqSusp = new DefaultTableModel(new Object[]{"ID", "Nombre", "Motivo", "Mem", "Deadline"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        modelTerminados = new DefaultTableModel(new Object[]{"ID", "Nombre", "T. Modo SO", "Deadline", "Resultado"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        pnl.add(crearTarjetaCola("1. Cola de Nuevos (NEW)", modelNuevos, () -> computador != null ? computador.getColaNuevos() : null));
        pnl.add(crearTarjetaCola("2. Cola de Listos en RAM (READY)", modelListos, () -> computador != null ? computador.getColaListos() : null));
        pnl.add(crearTarjetaCola("3. Cola de Bloqueados E/S (BLOCKED)", modelBloqueados, () -> computador != null ? computador.getColaBloqueados() : null));
        pnl.add(crearTarjetaCola("4. Listos-Suspendidos en Swap (READY_SUSP)", modelListosSusp, () -> computador != null ? computador.getColaListosSuspendidos() : null));
        pnl.add(crearTarjetaCola("5. Bloqueados-Suspendidos en Swap (BLOCKED_SUSP)", modelBloqSusp, () -> computador != null ? computador.getColaBloqueadosSuspendidos() : null));
        pnl.add(crearTarjetaCola("6. Cola de Terminados (EXIT)", modelTerminados, () -> computador != null ? computador.getColaTerminados() : null));

        return pnl;
    }

    private interface ProveedorCola {
        Cola<Pcb> obtener();
    }

    private JScrollPane crearTarjetaCola(String titulo, DefaultTableModel model, ProveedorCola proveedor) {
        JTable tabla = new JTable(model);
        estilarTabla(tabla);
        tabla.setToolTipText("Doble clic sobre una fila para inspeccionar el PCB completo");

        tabla.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && tabla.getSelectedRow() != -1) {
                    String pid = String.valueOf(model.getValueAt(tabla.getSelectedRow(), 0));
                    Pcb seleccionado = buscarPcbEnCola(proveedor.obtener(), pid);
                    if (seleccionado != null) {
                        DialogoNuevoProceso.mostrarDetallePCB(VistaMonitoreo.this, seleccionado);
                    }
                }
            }
        });

        JScrollPane sp = new JScrollPane(tabla);
        sp.getViewport().setBackground(new Color(22, 30, 44));
        sp.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(COLOR_CARD_BG), titulo,
            0, 0, new Font("Segoe UI", Font.BOLD, 11), COLOR_ACCENT_BLUE));
        return sp;
    }

    private JPanel crearPanelMetricasYGraficas() {
        JPanel contenedor = new JPanel(new BorderLayout(10, 10));
        contenedor.setBackground(COLOR_BG);
        contenedor.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Panel superior con tarjetas numéricas de rendimiento
        JPanel pnlCards = new JPanel(new GridLayout(2, 4, 10, 8));
        pnlCards.setBackground(COLOR_PANEL_BG);
        pnlCards.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(COLOR_CARD_BG), "Indicadores de Rendimiento en Vivo (Disco, Swap y CPU)",
            0, 0, new Font("Segoe UI", Font.BOLD, 12), COLOR_ACCENT_BLUE));

        lblDesplazamientoTotal = crearLabelMetrica("Desplazamiento Cabezal: 0 pistas");
        lblSolicitudesAtendidas = crearLabelMetrica("Solicitudes E/S Atendidas: 0");
        lblEsperaPromedioES = crearLabelMetrica("Espera Prom. Cola E/S: 0.00 ciclos");
        lblRespuestaPromedioES = crearLabelMetrica("Respuesta Prom. E/S: 0.00 ciclos");
        lblUtilizacionCPU = crearLabelMetrica("Utilización CPU: 0.0%");
        lblThroughput = crearLabelMetrica("Throughput: 0.00 proc/ciclo");
        lblDeadlines = crearLabelMetrica("Cumplimiento Deadlines: 100.0%");
        lblIntegridadFS = crearLabelMetrica("Integridad Árbol-Disco: VERIFICADA");

        pnlCards.add(lblDesplazamientoTotal);
        pnlCards.add(lblSolicitudesAtendidas);
        pnlCards.add(lblEsperaPromedioES);
        pnlCards.add(lblRespuestaPromedioES);
        pnlCards.add(lblUtilizacionCPU);
        pnlCards.add(lblThroughput);
        pnlCards.add(lblDeadlines);
        pnlCards.add(lblIntegridadFS);

        contenedor.add(pnlCards, BorderLayout.NORTH);

        // Panel central con 2 Gráficas JFreeChart
        JPanel pnlGraficas = new JPanel(new GridLayout(1, 2, 12, 0));
        pnlGraficas.setOpaque(false);

        serieCabezal = new XYSeries("Bloque / Pista del Cabezal");
        XYSeriesCollection datasetXY = new XYSeriesCollection(serieCabezal);
        JFreeChart chartCabezal = ChartFactory.createXYLineChart(
            "Trayectoria del Cabezal en el Tiempo",
            "Ciclo de Reloj",
            "Bloque / Pista",
            datasetXY,
            PlotOrientation.VERTICAL,
            true, true, false
        );
        estilarXYChart(chartCabezal);

        datasetPoliticas = new DefaultCategoryDataset();
        JFreeChart chartPoliticas = ChartFactory.createBarChart(
            "Rendimiento Comparativo por Política de Disco",
            "Política de Planificación",
            "Cantidad",
            datasetPoliticas,
            PlotOrientation.VERTICAL,
            true, true, false
        );
        estilarBarChart(chartPoliticas);

        pnlGraficas.add(new ChartPanel(chartCabezal));
        pnlGraficas.add(new ChartPanel(chartPoliticas));

        contenedor.add(pnlGraficas, BorderLayout.CENTER);
        return contenedor;
    }

    private JPanel crearPanelInferiorLog() {
        JPanel pnlInferior = new JPanel(new BorderLayout(0, 2));
        pnlInferior.setBackground(COLOR_BG);

        txtLogEventos = new JTextArea(5, 80);
        txtLogEventos.setEditable(false);
        txtLogEventos.setBackground(new Color(14, 18, 28));
        txtLogEventos.setForeground(new Color(170, 230, 170));
        txtLogEventos.setFont(new Font("Consolas", Font.PLAIN, 11));

        JScrollPane spLog = new JScrollPane(txtLogEventos);
        spLog.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(COLOR_CARD_BG), "Bitácora de Eventos del Kernel (Cabezal, CRUD y Swap)",
            0, 0, new Font("Segoe UI", Font.BOLD, 11), COLOR_ACCENT_BLUE));

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        footer.setBackground(COLOR_PANEL_BG);
        lblEstadoSimulacion = new JLabel("Estado: Listo para iniciar simulación.");
        lblEstadoSimulacion.setForeground(COLOR_TEXT_MUTED);
        lblEstadoSimulacion.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        footer.add(lblEstadoSimulacion);

        pnlInferior.add(spLog, BorderLayout.CENTER);
        pnlInferior.add(footer, BorderLayout.SOUTH);
        return pnlInferior;
    }

    // =========================================================================
    // INICIALIZACIÓN Y SINCRONIZACIÓN CON EL NÚCLEO (DÍA 4)
    // =========================================================================

    public void inicializarSimulacion(ConfiguracionSimulacion config) {
        this.configuracionActual = config;
        RelojGlobal.reiniciar();

        if (config == null) return;

        int duracionCiclo = 300;
        String politicaDiscoStr = "FIFO";

        if (config.datosProcesos != null && config.datosProcesos.globalConfig != null) {
            ConfiguracionSimulacion.GlobalConfigJSON gc = config.datosProcesos.globalConfig;
            if (gc.cycleDurationMs > 0) duracionCiclo = gc.cycleDurationMs;
            if (gc.initialDiskPolicy != null) politicaDiscoStr = gc.initialDiskPolicy;
            if (gc.headSpeed != null && gc.headSpeed > 0) {
                config.velocidadCabezal = gc.headSpeed;
            }
        }

        spinnerDuracionCiclo.setValue(duracionCiclo);
        RelojGlobal.setDuracionCicloMs(duracionCiclo);

        gestorMetricasGlobal = new GestorMetricas();
        ListaEnlazada<BufferAcotado> buffersVacios = new ListaEnlazada<>();

// 1. Construir Disco y SistemaArchivos directamente desde los DTOs cargados
int totalBloques = (config.datosFS != null && config.datosFS.diskConfig != null && config.datosFS.diskConfig.totalBlocks > 0)
                ? config.datosFS.diskConfig.totalBlocks : 200;
        int swapBlocks = (config.datosProcesos != null && config.datosProcesos.globalConfig != null)
                ? Math.max(0, config.datosProcesos.globalConfig.swapSizeBlocks) : 0;

        // Ajustar capacidad si los archivos del JSON + Swap exceden totalBloques
        int maxBloqueArchivo = 0;
        if (config.datosFS != null && config.datosFS.fileSystem != null) {
            for (ConfiguracionSimulacion.NodoFSJSON nodo : config.datosFS.fileSystem) {
                if (nodo != null && "FILE".equalsIgnoreCase(nodo.type) && nodo.startBlock != null && nodo.blocks != null) {
                    int fin = nodo.startBlock + nodo.blocks;
                    if (fin > maxBloqueArchivo) {
                        maxBloqueArchivo = fin;
                    }
                }
            }
        }
        if (maxBloqueArchivo + swapBlocks > totalBloques) {
            totalBloques = maxBloqueArchivo + swapBlocks; // [0..199] Archivos, [200..299] Swap
        }

        int cabezalInicial = (config.datosFS != null && config.datosFS.diskConfig != null)
                ? Math.max(0, Math.min(config.datosFS.diskConfig.initialHead, totalBloques - 1)) : 50;
        int velocidad = Math.max(1, config.velocidadCabezal);

        PoliticaDisco polDiscoEnum = parsearPoliticaDisco(politicaDiscoStr);
        disco = new Disco(totalBloques, swapBlocks, velocidad, cabezalInicial, instanciarPoliticaDisco(polDiscoEnum));
        sistemaArchivos = new SistemaArchivos(disco);

        // Cargar directorios y archivos iniciales en el Sistema de Archivos
        if (config.datosFS != null && config.datosFS.fileSystem != null) {
            for (ConfiguracionSimulacion.NodoFSJSON nodo : config.datosFS.fileSystem) {
                if (nodo != null && "DIRECTORY".equalsIgnoreCase(nodo.type)) {
                    sistemaArchivos.cargarDirectorioJSON(nodo.path, nodo.owner, nodo.permissions);
                }
            }
            for (ConfiguracionSimulacion.NodoFSJSON nodo : config.datosFS.fileSystem) {
                if (nodo != null && "FILE".equalsIgnoreCase(nodo.type)) {
                    int start = (nodo.startBlock != null) ? nodo.startBlock : -1;
                    int blocks = (nodo.blocks != null) ? nodo.blocks : 1;
                    sistemaArchivos.cargarArchivoJSON(nodo.path, start, blocks, nodo.owner, nodo.permissions, "SYS_INIT");
                }
            }
        }

        // 2. Instanciar único Computador ("1") y vincular Disco y SistemaArchivos
        int ram = 1024;
        String politicaCpuStr = "FCFS";
        int quantum = 2;
        if (config.datosProcesos != null && config.datosProcesos.computer != null) {
            ConfiguracionSimulacion.ComputerConfigJSON cc = config.datosProcesos.computer;
            if (cc.ramCapacity > 0) ram = cc.ramCapacity;
            if (cc.policy != null) politicaCpuStr = cc.policy;
            if (cc.quantum > 0) quantum = cc.quantum;
        }

        computador = new Computador("1", ram, gestorMetricasGlobal, buffersVacios);
        computador.setDisco(disco);
        computador.setSistemaArchivos(sistemaArchivos);

        // Registrar Disco y luego VistaMonitoreo (Computador ya se registró en su constructor)
        RelojGlobal.registrarObservador(disco);
        RelojGlobal.registrarObservador(this);

        try {
            PoliticaPlanificacion polCPU = PoliticaPlanificacion.valueOf(politicaCpuStr.trim().toUpperCase());
            cbPoliticaCPU.setSelectedItem(polCPU);
            computador.setPoliticaPlanificacion(polCPU, quantum);
        } catch (IllegalArgumentException ex) {
            cbPoliticaCPU.setSelectedItem(PoliticaPlanificacion.FCFS);
            computador.setPoliticaPlanificacion(PoliticaPlanificacion.FCFS, quantum);
        }
        cbPoliticaDisco.setSelectedItem(parsearPoliticaDisco(politicaDiscoStr));

        // 3. Cargar Procesos iniciales
        if (config.datosProcesos != null && config.datosProcesos.processes != null) {
            for (ConfiguracionSimulacion.ProcessJSON cp : config.datosProcesos.processes) {
                if (cp == null) continue;
                int prio = (cp.priority != null) ? cp.priority : 1;
                int mem = Math.max(1, cp.memory);
                long dead = (cp.deadline > 0) ? cp.deadline : 500L;
                int inst = Math.max(1, cp.instructions);

                ProcesoTipo tipoProc = (cp.operation != null && !cp.operation.isEmpty())
                        ? ProcesoTipo.IO_BOUND
                        : ProcesoTipo.CPU_BOUND;

                Pcb pcb = new Pcb(cp.name, "1", tipoProc, prio, mem, dead, inst);

                boolean operacionValida = true;
                if (cp.operation != null && cp.targetPath != null) {
                    TipoOperacionIO opIO = mapearOperacionIO(cp.operation);
                    if (opIO != null) {
                        String err = sistemaArchivos.prepararOperacionCRUD(
                            pcb, opIO, cp.targetPath, 1, cp.name,
                            ModoUsuario.ADMINISTRADOR, "admin"
                        );
                        if (err != null) {
                            registrarLog("[ERROR Carga JSON] Proceso " + cp.name + " omitido: " + err);
                            operacionValida = false;
                        }
                    }
                }
                if (operacionValida) {
                    computador.admitirProceso(pcb);
                }
            }
        }

        // 4. Vincular Paneles Visuales con sus firmas exactas
        panelFS.vincularSistema(sistemaArchivos, computador, new PanelSistemaArchivos.ListenerEventosCRUD() {
            @Override
            public void onProcesoCRUDEncolado(Pcb pcb, String descripcion) {
                registrarLog(descripcion);
                actualizarVistasGUI();
            }

            @Override
            public void onOperacionRechazada(String motivo) {
                registrarLog("[CRUD RECHAZADO] " + motivo);
            }
        });
        panelFS.setContextoUsuario((ModoUsuario) cbModoUsuario.getSelectedItem(), (String) cbUsuarioActivo.getSelectedItem());
        panelDisco.setDisco(disco);

        serieCabezal.clear();
        serieCabezal.add(0, disco.getCabezalActual());
        datasetPoliticas.clear();

        ultimoCabezalRegistrado = disco.getCabezalActual();
        ultimasCompletadasDisco = 0;
        ultimosSuspendidosCount = contarTotalSuspendidos();

        actualizarVistasGUI();
        registrarLog("ÁvilaOS inicializado -> Disco: " + disco.getTotalBloques() +
                     " bloques (Swap: " + disco.getSwapSizeBlocks() + "), RAM: " + ram + " MB.");
    }

    @Override
    public void tick(int cicloActual) {
        SwingUtilities.invokeLater(() -> {
            registrarEventosDelCiclo(cicloActual);
            actualizarVistasGUI();
        });
    }

    private void registrarEventosDelCiclo(int cicloActual) {
        if (disco == null || computador == null) return;

        int cabezalNow = disco.getCabezalActual();
        if (cabezalNow != ultimoCabezalRegistrado) {
            String dir = disco.isHaciaArriba() ? "SUBIENDO ↑" : "BAJANDO ↓";
            registrarLog("[Ciclo " + cicloActual + "] Cabezal desplazado: Bloque " + ultimoCabezalRegistrado + " -> " + cabezalNow + " (" + dir + ")");
            ultimoCabezalRegistrado = cabezalNow;
        }

        int atendidasNow = disco.getTotalSolicitudesAtendidas();
        if (atendidasNow > ultimasCompletadasDisco) {
            registrarLog("[Ciclo " + cicloActual + "] Disco completó transferencia E/S (" + disco.getPolitica().getClass().getSimpleName() + "). Total atendidas: " + atendidasNow);
            ultimasCompletadasDisco = atendidasNow;
        }

        int suspNow = contarTotalSuspendidos();
        if (suspNow > ultimosSuspendidosCount) {
            registrarLog("[Ciclo " + cicloActual + "] SWAP OUT: Proceso suspendido hacia zona Swap en disco (Suspendidos: " + suspNow + ").");
        } else if (suspNow < ultimosSuspendidosCount) {
            registrarLog("[Ciclo " + cicloActual + "] SWAP IN: Proceso reactivado desde Swap hacia RAM (Suspendidos: " + suspNow + ").");
        }
        ultimosSuspendidosCount = suspNow;
    }

    private void actualizarVistasGUI() {
        int cicloActual = RelojGlobal.getCicloActual();
        lblRelojCiclos.setText("Ciclo Global: " + cicloActual);

        if (computador != null) {
            Cpu cpu = computador.getCpu();
            if (cpu != null && cpu.estaOcupada()) {
                Pcb p = cpu.getProcesoEnEjecucion();
                String modo = (cpu.getModo() == ModoEjecucion.SISTEMA_OPERATIVO) ? "KERNEL/SO" : "USUARIO";
                lblCpuInfo.setText("CPU: " + p.getNombre() + " [" + modo + "]");
                lblCpuInfo.setForeground(cpu.getModo() == ModoEjecucion.SISTEMA_OPERATIVO ? COLOR_ACCENT_ORANGE : COLOR_ACCENT_GREEN);
            } else {
                lblCpuInfo.setText("CPU: IDLE (Libre)");
                lblCpuInfo.setForeground(COLOR_TEXT_MUTED);
            }

            int ramUsada = computador.getMemoriaRAM().getMemoriaUsada();
            int ramTotal = computador.getMemoriaRAM().getCapacidadTotal();
            lblRamInfo.setText("RAM: " + ramUsada + " / " + ramTotal + " MB");

            refrescarTablaCola(modelNuevos, computador.getColaNuevos(),
                p -> new Object[]{p.getId(), p.getNombre(), p.getOperacionIO(), p.getMemoriaAsignada(), p.getDeadline()});
            refrescarTablaCola(modelListos, computador.getColaListos(),
                p -> new Object[]{p.getId(), p.getNombre(), p.getOperacionIO(), p.getPrioridad(), p.getDeadline()});
            refrescarTablaCola(modelBloqueados, computador.getColaBloqueados(),
                p -> new Object[]{p.getId(), p.getNombre(), p.getMotivoBloqueo(), p.getRutaArchivo(), p.getDeadline()});
            refrescarTablaCola(modelListosSusp, computador.getColaListosSuspendidos(),
                p -> new Object[]{p.getId(), p.getNombre(), p.getOperacionIO(), p.getMemoriaAsignada(), p.getDeadline()});
            refrescarTablaCola(modelBloqSusp, computador.getColaBloqueadosSuspendidos(),
                p -> new Object[]{p.getId(), p.getNombre(), p.getMotivoBloqueo(), p.getMemoriaAsignada(), p.getDeadline()});
            refrescarTablaCola(modelTerminados, computador.getColaTerminados(),
                p -> new Object[]{p.getId(),p.getNombre(),p.getTiempoModoSO(),p.getDeadline(), p.isDeadlineFalladoRegistrado() ? "DEADLINE_EXPIRADO" : "A TIEMPO"
                });
        }

        if (disco != null) {
            int swapTotal = disco.getSwapSizeBlocks();
            int swapUsados = swapTotal - disco.contarBloquesLibresSwap();
            lblSwapInfo.setText("Swap: " + swapUsados + " / " + swapTotal + " bloques");

            String flecha = disco.isHaciaArriba() ? "↑ SUBIENDO" : "↓ BAJANDO";
            lblCabezalInfo.setText("Cabezal: Bloque " + disco.getCabezalActual() + " (" + flecha + ")");

            lblDesplazamientoTotal.setText("Desplazamiento Cabezal: " + disco.getDesplazamientoTotal() + " pistas");
            lblSolicitudesAtendidas.setText("Solicitudes E/S Atendidas: " + disco.getTotalSolicitudesAtendidas());
            lblEsperaPromedioES.setText(String.format("Espera Prom. Cola E/S: %.2f ciclos", disco.getTiempoPromedioEsperaES()));
            lblRespuestaPromedioES.setText(String.format("Respuesta Prom. E/S: %.2f ciclos", disco.getTiempoPromedioRespuestaES()));

            serieCabezal.add(cicloActual, disco.getCabezalActual());

            String[] politicas = {"FIFO", "SSTF", "SCAN", "C-SCAN"};
            for (String pol : politicas) {
                datasetPoliticas.setValue(disco.getDesplazamientoPorPolitica(pol), "Pistas Recorridas", pol);
                datasetPoliticas.setValue(disco.getSolicitudesAtendidasPorPolitica(pol), "Solicitudes Atendidas", pol);
            }
        }

        if (gestorMetricasGlobal != null) {
            lblUtilizacionCPU.setText(String.format("Utilización CPU: %.1f%%", gestorMetricasGlobal.calcularUtilizacionCPU(cicloActual)));
            lblThroughput.setText(String.format("Throughput: %.2f proc/ciclo", gestorMetricasGlobal.calcularThroughput(cicloActual)));
            lblDeadlines.setText(String.format("Cumplimiento Deadlines: %.1f%%", gestorMetricasGlobal.calcularTasaCumplimientoDeadlines()));
        }

        if (sistemaArchivos != null) {
            boolean ok = sistemaArchivos.verificarIntegridadReferencial();
            lblIntegridadFS.setText("Integridad Árbol-Disco: " + (ok ? "VERIFICADA (OK)" : "DESINCRONIZADA"));
            lblIntegridadFS.setForeground(ok ? COLOR_ACCENT_GREEN : COLOR_ACCENT_ORANGE);
            panelFS.refrescarTodo();
        }

        if (panelDisco != null) {
            panelDisco.refrescar();
        }
    }

    // =========================================================================
    // MÉTODOS AUXILIARES Y ACCIONES DE USUARIO
    // =========================================================================

    private interface ExtractorFila {
        Object[] extraer(Pcb p);
    }

    private void refrescarTablaCola(DefaultTableModel model, Cola<Pcb> cola, ExtractorFila extractor) {
        model.setRowCount(0);
        if (cola == null || cola.esVacia()) return;

        Cola<Pcb> aux = new Cola<>();
        while (!cola.esVacia()) {
            Pcb p = cola.desencolar();
            model.addRow(extractor.extraer(p));
            aux.encolar(p);
        }
        while (!aux.esVacia()) {
            cola.encolar(aux.desencolar());
        }
    }

    private Pcb buscarPcbEnCola(Cola<Pcb> cola, String pid) {
        if (cola == null || cola.esVacia() || pid == null) return null;
        Pcb encontrado = null;
        Cola<Pcb> aux = new Cola<>();
        while (!cola.esVacia()) {
            Pcb p = cola.desencolar();
            if (p.getId().equals(pid)) encontrado = p;
            aux.encolar(p);
        }
        while (!aux.esVacia()) {
            cola.encolar(aux.desencolar());
        }
        return encontrado;
    }

    private int contarTotalSuspendidos() {
        if (computador == null) return 0;
        return contarElementosCola(computador.getColaListosSuspendidos())
             + contarElementosCola(computador.getColaBloqueadosSuspendidos());
    }

    private int contarElementosCola(Cola<Pcb> cola) {
        if (cola == null || cola.esVacia()) return 0;
        int count = 0;
        Cola<Pcb> aux = new Cola<>();
        while (!cola.esVacia()) {
            aux.encolar(cola.desencolar());
            count++;
        }
        while (!aux.esVacia()) {
            cola.encolar(aux.desencolar());
        }
        return count;
    }

    private void crearProcesoManual() {
        if (computador == null || sistemaArchivos == null) return;
        ModoUsuario modo = (ModoUsuario) cbModoUsuario.getSelectedItem();
        String user = (String) cbUsuarioActivo.getSelectedItem();

        Pcb creado = DialogoNuevoProceso.mostrarDialogoManual(this, computador, sistemaArchivos, modo, user);
        if (creado != null) {
            registrarLog("Proceso manual creado: " + creado.getId() + " (" + creado.getNombre() + ") admitido.");
            actualizarVistasGUI();
        }
    }

    private void crearProcesoAleatorio() {
        if (computador == null || sistemaArchivos == null) return;
        ModoUsuario modo = (ModoUsuario) cbModoUsuario.getSelectedItem();
        String user = (String) cbUsuarioActivo.getSelectedItem();

        Pcb rnd = DialogoNuevoProceso.generarProcesoAleatorio(computador, sistemaArchivos, modo, user);
        if (rnd != null) {
            registrarLog("Proceso aleatorio encolado: " + rnd.getId() + " (" + rnd.getNombre() + ").");
            actualizarVistasGUI();
        }
    }

    private void exportarEstadoJSON() {
        if (sistemaArchivos == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Guardar Estado del Sistema de Archivos y Disco (.json)");
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            String ruta = f.getAbsolutePath().endsWith(".json") ? f.getAbsolutePath() : f.getAbsolutePath() + ".json";
            try {
                new GestorJSON().guardarSistemaArchivosJSON(sistemaArchivos, ruta);
                registrarLog("Estado del sistema de archivos exportado a: " + ruta);
                JOptionPane.showMessageDialog(this, "Estado exportado exitosamente en:\n" + ruta, "Exportación JSON", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al exportar JSON:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private PoliticaDisco parsearPoliticaDisco(String nombre) {
        if (nombre == null) return PoliticaDisco.FIFO;
        String norm = nombre.trim().toUpperCase().replace("-", "_");
        try {
            return PoliticaDisco.valueOf(norm);
        } catch (IllegalArgumentException e) {
            return PoliticaDisco.FIFO;
        }
    }

    private IPoliticaDisco instanciarPoliticaDisco(PoliticaDisco pol) {
        if (pol == null) return new PoliticaDiscoFIFO();
        switch (pol) {
            case SSTF: return new PoliticaDiscoSSTF();
            case SCAN: return new PoliticaDiscoSCAN();
            case C_SCAN: return new PoliticaDiscoCSCAN();
            case FIFO:
            default: return new PoliticaDiscoFIFO();
        }
    }

    private TipoOperacionIO mapearOperacionIO(String op) {
        if (op == null) return null;
        switch (op.trim().toUpperCase()) {
            case "CREATE":
            case "CREAR": return TipoOperacionIO.CREAR;
            case "READ":
            case "LEER": return TipoOperacionIO.LEER;
            case "UPDATE":
            case "ACTUALIZAR": return TipoOperacionIO.ACTUALIZAR;
            case "DELETE":
            case "ELIMINAR": return TipoOperacionIO.ELIMINAR;
            default: return null;
        }
    }

    private void registrarLog(String mensaje) {
        txtLogEventos.append(mensaje + "\n");
        txtLogEventos.setCaretPosition(txtLogEventos.getDocument().getLength());
    }

    private void estilarXYChart(JFreeChart chart) {
        chart.setBackgroundPaint(COLOR_PANEL_BG);
        if (chart.getTitle() != null) chart.getTitle().setPaint(COLOR_TEXT_MAIN);
        if (chart.getLegend() != null) {
            chart.getLegend().setItemPaint(COLOR_TEXT_MAIN);
            chart.getLegend().setBackgroundPaint(COLOR_CARD_BG);
        }
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(new Color(15, 20, 30));
        plot.setDomainGridlinePaint(COLOR_CARD_BG);
        plot.setRangeGridlinePaint(COLOR_CARD_BG);
        plot.getDomainAxis().setLabelPaint(COLOR_TEXT_MAIN);
        plot.getDomainAxis().setTickLabelPaint(COLOR_TEXT_MAIN);
        plot.getRangeAxis().setLabelPaint(COLOR_TEXT_MAIN);
        plot.getRangeAxis().setTickLabelPaint(COLOR_TEXT_MAIN);
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, COLOR_ACCENT_BLUE);
        renderer.setSeriesStroke(0, new BasicStroke(2.2f));
        plot.setRenderer(renderer);
    }

    private void estilarBarChart(JFreeChart chart) {
        chart.setBackgroundPaint(COLOR_PANEL_BG);
        if (chart.getTitle() != null) chart.getTitle().setPaint(COLOR_TEXT_MAIN);
        if (chart.getLegend() != null) {
            chart.getLegend().setItemPaint(COLOR_TEXT_MAIN);
            chart.getLegend().setBackgroundPaint(COLOR_CARD_BG);
        }
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(new Color(15, 20, 30));
        plot.setRangeGridlinePaint(COLOR_CARD_BG);
        plot.getDomainAxis().setLabelPaint(COLOR_TEXT_MAIN);
        plot.getDomainAxis().setTickLabelPaint(COLOR_TEXT_MAIN);
        plot.getRangeAxis().setLabelPaint(COLOR_TEXT_MAIN);
        plot.getRangeAxis().setTickLabelPaint(COLOR_TEXT_MAIN);
    }

    private JButton crearBoton(String texto, Color bg) {
        JButton btn = new JButton(texto);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setFocusPainted(false);
        return btn;
    }

    private JLabel crearLabelMuted(String texto) {
        JLabel l = new JLabel(texto);
        l.setForeground(COLOR_TEXT_MUTED);
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        return l;
    }

    private JLabel crearLabelTelemetria(String texto, Color color) {
        JLabel l = new JLabel(texto);
        l.setForeground(color);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        return l;
    }

    private JLabel crearLabelMetrica(String texto) {
        JLabel l = new JLabel(texto);
        l.setForeground(COLOR_TEXT_MAIN);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        return l;
    }

    private void estilarCombo(JComboBox<?> combo) {
        combo.setBackground(COLOR_CARD_BG);
        combo.setForeground(COLOR_TEXT_MAIN);
        combo.setFont(new Font("Segoe UI", Font.BOLD, 11));
    }

    private void estilarTabla(JTable tabla) {
        tabla.setBackground(new Color(22, 30, 44));
        tabla.setForeground(COLOR_TEXT_MAIN);
        tabla.setGridColor(COLOR_CARD_BG);
        tabla.setRowHeight(21);
        tabla.getTableHeader().setBackground(COLOR_CARD_BG);
        tabla.getTableHeader().setForeground(COLOR_ACCENT_BLUE);
        tabla.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
    }

    public static void main(String args[]) {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        java.awt.EventQueue.invokeLater(() -> new VistaMonitoreo().setVisible(true));
    }


    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /**
     * @param args the command line arguments
     */

   
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
