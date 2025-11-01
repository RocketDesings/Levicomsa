import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.sql.*;

public class CRUDCategorias {
    // --- Componentes del .form ---
    private JPanel panelTabla;
    private JScrollPane scrTablaCategorias;
    private JTable tblCategorias;
    private JLabel lblEtiqueta;
    private JPanel panelBotones;
    private JButton btnAgregarCategorias;
    private JButton btnModificarCategorias;
    private JButton btnEliminarCategorias;
    private JButton btnCancelar;
    private JPanel panelMain;
    private JPanel panelBotones2;

    // --- Contexto (por si usas triggers/bitácoras) ---
    private final int usuarioId;
    private final int sucursalId;

    //COLORES
    private static final Color GREEN_DARK   = new Color(0x0A6B2A);
    private static final Color GREEN_BASE   = new Color(0x16A34A);
    private static final Color GREEN_SOFT   = new Color(0x22C55E);
    private static final Color RED_BASE     = new Color(0xDC2626);
    private static final Color RED_HOV      = new Color(0xD1D5DB);
    private static final Color RED_PR       = new Color(0x9CA3AF);
    private static final Color BG_CANVAS    = new Color(0xF3F4F6);
    private static final Color CARD_BG      = Color.WHITE;
    private static final Color BORDER_SOFT  = new Color(0x000000);
    private static final Color TEXT_PRIMARY = new Color(0x111827);
    private static final Color TEXT_MUTED   = new Color(0x6B7280);
    private static final Color BORDER_FOCUS = new Color(0x059669);
    private final Font fText   = new Font("Segoe UI", Font.PLAIN, 16);
    private final Font fTitle  = new Font("Segoe UI", Font.BOLD, 22);

    // --- Tabla ---
    private DefaultTableModel modelo;
    private TableRowSorter<DefaultTableModel> sorter;

    // ====== ctor ======
    public CRUDCategorias(int usuarioId, int sucursalId) {
        this.usuarioId = usuarioId;
        this.sucursalId = sucursalId;

        if (panelMain != null) panelMain.setBorder(new EmptyBorder(10,10,10,10));
        configurarTabla();
        estilizarBotonera();
        cargarCategorias();

        // Acciones
        if (btnCancelar != null) {
            btnCancelar.addActionListener(e -> {
                Window w = SwingUtilities.getWindowAncestor(btnCancelar);
                if (w instanceof JDialog d) d.dispose();
            });
        }
        // Abrir "Agregar categoría"
        if (btnAgregarCategorias != null) {
            btnAgregarCategorias.addActionListener(e -> {
                Window owner = SwingUtilities.getWindowAncestor(panelMain);
                JDialog d = AgregarCategoria.createDialog(
                        owner,
                        usuarioId,
                        sucursalId,
                        this::cargarCategorias  // callback para refrescar al guardar
                );
                d.setVisible(true);
            });
        }
        if (btnModificarCategorias != null) {
            btnModificarCategorias.addActionListener(e -> {
                Integer id = getSelectedCategoriaId();
                if (id == null) { JOptionPane.showMessageDialog(panelMain, "Selecciona una categoría."); return; }

                Window owner = SwingUtilities.getWindowAncestor(btnModificarCategorias);
                JDialog d = ModificarCategoria.createDialog(owner, usuarioId, sucursalId, id, this::cargarCategorias);
                d.setVisible(true);
            });
        }

        if (btnEliminarCategorias != null) {
            btnEliminarCategorias.addActionListener(e -> eliminarCategoriaSeleccionada());
        }
        decorateAsCard(panelTabla);
        decorateAsCard(panelBotones2);
        decorateAsCard(panelMain);
    }

