import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.io.FileWriter;
import java.sql.*;
import java.text.SimpleDateFormat;

public class ConsultarCortesCaja {
    // --- UI (del .form) ---
    private JPanel panelMain;
    private JPanel panelContenedor;
    private JPanel panelTabla;
    private JLabel lblTitulo;
    private JTable tblCortes;
    private JScrollPane scrTabla;
    private JButton exportarACSVButton;
    private JButton cerrarButton;
    private JLabel lblEtiqueta;
    private JPanel panelBotones;

    // ---- PALETA / TIPOS (solo presentación) ----
    private static final Color BG_TOP       = new Color(0x052E16);
    private static final Color BG_BOT       = new Color(0x064E3B);
    private static final Color TEXT_MUTED   = new Color(0x67676E);
    private static final Color TABLE_ALT    = new Color(0xF9FAFB);
    private static final Color TABLE_SEL_BG = new Color(0xE6F7EE);
    private static final Color BORDER_SOFT  = new Color(0x535353);
    private static final Color CARD_BG      = new Color(255, 255, 255);
    private static final Color GREEN_DARK   = new Color(0x0A6B2A);
    private static final Color GREEN_BASE   = new Color(0x16A34A);
    private static final Color GREEN_SOFT   = new Color(0x22C55E);
    private static final Color TEXT_PRIMARY = new Color(0x111827);
    private static final Color BORDER_FOCUS = new Color(0x059669);
    private final Font fText   = new Font("Segoe UI", Font.PLAIN, 16);
    private final Font fTitle  = new Font("Segoe UI", Font.BOLD, 24);

    // --- tabla ---
    private DefaultTableModel modelo;
    private TableRowSorter<DefaultTableModel> sorter;

    // --- ctor ---
    public ConsultarCortesCaja() {
        construirTabla();
        cargarCortes();

        if (exportarACSVButton != null) {
            exportarACSVButton.addActionListener(e -> exportarCorteSeleccionadoCSV());
        }
        if (cerrarButton != null) {
            cerrarButton.addActionListener(e -> {
                Window w = getOwnerWindow();
                if (w instanceof JDialog d) d.dispose();
                else if (w != null) w.dispose();
            });
        }

        // ---- Estilo (solo UI) ----
        if (panelMain != null) panelMain.setBorder(new EmptyBorder(12,12,12,12));
        if (lblTitulo != null) {
            lblTitulo.setText("Cortes de caja");
            lblTitulo.setFont(fTitle);
            lblTitulo.setForeground(TEXT_PRIMARY);
        }
        if (lblEtiqueta != null) {
            lblEtiqueta.setVisible(true);
            lblEtiqueta.setForeground(TEXT_MUTED);
        }
        applyTheme(); // aplica tarjetas, botones, encabezados, etc.
    }

    /** Crea y devuelve el diálogo listo para mostrarse. */
    public static JDialog createDialog(Window owner) {
        ConsultarCortesCaja ui = new ConsultarCortesCaja();
        JDialog d = new JDialog(owner, "Consultar cortes de caja", Dialog.ModalityType.MODELESS);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(ui.root());
        d.setMinimumSize(new Dimension(900, 520));
        d.pack();
        if (owner != null && owner.isShowing()) d.setLocationRelativeTo(owner);
        else d.setLocationRelativeTo(null);
        return d;
    }

