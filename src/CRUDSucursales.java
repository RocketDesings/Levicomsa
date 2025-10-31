import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.sql.*;

public class CRUDSucursales {
    // --- UI (del .form) ---
    private JPanel panelMain;
    private JTable tblSucursales;
    private JButton btnCancelar;
    private JScrollPane scrTablaSucursales;
    private JPanel panelTabla;
    private JPanel panelBotones;
    private JButton btnAgregarSucursal;
    private JButton btnModificarSucursal;
    private JButton btnEliminarSucursal;

    // ===== contexto =====
    private final int usuarioId;
    private final int sucursalId;

    // ===== tabla =====
    private DefaultTableModel modelo;
    private TableRowSorter<DefaultTableModel> sorter;

    public CRUDSucursales(int usuarioId, int sucursalId) {
        this.usuarioId = usuarioId;
        this.sucursalId = sucursalId;

        configurarTabla();
        cargarSucursales();

        // Estilo consistente con Usuarios/Servicios
        compactLookAndFeel();

        // Acciones
        if (btnCancelar != null) {
            btnCancelar.addActionListener(e -> {
                Window w = SwingUtilities.getWindowAncestor((Component) e.getSource());
                if (w instanceof JDialog d) d.dispose();
            });
        }
        if (btnAgregarSucursal != null) {
            btnAgregarSucursal.addActionListener(e -> {
                Window owner = SwingUtilities.getWindowAncestor((Component) e.getSource());
                JDialog dialog = AgregarSucursal.createDialog(
                        owner,
                        usuarioId,
                        sucursalId,
                        this::cargarSucursales
                );
                dialog.setVisible(true);
            });
        }
        if (btnModificarSucursal != null) {
            btnModificarSucursal.addActionListener(e -> {
                Integer id = getSelectedSucursalId();
                if (id == null) {
                    JOptionPane.showMessageDialog(root(), "Selecciona una sucursal para modificar.");
                    return;
                }
                Window owner = SwingUtilities.getWindowAncestor((Component) e.getSource());
                JDialog d = ModificarSucursal.createDialog(
                        owner,
                        id,
                        usuarioId,
                        sucursalId,
                        this::cargarSucursales
                );
                d.setVisible(true);
            });
        }
        if (btnEliminarSucursal != null) {
            btnEliminarSucursal.addActionListener(e -> eliminarSucursalSeleccionada());
        }
    }

    /** Crea un JDialog MODELESS conteniendo esta UI (con fallback de contentPane). */
    public static JDialog createDialog(Window owner, int usuarioId, int sucursalId) {
        CRUDSucursales ui = new CRUDSucursales(usuarioId, sucursalId);
        JDialog d = new JDialog(owner, "Sucursales", Dialog.ModalityType.MODELESS);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        // Nunca pasar null al contentPane:
        d.setContentPane(ui.root());

        d.setMinimumSize(new Dimension(900, 520));
        d.pack();
        if (owner != null && owner.isShowing()) d.setLocationRelativeTo(owner);
        else d.setLocationRelativeTo(null);
        return d;
    }