    /** Crea y muestra el diálogo centrado. */
    public static JDialog createDialog(Window owner, int usuarioId, int sucursalId) {
        CRUDCategorias ui = new CRUDCategorias(usuarioId, sucursalId);
        JDialog d = new JDialog(owner, "Categorías", Dialog.ModalityType.MODELESS);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(ui.panelMain);
        d.setMinimumSize(new Dimension(760, 520));
        d.pack();
        if (owner != null && owner.isShowing()) d.setLocationRelativeTo(owner);
        else d.setLocationRelativeTo(null);
        return d;
    }

    // ================== Tabla ==================
    private void configurarTabla() {
        String[] cols = {"ID", "Nombre", "Activo"};
        modelo = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return (c == 0) ? Integer.class : String.class;
            }
        };
        tblCategorias.setModel(modelo);
        sorter = new TableRowSorter<>(modelo);
        tblCategorias.setRowSorter(sorter);

        tblCategorias.setRowHeight(26);
        tblCategorias.setShowGrid(false);
        tblCategorias.setIntercellSpacing(new Dimension(0, 0));

        JTableHeader h = tblCategorias.getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, 32));

        int[] widths = {70, 480, 90};
        for (int i = 0; i < Math.min(widths.length, tblCategorias.getColumnCount()); i++) {
            tblCategorias.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // Renderer “zebra” + alineaciones
        ZebraRenderer zr = new ZebraRenderer(tblCategorias);
        tblCategorias.setDefaultRenderer(Object.class, zr);
        tblCategorias.setDefaultRenderer(Number.class, zr);
        tblCategorias.getColumnModel().getColumn(0).setCellRenderer(zr); // ID izq
    }

    /** Carga categorías desde la BD. */
    public final void cargarCategorias() {
        final String sql = """
            SELECT id, nombre, activo
              FROM categorias_servicio
             ORDER BY nombre
        """;
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            modelo.setRowCount(0);
            while (rs.next()) {
                int id        = rs.getInt("id");
                String nombre = nvl(rs.getString("nombre"));
                String activo = rs.getInt("activo") == 1 ? "Sí" : "No";
                modelo.addRow(new Object[]{ id, nombre, activo });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(panelMain, "Error al cargar categorías: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Integer getSelectedCategoriaId() {
        int viewRow = tblCategorias.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = tblCategorias.convertRowIndexToModel(viewRow);
        Object v = tblCategorias.getModel().getValueAt(modelRow, 0); // Columna 0 = ID
        if (v == null) return null;
        return (v instanceof Integer) ? (Integer) v : Integer.parseInt(v.toString());
    }

    private void eliminarCategoriaSeleccionada() {
        Integer id = getSelectedCategoriaId();
        if (id == null) { JOptionPane.showMessageDialog(panelMain, "Selecciona una categoría."); return; }

        int ok = JOptionPane.showConfirmDialog(
                panelMain,
                "¿Eliminar definitivamente la categoría?\n" +
                        "Si está referenciada, podrás desactivarla.",
                "Confirmar", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
        );
        if (ok != JOptionPane.YES_OPTION) return;

        final String sqlDel = "DELETE FROM categorias_servicio WHERE id = ?";
        try (Connection con = DB.get()) {
            if (usuarioId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_user_id=?")) { p.setInt(1, usuarioId); p.executeUpdate(); }
            if (sucursalId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_sucursal_id=?")) { p.setInt(1, sucursalId); p.executeUpdate(); }

            try (PreparedStatement ps = con.prepareStatement(sqlDel)) {
                ps.setInt(1, id);
                int n = ps.executeUpdate();
                JOptionPane.showMessageDialog(panelMain, n>0 ? "Categoría eliminada." : "No se encontró la categoría.");
                cargarCategorias();
            }
        } catch (SQLException e) {
            // Si está referenciada, ofrecer “soft delete”
            if (isFK(e)) {
                int ch = JOptionPane.showConfirmDialog(panelMain,
                        "La categoría está referenciada.\n¿Deseas DESACTIVARLA en su lugar?",
                        "Categoría referenciada", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (ch == JOptionPane.YES_OPTION) softDeleteCategoria(id);
            } else {
                JOptionPane.showMessageDialog(panelMain, "Error al eliminar: " + e.getMessage());
            }
        }
    }

    private void softDeleteCategoria(int id) {
        final String sql = "UPDATE categorias_servicio SET activo = 0 WHERE id = ?";
        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            int n = ps.executeUpdate();
            JOptionPane.showMessageDialog(panelMain, n>0 ? "Categoría desactivada." : "No se pudo desactivar.");
            cargarCategorias();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(panelMain, "Error al desactivar: " + ex.getMessage());
        }
    }

    // ============== Estilos ==============
    private void estilizarBotonera() {
        if (btnAgregarCategorias != null) stylePrimaryButton(btnAgregarCategorias);
        if (btnModificarCategorias != null) stylePrimaryButton(btnModificarCategorias);
        if (btnEliminarCategorias != null) styleExitButton(btnEliminarCategorias);
        if (btnCancelar != null) styleExitButton(btnCancelar);
    }

    private void stylePrimaryButton(JButton b) {
        if (b == null) return;
        b.setUI(new HerramientasAdmin.ModernButtonUI(GREEN_BASE, GREEN_SOFT, GREEN_DARK, Color.WHITE, 14, true));
        b.setBorder(new EmptyBorder(12,18,12,18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
    }
    private void styleExitButton(JButton b) {
        if (b == null) return;
        b.setUI(new HerramientasAdmin.ModernButtonUI(RED_BASE, RED_HOV, RED_PR, Color.WHITE, 14, true));
        b.setBorder(new EmptyBorder(12,18,12,18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
    }

    // Botón moderno (mismo que venimos usando)
    static class ModernButtonUI extends BasicButtonUI {
        private final Color bg, hover, press, fg;
        private final int arc; private final boolean filled;
        ModernButtonUI(Color bg, Color hover, Color press, Color fg, int arc, boolean filled) {
            this.bg = bg; this.hover = hover; this.press = press; this.fg = fg; this.arc = arc; this.filled = filled;
        }
        @Override public void installUI(JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setOpaque(false);
            b.setBorder(new EmptyBorder(10, 18, 10, 18));
            b.setRolloverEnabled(true);
            b.setFocusPainted(false);
            b.setForeground(fg);
        }
        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ButtonModel m = b.getModel();
            Color fill = m.isPressed() ? press : (m.isRollover() ? hover : bg);
            if (filled || fill.getAlpha() > 0) {
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), arc, arc);
            }
            g2.dispose();
            super.paint(g, c);
        }
    }

    // Renderer zebra + alineaciones
    static class ZebraRenderer extends javax.swing.table.DefaultTableCellRenderer {
        private final Color even = Color.WHITE;
        private final Color odd  = new Color(236, 253, 245); // verde pastel
        ZebraRenderer(JTable table) {}
        @Override
        public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
            if (!isSelected) setBackground((row % 2 == 0) ? even : odd);

            if (column == 0) setHorizontalAlignment(LEFT);     // ID izq
            else if (column == 2) setHorizontalAlignment(CENTER); // Activo centro
            else setHorizontalAlignment(LEFT);                 // Nombre izq
            return this;
        }
    }

    // helpers
    private static String nvl(String s) { return s != null ? s : ""; }
    private static boolean isFK(SQLException e) {
        return "23000".equals(e.getSQLState()) || e.getErrorCode() == 1451 ||
                (e.getMessage() != null && e.getMessage().toLowerCase().contains("foreign key"));
    }
    //ESTILO COMUN
    private void decorateAsCard(JComponent c) {
        if (c == null) return;
        c.setOpaque(true);
        c.setBackground(CARD_BG);
        c.setBorder(new PantallaAdmin.CompoundRoundShadowBorder(14, BORDER_SOFT, new Color(0,0,0,28)));
    }
}