    // ===================== Tabla =====================
    private void construirTabla() {
        if (tblCortes == null) return;
        String[] cols = {"Fecha", "ID", "Sucursal", "Usuario"}; // ← cambia encabezado
        modelo = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                // Col 1 = ID sigue siendo Integer; "Usuario" ahora es String
                return (c==1) ? Integer.class : String.class;
            }
        };
        tblCortes.setModel(modelo);
        sorter = new TableRowSorter<>(modelo);
        tblCortes.setRowSorter(sorter);

        tblCortes.setRowHeight(26);
        tblCortes.setShowGrid(false);
        tblCortes.setIntercellSpacing(new Dimension(0,0));
        JTableHeader h = tblCortes.getTableHeader();
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, 32));

        int[] widths = {220, 90, 280, 200};
        for (int i = 0; i < Math.min(widths.length, tblCortes.getColumnCount()); i++) {
            tblCortes.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // Estilos de tabla (renderer cabecera + zebra)
        if (h != null) {
            TableCellRenderer base = h.getDefaultRenderer();
            h.setDefaultRenderer(new HeaderRenderer(base));
        }
        tblCortes.setDefaultRenderer(Object.class, new ZebraRenderer());
        tblCortes.setForeground(TEXT_PRIMARY);
        tblCortes.setBackground(Color.WHITE);
        tblCortes.setFont(fText);
    }

    private void cargarCortes() {
        if (tblCortes == null) return;
        modelo.setRowCount(0);

        final String sql = """
            SELECT
              c.id,
              c.usuario_id,
              c.generado_en,
              COALESCE(s.nombre, '') AS sucursal,
              COALESCE(u.nombre, t.nombre, CONCAT('ID ', u.id)) AS usuario   -- ← nombre mostrado
            FROM corte_caja_resumen c
            LEFT JOIN Usuarios u     ON u.id = c.usuario_id
            LEFT JOIN trabajadores t ON t.id = u.trabajador_id
            LEFT JOIN sucursales s   ON s.id = t.sucursal_id
            ORDER BY c.generado_en DESC, c.id DESC
        """;

        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            while (rs.next()) {
                int id          = rs.getInt("id");
                Timestamp ts    = rs.getTimestamp("generado_en");
                String fecha    = (ts != null ? sdf.format(ts) : "");
                String sucursal = rs.getString("sucursal");
                String usuario  = rs.getString("usuario"); // ← nombre final para mostrar

                modelo.addRow(new Object[]{fecha, id, sucursal, usuario});
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(root(), "Error al cargar cortes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Integer getSelectedCorteId() {
        int view = tblCortes.getSelectedRow();
        if (view < 0) return null;
        int modelRow = tblCortes.convertRowIndexToModel(view);
        Object v = modelo.getValueAt(modelRow, 1); // columna "ID" se mantiene en índice 1
        if (v == null) return null;
        return (v instanceof Integer) ? (Integer) v : Integer.parseInt(v.toString());
    }

    // ===================== Exportar =====================
    private void exportarCorteSeleccionadoCSV() {
        Integer id = getSelectedCorteId();
        if (id == null) {
            JOptionPane.showMessageDialog(root(), "Selecciona un corte para exportar.");
            return;
        }

        final String q = "SELECT * FROM corte_caja_resumen WHERE id = ?";
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(q)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    JOptionPane.showMessageDialog(root(), "No se encontró el corte ID " + id);
                    return;
                }
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();

                JFileChooser fc = new JFileChooser();
                fc.setDialogTitle("Exportar corte #" + id);
                fc.setSelectedFile(new java.io.File("corte_" + id + ".csv"));
                if (fc.showSaveDialog(root()) != JFileChooser.APPROVE_OPTION) return;

                try (FileWriter wr = new FileWriter(fc.getSelectedFile())) {
                    // encabezados
                    for (int c = 1; c <= cols; c++) {
                        wr.write(esc(md.getColumnLabel(c)));
                        if (c < cols) wr.write(",");
                    }
                    wr.write("\n");
                    // fila de datos
                    for (int c = 1; c <= cols; c++) {
                        Object v = rs.getObject(c);
                        wr.write(esc(v == null ? "" : v.toString()));
                        if (c < cols) wr.write(",");
                    }
                    wr.write("\n");
                }

                JOptionPane.showMessageDialog(root(),
                        "Exportado a:\n" + fc.getSelectedFile().getAbsolutePath());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(root(), "No se pudo exportar:\n" + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"","\"\"") + "\"";
        }
        return s;
    }

    // ===================== Helpers UI =====================
    private Container root() {
        if (panelMain != null) return panelMain;
        JPanel r = new JPanel(new BorderLayout());
        r.add(panelTabla != null ? panelTabla : new JScrollPane(new JTable()), BorderLayout.CENTER);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        if (exportarACSVButton != null) south.add(exportarACSVButton);
        if (cerrarButton != null) south.add(cerrarButton);
        r.add(south, BorderLayout.SOUTH);
        return r;
    }

    private Window getOwnerWindow() {
        Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (active != null) return active;
        for (Frame f : Frame.getFrames()) if (f.isVisible()) return f;
        return JOptionPane.getRootFrame();
    }

    // ========== SOLO ESTILO (no cambia funcionalidades) ==========
    private void applyTheme() {
        // paneles como tarjetas
        decorateAsCard(panelContenedor);
        decorateAsCard(panelTabla);
        decorateAsCard(panelBotones);
        decorateAsCard(panelMain);

        // scrollpane fondo blanco
        if (scrTabla != null) {
            scrTabla.getViewport().setBackground(CARD_BG);
            scrTabla.setBorder(new EmptyBorder(0,0,0,0));
        }

        // botones
        if (exportarACSVButton != null) stylePrimaryButton(exportarACSVButton);
        if (cerrarButton != null)       styleExitButton(cerrarButton);
    }

    private void stylePrimaryButton(JButton b) {
        if (b == null) return;
        // Igual que pantallaCajero: usa ModernButtonUI de PantallaAdmin
        b.setUI(new PantallaAdmin.ModernButtonUI(GREEN_DARK, GREEN_SOFT, GREEN_DARK, Color.WHITE, 15, true));
        b.setBorder(new EmptyBorder(10,18,10,28));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setForeground(Color.WHITE);
    }

    private void styleExitButton(JButton b) {
        if (b == null) return;
        // Botón rojo consistente con tu estilo
        Color ROJO_BASE    = new Color(0xDC2626);
        Color GRIS_HOVER   = new Color(0xD1D5DB);
        Color GRIS_PRESSED = new Color(0x9CA3AF);
        b.setUI(new Login.ModernButtonUI(ROJO_BASE, GRIS_HOVER, GRIS_PRESSED, Color.BLACK, 22, true));
        b.setBorder(new EmptyBorder(10,18,10,28));
        b.setForeground(Color.WHITE);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
    }

    private void decorateAsCard(JComponent c) {
        if (c == null) return;
        c.setOpaque(true);
        c.setBackground(CARD_BG);
        c.setBorder(new PantallaAdmin.CompoundRoundShadowBorder(14, BORDER_SOFT, new Color(0,0,0,28)));
    }

    // ===== Renderers visuales =====
    private static class HeaderRenderer extends DefaultTableCellRenderer {
        private final TableCellRenderer base;
        HeaderRenderer(TableCellRenderer base){ this.base = base; }
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            Component comp = base.getTableCellRendererComponent(t, v, s, f, r, c);
            comp.setBackground(GREEN_DARK);
            comp.setForeground(Color.WHITE);
            comp.setFont(new Font("Segoe UI", Font.BOLD, 13));
            if (comp instanceof JComponent jc) {
                jc.setBorder(new EmptyBorder(6,8,6,8));
            }
            return comp;
        }
    }
    private static class ZebraRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            if (sel) {
                setBackground(TABLE_SEL_BG);
                setForeground(TEXT_PRIMARY);
            } else {
                setBackground((r % 2 == 0) ? Color.WHITE : TABLE_ALT);
                setForeground(TEXT_PRIMARY);
            }
            setBorder(new EmptyBorder(6,8,6,8));
            if (v instanceof Number) setHorizontalAlignment(RIGHT);
            else setHorizontalAlignment(LEFT);
            return this;
        }
    }
}
