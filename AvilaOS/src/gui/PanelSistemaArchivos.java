/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package gui;
import enums.ModoUsuario;
import enums.ProcesoTipo;
import enums.TipoOperacionIO;
import estructuras.ListaEnlazada;
import java.awt.*;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import kernel.Archivo;
import kernel.Computador;
import kernel.Directorio;
import kernel.NodoFS;
import kernel.Pcb;
import kernel.SistemaArchivos;

/**
 *
 * @author Usuario
 */
    public class PanelSistemaArchivos extends JPanel {
    public interface ListenerEventosCRUD {
    void onProcesoCRUDEncolado(Pcb pcb, String descripcion);
    void onOperacionRechazada(String motivo);
    }
    
    private static final Color COLOR_PANEL_BG = new Color(28, 36, 54);
    private static final Color COLOR_CARD_BG = new Color(38, 48, 70);
    private static final Color COLOR_TEXT_MAIN = new Color(240, 244, 250);
    private static final Color COLOR_ACCENT_BLUE = new Color(51, 153, 255);
    private static final Color COLOR_ACCENT_GREEN = new Color(46, 204, 113);
    private static final Color COLOR_ACCENT_ORANGE = new Color(230, 126, 34);
    private static final Color COLOR_ACCENT_RED = new Color(231, 76, 60);

    private SistemaArchivos sistemaArchivos;
    private Computador computador;
    private ModoUsuario modoUsuarioActivo = ModoUsuario.ADMINISTRADOR;
    private String usuarioActivo = "user1";
    private ListenerEventosCRUD listener;

    private JTree arbolFS;
    private DefaultTreeModel treeModel;
    private JLabel lblMetaNombre;
    private JLabel lblMetaRuta;
    private JLabel lblMetaTamano;
    private JLabel lblMetaPrimerBloque;
    private JLabel lblMetaDuenoPermisos;

    private JTable tblAsignacion;
    private DefaultTableModel modelAsignacion;

    public PanelSistemaArchivos() {
        initUI();
    }

    public void vincularSistema(SistemaArchivos fs, Computador comp, ListenerEventosCRUD listener) {
        this.sistemaArchivos = fs;
        this.computador = comp;
        this.listener = listener;
        refrescarTodo();
    }

    public void setContextoUsuario(ModoUsuario modo, String usuario) {
        this.modoUsuarioActivo = modo;
        this.usuarioActivo = (usuario != null && !usuario.trim().isEmpty()) ? usuario.trim() : "user1";
    }

    private void initUI() {
        setLayout(new GridLayout(2, 1, 0, 8));
        setBackground(new Color(18, 24, 38));

        // 1. PANEL SUPERIOR: JTree + Metadatos + Botones CRUD
        JPanel pnlArbolYCRUD = new JPanel(new BorderLayout(6, 6));
        pnlArbolYCRUD.setBackground(COLOR_PANEL_BG);
        pnlArbolYCRUD.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(COLOR_CARD_BG),
            "Sistema de Archivos Jerárquico (JTree) & Operaciones CRUD",
            0, 0, new Font("Segoe UI", Font.BOLD, 12), COLOR_ACCENT_BLUE));

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("/");
        treeModel = new DefaultTreeModel(rootNode);
        arbolFS = new JTree(treeModel);
        arbolFS.setBackground(new Color(22, 30, 44));
        arbolFS.setForeground(COLOR_TEXT_MAIN);
        arbolFS.addTreeSelectionListener(e -> actualizarPanelMetadatos());

        JScrollPane spTree = new JScrollPane(arbolFS);
        pnlArbolYCRUD.add(spTree, BorderLayout.CENTER);

        // Metadatos del nodo seleccionado
        JPanel pnlMeta = new JPanel(new GridLayout(5, 1, 2, 2));
        pnlMeta.setBackground(new Color(22, 30, 44));
        pnlMeta.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(COLOR_CARD_BG), "Metadatos",
            0, 0, new Font("Segoe UI", Font.BOLD, 11), COLOR_ACCENT_BLUE));
        pnlMeta.setPreferredSize(new Dimension(235, 120));

        lblMetaNombre = crearLabelMeta("Nombre: /");
        lblMetaRuta = crearLabelMeta("Ruta: /");
        lblMetaTamano = crearLabelMeta("Tamaño: 0 bloques");
        lblMetaPrimerBloque = crearLabelMeta("Primer Bloque: -");
        lblMetaDuenoPermisos = crearLabelMeta("Dueño: admin (public)");

        pnlMeta.add(lblMetaNombre);
        pnlMeta.add(lblMetaRuta);
        pnlMeta.add(lblMetaTamano);
        pnlMeta.add(lblMetaPrimerBloque);
        pnlMeta.add(lblMetaDuenoPermisos);
        pnlArbolYCRUD.add(pnlMeta, BorderLayout.EAST);

        // Barra inferior de botones CRUD (instancia PCBs de E/S)
        JPanel pnlBotonesCRUD = new JPanel(new GridLayout(1, 4, 6, 0));
        pnlBotonesCRUD.setOpaque(false);

        JButton btnCrear = crearBoton("Crear", COLOR_ACCENT_GREEN);
        JButton btnLeer = crearBoton("Leer", COLOR_ACCENT_BLUE);
        JButton btnActualizar = crearBoton("Renombrar", COLOR_ACCENT_ORANGE);
        JButton btnEliminar = crearBoton("Eliminar", COLOR_ACCENT_RED);

        btnCrear.addActionListener(e -> accionCRUD(TipoOperacionIO.CREAR));
        btnLeer.addActionListener(e -> accionCRUD(TipoOperacionIO.LEER));
        btnActualizar.addActionListener(e -> accionCRUD(TipoOperacionIO.ACTUALIZAR));
        btnEliminar.addActionListener(e -> accionCRUD(TipoOperacionIO.ELIMINAR));

        pnlBotonesCRUD.add(btnCrear);
        pnlBotonesCRUD.add(btnLeer);
        pnlBotonesCRUD.add(btnActualizar);
        pnlBotonesCRUD.add(btnEliminar);
        pnlArbolYCRUD.add(pnlBotonesCRUD, BorderLayout.SOUTH);

        // 2. PANEL INFERIOR: TABLA DE ASIGNACIÓN DE ARCHIVOS
        JPanel pnlTabla = new JPanel(new BorderLayout());
        pnlTabla.setBackground(COLOR_PANEL_BG);
        pnlTabla.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(COLOR_CARD_BG),
            "Tabla de Asignación de Archivos (Asignación Encadenada)",
            0, 0, new Font("Segoe UI", Font.BOLD, 12), COLOR_ACCENT_BLUE));

        modelAsignacion = new DefaultTableModel(
            new Object[]{"Archivo", "Bloques", "1er Bloque", "Dueño", "Proceso Creador", "Color"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tblAsignacion = new JTable(modelAsignacion);
        tblAsignacion.setBackground(new Color(22, 30, 44));
        tblAsignacion.setForeground(COLOR_TEXT_MAIN);
        tblAsignacion.setGridColor(COLOR_CARD_BG);
        tblAsignacion.setRowHeight(22);
        tblAsignacion.getTableHeader().setBackground(COLOR_CARD_BG);
        tblAsignacion.getTableHeader().setForeground(COLOR_ACCENT_BLUE);
        tblAsignacion.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));

        // Renderizador visual para la columna de Color
        tblAsignacion.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                JLabel cell = (JLabel) super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
                cell.setOpaque(true);
                try {
                    cell.setBackground(Color.decode(String.valueOf(value)));
                } catch (Exception ex) {
                    cell.setBackground(COLOR_ACCENT_BLUE);
                }
                return cell;
            }
        });
        pnlTabla.add(new JScrollPane(tblAsignacion), BorderLayout.CENTER);
        add(pnlArbolYCRUD);
        add(pnlTabla);
    }

    private void accionCRUD(TipoOperacionIO operacion) {
        if (sistemaArchivos == null || computador == null) return;

        NodoFS seleccionado = obtenerNodoSeleccionado();
        String rutaBase = (seleccionado != null) ? seleccionado.getRutaCompleta() : "/";

        String rutaObjetivo = rutaBase;
        int tamanoBloques = 1;
        String nuevoNombre = null;

        if (operacion == TipoOperacionIO.CREAR) {
            String dirPadre = (seleccionado != null && !seleccionado.isDirectorio())
                ? (seleccionado.getPadre() != null ? seleccionado.getPadre().getRutaCompleta() : "/")
                : rutaBase;

            JTextField txtNombre = new JTextField("nuevo_archivo.txt");
            JComboBox<String> cbTipo = new JComboBox<>(new String[]{"ARCHIVO", "DIRECTORIO"});
            JTextField txtBloques = new JTextField("4");

            JPanel form = new JPanel(new GridLayout(3, 2, 6, 6));
            form.add(new JLabel("Directorio Padre: " + dirPadre));
            form.add(new JLabel(""));
            form.add(new JLabel("Tipo:"));
            form.add(cbTipo);
            form.add(new JLabel("Nombre:"));
            form.add(txtNombre);

            JPanel wrapper = new JPanel(new BorderLayout(0, 6));
            wrapper.add(form, BorderLayout.NORTH);
            JPanel pnlB = new JPanel(new GridLayout(1, 2, 6, 6));
            pnlB.add(new JLabel("Tamaño en Bloques (Archivo):"));
            pnlB.add(txtBloques);
            wrapper.add(pnlB, BorderLayout.SOUTH);

            if (JOptionPane.showConfirmDialog(this, wrapper, "Crear Elemento en " + dirPadre,
                    JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
                return;
            }

            if (!ValidadorCampos.validarTextoNoVacio(txtNombre, "Nombre")) return;
            String nom = txtNombre.getText().trim();
            if ("DIRECTORIO".equals(cbTipo.getSelectedItem())) {
                tamanoBloques = 0;
            } else {
                if (!ValidadorCampos.validarEnteroRango(txtBloques, "Tamaño en Bloques", 1, 200)) return;
                tamanoBloques = Integer.parseInt(txtBloques.getText().trim());
            }
            rutaObjetivo = "/".equals(dirPadre) ? "/" + nom : dirPadre + "/" + nom;

        } else if (operacion == TipoOperacionIO.ACTUALIZAR) {
            if (seleccionado == null || "/".equals(rutaBase)) {
                JOptionPane.showMessageDialog(this, "Seleccione un archivo o directorio válido para renombrar.",
                    "Selección Requerida", JOptionPane.WARNING_MESSAGE);
                return;
            }
            nuevoNombre = JOptionPane.showInputDialog(this, "Nuevo nombre para '" + seleccionado.getNombre() + "':",
                seleccionado.getNombre());
            if (nuevoNombre == null || nuevoNombre.trim().isEmpty()) return;

        } else {
            if (seleccionado == null || "/".equals(rutaBase)) {
                JOptionPane.showMessageDialog(this, "Seleccione un elemento del árbol primero.",
                    "Selección Requerida", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        // Instanciar PCB de E/S y delegar validación + preparación al SistemaArchivos
        Pcb pcbIO = new Pcb(
            "IO_" + operacion.name(),
            computador.getId(),
            ProcesoTipo.IO_BOUND,
            2,
            16,
            600,
            2
        );

        String error = sistemaArchivos.prepararOperacionCRUD(
            pcbIO, operacion, rutaObjetivo, tamanoBloques, nuevoNombre, modoUsuarioActivo, usuarioActivo
        );

        if (error != null) {
            JOptionPane.showMessageDialog(this, error, "Permiso / Validación", JOptionPane.WARNING_MESSAGE);
            if (listener != null) listener.onOperacionRechazada(error);
            return;
        }

        computador.admitirProceso(pcbIO);
        if (listener != null) {
            listener.onProcesoCRUDEncolado(
                pcbIO,
                "Proceso " + pcbIO.getId() + " (" + operacion + " -> " + rutaObjetivo + ") admitido en colaNuevos."
            );
        }
    }

    public void refrescarTodo() {
        if (sistemaArchivos == null) return;
        refrescarArbol();
        refrescarTablaAsignacion();
        actualizarPanelMetadatos();
    }

    private void refrescarArbol() {
        NodoFS previo = obtenerNodoSeleccionado();
        String rutaPrevia = (previo != null) ? previo.getRutaCompleta() : null;

        Directorio raiz = sistemaArchivos.getRaiz();
        DefaultMutableTreeNode rootTreeNode = construirNodoVisual(raiz);
        treeModel.setRoot(rootTreeNode);

        for (int i = 0; i < arbolFS.getRowCount(); i++) {
            arbolFS.expandRow(i);
        }

        if (rutaPrevia != null) {
            seleccionarPorRuta(rootTreeNode, rutaPrevia);
        }
    }

    private DefaultMutableTreeNode construirNodoVisual(NodoFS nodoFS) {
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(new WrapperNodoFS(nodoFS));
        if (nodoFS.isDirectorio()) {
            Directorio dir = (Directorio) nodoFS;
            ListaEnlazada<NodoFS> hijos = dir.getContenido();
            for (int i = 0; i < hijos.getTamano(); i++) {
                treeNode.add(construirNodoVisual(hijos.obtener(i)));
            }
        }
        return treeNode;
    }

    private void seleccionarPorRuta(DefaultMutableTreeNode actual, String rutaBuscada) {
        Object uo = actual.getUserObject();
        if (uo instanceof WrapperNodoFS) {
            if (((WrapperNodoFS) uo).nodo.getRutaCompleta().equals(rutaBuscada)) {
                arbolFS.setSelectionPath(new javax.swing.tree.TreePath(actual.getPath()));
                return;
            }
        }
        for (int i = 0; i < actual.getChildCount(); i++) {
            seleccionarPorRuta((DefaultMutableTreeNode) actual.getChildAt(i), rutaBuscada);
        }
    }

    private void refrescarTablaAsignacion() {
        modelAsignacion.setRowCount(0);
        ListaEnlazada<Archivo> tabla = sistemaArchivos.getTablaAsignacion();
        for (int i = 0; i < tabla.getTamano(); i++) {
            Archivo a = tabla.obtener(i);
            modelAsignacion.addRow(new Object[]{
                a.getRutaCompleta(),
                a.getTamano(),
                a.getPrimerBloque(),
                a.getOwner(),
                a.getProcesoCreador(),
                a.getColorHex()
            });
        }
    }

    private void actualizarPanelMetadatos() {
        NodoFS nodo = obtenerNodoSeleccionado();
        if (nodo == null && sistemaArchivos != null) {
            nodo = sistemaArchivos.getRaiz();
        }
        if (nodo == null) return;

        lblMetaNombre.setText("Nombre: " + nodo.getNombre());
        lblMetaRuta.setText("Ruta: " + nodo.getRutaCompleta());
        lblMetaTamano.setText("Tamaño: " + nodo.getTamano() + " bloques");
        lblMetaDuenoPermisos.setText("Dueño: " + nodo.getOwner() + " (" + nodo.getPermisosTexto() + ")");
        if (!nodo.isDirectorio()) {
            lblMetaPrimerBloque.setText("Primer Bloque: " + ((Archivo) nodo).getPrimerBloque());
        } else {
            lblMetaPrimerBloque.setText("Tipo: Directorio");
        }
    }

    private NodoFS obtenerNodoSeleccionado() {
        DefaultMutableTreeNode sel = (DefaultMutableTreeNode) arbolFS.getLastSelectedPathComponent();
        if (sel != null && sel.getUserObject() instanceof WrapperNodoFS) {
            return ((WrapperNodoFS) sel.getUserObject()).nodo;
        }
        return null;
    }

    private JLabel crearLabelMeta(String texto) {
        JLabel l = new JLabel(texto);
        l.setForeground(COLOR_TEXT_MAIN);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        return l;
    }

    private JButton crearBoton(String texto, Color bg) {
        JButton btn = new JButton(texto);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setFocusPainted(false);
        return btn;
    }

    private static class WrapperNodoFS {
        final NodoFS nodo;
        WrapperNodoFS(NodoFS nodo) { this.nodo = nodo; }

        @Override
        public String toString() {
            if (nodo.isDirectorio()) {
                return "[DIR] " + nodo.getNombre();
            }
            return nodo.getNombre() + " (" + nodo.getTamano() + " blq)";
        }
    }
}
 