    // ================= tabla =================
    private void configurarTabla() {
        if (tblSucursales == null) return;

        String[] cols = {"ID", "Nombre", "Dirección", "Teléfono", "Activo", "Creado en"};
        modelo = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return switch (c) {
                    case 0 -> Integer.class;           // ID
                    default -> String.class;
                };
            }
        };
        tblSucursales.setModel(modelo);
        sorter = new TableRowSorter<>(modelo);
        tblSucursales.setRowSorter(sorter);

        tblSucursales.setRowHeight(26);
        tblSucursales.setShowGrid(false);
        tblSucursales.setIntercellSpacing(new Dimension(0,0));

        JTableHeader h = tblSucursales.getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, 32));

        int[] widths = {60, 220, 360, 140, 80, 180};
        for (int i = 0; i < Math.min(widths.length, tblSucursales.getColumnCount()); i++) {
            tblSucursales.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // Renderer zebra + alineaciones
        ZebraRenderer zebra = new ZebraRenderer();
        tblSucursales.setDefaultRenderer(Object.class, zebra);
    }

    /** Carga datos y llena el modelo. */
    public final void cargarSucursales() {
        final String sql = """
            SELECT id, nombre, direccion, telefono, activo, creado_en
            FROM sucursales
            ORDER BY nombre
        """;
        try (Connection con = DB.get()) {
            // Variables de sesión (si usas triggers/bitácora)
            if (usuarioId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_user_id=?")) { p.setInt(1, usuarioId); p.executeUpdate(); }
            if (sucursalId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_sucursal_id=?")) { p.setInt(1, sucursalId); p.executeUpdate(); }

            try (PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                modelo.setRowCount(0);
                while (rs.next()) {
                    int id        = rs.getInt("id");
                    String nombre = nvl(rs.getString("nombre"));
                    String dir    = nvl(rs.getString("direccion"));
                    String tel    = nvl(rs.getString("telefono"));
                    String activo = (rs.getInt("activo") == 1) ? "Sí" : "No";
                    String creado = ts(rs.getTimestamp("creado_en"));

                    modelo.addRow(new Object[]{id, nombre, dir, tel, activo, creado});
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(root(), "Error al cargar sucursales: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Integer getSelectedSucursalId() {
        if (tblSucursales == null || modelo == null) return null;
        int viewRow = tblSucursales.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = tblSucursales.convertRowIndexToModel(viewRow);
        Object v = modelo.getValueAt(modelRow, 0);
        return (v instanceof Integer) ? (Integer) v : Integer.valueOf(v.toString());
    }

    private void eliminarSucursalSeleccionada() {
        Integer id = getSelectedSucursalId();
        if (id == null) {
            JOptionPane.showMessageDialog(root(), "Selecciona una sucursal para eliminar.");
            return;
        }
        int ok = JOptionPane.showConfirmDialog(
                root(),
                "¿Seguro que deseas eliminar la sucursal ID " + id + "?\nEsta acción no se puede deshacer.",
                "Confirmar eliminación",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (ok != JOptionPane.YES_OPTION) return;

        final String sql = "DELETE FROM sucursales WHERE id = ?";
        try (Connection con = DB.get()) {
            if (usuarioId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_user_id=?")) { p.setInt(1, usuarioId); p.executeUpdate(); }
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, id);
                int n = ps.executeUpdate();
                if (n > 0) {
                    JOptionPane.showMessageDialog(root(), "Sucursal eliminada.");
                    cargarSucursales();
                } else {
                    JOptionPane.showMessageDialog(root(), "No se encontró la sucursal.");
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(root(),
                    "No se pudo eliminar la sucursal (puede estar referenciada).\n" + e.getMessage());
            e.printStackTrace();
        }
    }

    // ================== Estilo compartido ==================
    private void compactLookAndFeel() {
        if (panelMain != null) panelMain.setBorder(new EmptyBorder(8, 10, 10, 10));

        makeBig(btnAgregarSucursal);
        makeBig(btnModificarSucursal);
        makeBig(btnEliminarSucursal);
        makeBig(btnCancelar);

        stylePrimaryButton(btnAgregarSucursal);
        stylePrimaryButton(btnModificarSucursal);
        styleDangerButton(btnEliminarSucursal);
        styleDangerButton(btnCancelar);

        hideHeaderIconOrTitle(panelMain);
    }

    private void makeBig(JButton b) {
        if (b == null) return;
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        Dimension big = new Dimension(140, 40);
        b.setPreferredSize(big);
        b.setMinimumSize(big);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void stylePrimaryButton(JButton b) {
        if (b == null) return;
        b.setUI(new ModernButtonUI(new Color(0x22C55E), new Color(0x16A34A), new Color(0x15803D),
                Color.WHITE, 12, true));
    }
    private void styleDangerButton(JButton b) {
        if (b == null) return;
        b.setUI(new ModernButtonUI(new Color(0xEF4444), new Color(0xDC2626), new Color(0xB91C1C),
                Color.WHITE, 12, true));
    }

    private void hideHeaderIconOrTitle(Container root) {
        if (root == null) return;
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel lbl) {
                String t = (lbl.getText() != null) ? lbl.getText().trim().toLowerCase() : "";
                boolean looksTitle = t.contains("sucursal");
                boolean hasIcon = lbl.getIcon() != null;
                if (looksTitle || hasIcon) lbl.setVisible(false);
            }
            if (c instanceof Container child) hideHeaderIconOrTitle(child);
        }
    }

    // ===== Renderer zebra =====
    static class ZebraRenderer extends javax.swing.table.DefaultTableCellRenderer {
        private final Color even = Color.WHITE;
        private final Color odd  = new Color(236, 253, 245); // verde pastel suave

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) setBackground((row % 2 == 0) ? even : odd);

            // Alineaciones: ID centro, Teléfono centro, Activo centro, resto izquierda
            if (column == 0 || column == 3 || column == 4) setHorizontalAlignment(CENTER);
            else setHorizontalAlignment(LEFT);
            return this;
        }
    }

    // ===== Botón moderno =====
    static class ModernButtonUI extends BasicButtonUI {
        private final Color bg, hover, press, fg; private final int arc; private final boolean filled;
        ModernButtonUI(Color bg, Color hover, Color press, Color fg, int arc, boolean filled) {
            this.bg = bg; this.hover = hover; this.press = press; this.fg = fg; this.arc = arc; this.filled = filled;
        }
        @Override public void installUI(JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setOpaque(false);
            b.setBorder(new EmptyBorder(10, 24, 10, 24));
            b.setRolloverEnabled(true);
            b.setFocusPainted(false);
            b.setForeground(fg);
        }
        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel m = b.getModel();
            Color fill = m.isPressed() ? press : (m.isRollover() ? hover : bg);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), arc, arc);
            g2.dispose();
            super.paint(g, c);
        }
    }

    // ===== helpers =====
    /** Devuelve un contenedor NUNCA null (para setContentPane). */
    private Container root() {
        if (panelMain != null) return panelMain;
        if (panelTabla != null && panelTabla.getParent() != null) return panelTabla.getParent();
        if (panelTabla != null) return panelTabla;
        if (scrTablaSucursales != null) return scrTablaSucursales;
        JPanel r = new JPanel(new BorderLayout());
        if (panelTabla != null) r.add(panelTabla, BorderLayout.CENTER);
        if (panelBotones != null) r.add(panelBotones, BorderLayout.SOUTH);
        return r;
    }

    private static String nvl(String s) { return (s != null) ? s : ""; }
    private static String ts(Timestamp t) { return (t != null) ? t.toString() : ""; }
}
