/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package gui;
import java.awt.*;
import javax.swing.*;
import kernel.Bloque;
import kernel.Disco;

/**
 *
 * @author Usuario
 */
public class PanelMapaDisco extends JPanel {
    private static final Color COLOR_PANEL_BG = new Color(28, 36, 54);
    private static final Color COLOR_CARD_BG = new Color(38, 48, 70);
    private static final Color COLOR_TEXT_MAIN = new Color(240, 244, 250);
    private static final Color COLOR_ACCENT_BLUE = new Color(51, 153, 255);

    private Disco disco;
    private JLabel lblInfoCabezal;
    private JLabel lblInfoParticion;
    private CuadriculaBloquesPanel panelCuadricula;

    public PanelMapaDisco() {
        initUI();
    }

    public void setDisco(Disco disco) {
        this.disco = disco;
        refrescar();
    }

    private void initUI() {
        setLayout(new BorderLayout(6, 6));
        setBackground(COLOR_PANEL_BG);
        setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(COLOR_CARD_BG),
            "Mapa de Bloques del Disco (SD), Zona Swap y Cabezal",
            0, 0, new Font("Segoe UI", Font.BOLD, 12), COLOR_ACCENT_BLUE));

        JPanel pnlHeader = new JPanel(new GridLayout(2, 1, 2, 2));
        pnlHeader.setOpaque(false);

        lblInfoCabezal = new JLabel("Cabezal: Bloque 0 | Dirección: ↑ (Mayores) | Desplazamiento: 0");
        lblInfoCabezal.setForeground(COLOR_TEXT_MAIN);
        lblInfoCabezal.setFont(new Font("Segoe UI", Font.BOLD, 12));

        lblInfoParticion = new JLabel("Zona Archivos: [0..99] | Zona Swap: [100..199]");
        lblInfoParticion.setForeground(new Color(160, 175, 200));
        lblInfoParticion.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        pnlHeader.add(lblInfoCabezal);
        pnlHeader.add(lblInfoParticion);
        add(pnlHeader, BorderLayout.NORTH);

        panelCuadricula = new CuadriculaBloquesPanel();
        JScrollPane scroll = new JScrollPane(panelCuadricula);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        // Leyenda inferior
        JPanel pnlLeyenda = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        pnlLeyenda.setOpaque(false);
        pnlLeyenda.add(crearItemLeyenda("Libre (FS)", new Color(34, 45, 65)));
        pnlLeyenda.add(crearItemLeyenda("Archivo (Color FAT)", new Color(46, 204, 113)));
        pnlLeyenda.add(crearItemLeyenda("Swap Libre", new Color(65, 52, 32)));
        pnlLeyenda.add(crearItemLeyenda("Swap Ocupado", new Color(231, 76, 60)));
        pnlLeyenda.add(crearItemLeyenda("Cabezal Activo", Color.YELLOW));
        add(pnlLeyenda, BorderLayout.SOUTH);
    }

    public void refrescar() {
        if (disco == null) return;
        String dir = disco.isHaciaArriba() ? "↑ (Hacia bloques mayores)" : "↓ (Hacia bloques menores)";
        String estadoReq = (disco.getSolicitudActiva() != null)
            ? (disco.isEnTransferencia() ? "TRANSFIRIENDO" : "MOVIENDO")
            : "INACTIVO";

        lblInfoCabezal.setText(String.format(
            "Cabezal: Bloque %d | Dir: %s | Estado: %s | Desplazamiento: %d",
            disco.getCabezalActual(), dir, estadoReq, disco.getDesplazamientoTotal()
        ));

        lblInfoParticion.setText(String.format(
            "Archivos: [0 .. %d] (%d libres) | Swap: [%d .. %d] (%d libres)",
            disco.getLimiteArchivos() - 1, disco.contarBloquesLibresArchivos(),
            disco.getLimiteArchivos(), disco.getTotalBloques() - 1, disco.contarBloquesLibresSwap()
        ));

        panelCuadricula.revalidate();
        panelCuadricula.repaint();
    }

    private JLabel crearItemLeyenda(String texto, Color color) {
        JLabel lbl = new JLabel("■ " + texto);
        lbl.setForeground(color);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        return lbl;
    }

    private class CuadriculaBloquesPanel extends JPanel {
        private static final int COLS = 20;
        private static final int CELL_W = 36;
        private static final int CELL_H = 26;

        CuadriculaBloquesPanel() {
            setBackground(new Color(15, 20, 30));
            setToolTipText("");
        }

        @Override
        public Dimension getPreferredSize() {
            int total = (disco != null) ? disco.getTotalBloques() : 200;
            int rows = (int) Math.ceil((double) total / COLS);
            return new Dimension(COLS * CELL_W + 10, rows * CELL_H + 10);
        }

        @Override
        public String getToolTipText(java.awt.event.MouseEvent event) {
            if (disco == null) return null;
            int col = (event.getX() - 4) / CELL_W;
            int row = (event.getY() - 4) / CELL_H;
            if (col < 0 || col >= COLS || row < 0) return null;
            int idx = row * COLS + col;
            Bloque b = disco.getBloque(idx);
            if (b == null) return null;

            String zona = disco.esBloqueDeSwap(idx) ? "SWAP" : "ARCHIVOS";
            if (!b.isOcupado()) {
                return "Bloque #" + idx + " [" + zona + "] - LIBRE";
            }
            return "Bloque #" + idx + " [" + zona + "] - Ocupado por: " + b.getContenido() +
                   " | Siguiente: " + (b.getSiguienteBloque() == -1 ? "FIN (-1)" : b.getSiguienteBloque());
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (disco == null) return;

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setFont(new Font("Consolas", Font.BOLD, 10));

            int total = disco.getTotalBloques();
            int cabezal = disco.getCabezalActual();

            for (int i = 0; i < total; i++) {
                int r = i / COLS;
                int c = i % COLS;
                int x = 4 + c * CELL_W;
                int y = 4 + r * CELL_H;

                Bloque b = disco.getBloque(i);
                boolean esSwap = disco.esBloqueDeSwap(i);

                Color fondo;
                if (b.isOcupado()) {
                    try {
                        fondo = Color.decode(b.getColorHex());
                    } catch (Exception ex) {
                        fondo = esSwap ? new Color(231, 76, 60) : new Color(51, 153, 255);
                    }
                } else {
                    fondo = esSwap ? new Color(65, 52, 32) : new Color(34, 45, 65);
                }

                g2.setColor(fondo);
                g2.fillRoundRect(x + 1, y + 1, CELL_W - 3, CELL_H - 3, 4, 4);

                if (i == cabezal) {
                    g2.setColor(Color.YELLOW);
                    g2.setStroke(new BasicStroke(2.2f));
                    g2.drawRoundRect(x + 1, y + 1, CELL_W - 3, CELL_H - 3, 4, 4);
                    g2.setStroke(new BasicStroke(1.0f));
                }

                g2.setColor(b.isOcupado() ? Color.BLACK : Color.LIGHT_GRAY);
                String etiqueta = String.valueOf(i);
                if (b.isOcupado() && b.getSiguienteBloque() != -1) {
                    etiqueta = i + "→";
                }
                g2.drawString(etiqueta, x + 4, y + 16);
            }
        }
    }
}

