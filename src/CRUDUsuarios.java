import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.sql.*;

public class CRUDUsuarios {
    // --- UI del .form ---
    private JPanel panelMain;
    private JPanel panelTabla;
    private JScrollPane scrTablaUsuarios;
    private JTable tblUsuarios;
    private JPanel panelBotones;
    private JButton btnAgregarUsuario;
    private JButton btnModificarUsuario;
    private JButton btnEliminarUsuario;
    private JButton btnCancelar;
    private JPanel panelContenedor;
    private static final Color GREEN_DARK   = new Color(0x0A6B2A);
    private static final Color GREEN_BASE   = new Color(0x16A34A);
    private static final Color GREEN_SOFT   = new Color(0x22C55E);
    private static final Color RED_BASE     = new Color(0xDC2626);
    private static final Color RED_HOV      = new Color(0xD1D5DB);
    private static final Color RED_PR       = new Color(0x9CA3AF);
    private static final Color CARD_BG      = Color.WHITE;
    private static final Color BORDER_SOFT  = new Color(0x000000);
    private final Font fText   = new Font("Segoe UI", Font.PLAIN, 16);
    private final Font fTitle  = new Font("Segoe UI", Font.BOLD, 22);
    // --- contexto ---
    private final Window owner;
    private final int usuarioId;
    private final int sucursalId;

    // --- tabla ---
    private DefaultTableModel modelo;
    private TableRowSorter<DefaultTableModel> sorter;

    public CRUDUsuarios(Window owner, int usuarioId, int sucursalId) {
        this.owner = owner;
        this.usuarioId = usuarioId;
        this.sucursalId = sucursalId;

        configurarTabla();
        cargarUsuarios();

        // ======= Estilo igual a CRUDServicios =======
        compactLookAndFeel();

        // ======= Acciones =======
        if (btnCancelar != null) {
            btnCancelar.addActionListener(e -> {
                Window w = getOwnerWindow();
                if (w instanceof JDialog d) d.dispose();
            });
        }
        if (btnAgregarUsuario != null) {
            btnAgregarUsuario.addActionListener(e -> {
                JDialog d = AgregarUsuario.createDialog(
                        getOwnerWindow(),
                        usuarioId,
                        sucursalId,
                        this::cargarUsuarios
                );
                d.setVisible(true);
            });
        }
        if (btnModificarUsuario != null) {
            btnModificarUsuario.addActionListener(e -> {
                JDialog d = ModificarUsuario.createDialog(
                        getOwnerWindow(),
                        usuarioId,
                        sucursalId,
                        this::cargarUsuarios
                );
                d.setVisible(true);
            });
        }
        if (btnEliminarUsuario != null) {
            btnEliminarUsuario.addActionListener(e -> eliminarUsuarioSeleccionado());
        }
        decorateAsCard(panelMain);
        decorateAsCard(panelTabla);
        decorateAsCard(panelContenedor);
    }

    /** Factory para abrir como diálogo MODELESS. */
    public static JDialog createDialog(Window owner, int usuarioId, int sucursalId) {
        CRUDUsuarios ui = new CRUDUsuarios(owner, usuarioId, sucursalId);
        JDialog d = new JDialog(owner, "Usuarios", Dialog.ModalityType.MODELESS);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(ui.panelMain);
        d.setMinimumSize(new Dimension(900, 520));
        d.pack();

        if (owner != null && owner.isShowing()) d.setLocationRelativeTo(owner);
        else d.setLocationRelativeTo(null);
        return d;
    }

    // ===================== Tabla =====================
    private void configurarTabla() {
        if (tblUsuarios == null) return;

        // Col 0 = ID (oculta). Visibles: Usuario, Nombre empleado, Rol, Activo
        String[] cols = {"#", "Usuario", "Nombre empleado", "Rol", "Activo"};
        modelo = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return (c == 0) ? Integer.class : String.class; }
        };
        tblUsuarios.setModel(modelo);
        sorter = new TableRowSorter<>(modelo);
        tblUsuarios.setRowSorter(sorter);

        tblUsuarios.setRowHeight(26);
        tblUsuarios.setShowGrid(false);
        tblUsuarios.setIntercellSpacing(new Dimension(0, 0));
        tblUsuarios.getTableHeader().setReorderingAllowed(false);

        JTableHeader h = tblUsuarios.getTableHeader();
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, 32));

        // Oculta ID
        var idCol = tblUsuarios.getColumnModel().getColumn(0);
        idCol.setMinWidth(0); idCol.setMaxWidth(0); idCol.setPreferredWidth(0);

        int[] widths = {0, 160, 260, 200, 80};
        for (int i = 1; i < Math.min(widths.length, tblUsuarios.getColumnCount()); i++) {
            tblUsuarios.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // Zebra renderer y alineaciones
        ZebraRenderer zebra = new ZebraRenderer(tblUsuarios);
        tblUsuarios.setDefaultRenderer(Object.class, zebra);
    }

    /* Carga: Usuario, Nombre empleado, Rol (nombre), Activo. */
    public final void cargarUsuarios() {
        if (tblUsuarios == null) return;

        final String sql = """
            SELECT u.id,
                   u.usuario,
                   COALESCE(t.nombre, u.nombre) AS empleado,
                   COALESCE(r.nombre, CONCAT('Rol ', u.rol_id)) AS rol,
                   u.activo
            FROM Usuarios u
            LEFT JOIN trabajadores t ON t.id = u.trabajador_id
            LEFT JOIN roles r        ON r.id = u.rol_id
            ORDER BY u.usuario
        """;

        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            modelo.setRowCount(0);
            while (rs.next()) {
                int id          = rs.getInt("id");
                String usuario  = nvl(rs.getString("usuario"));
                String empleado = nvl(rs.getString("empleado"));
                String rol      = nvl(rs.getString("rol"));
                String activo   = (rs.getInt("activo") == 1) ? "Sí" : "No";
                modelo.addRow(new Object[]{ id, usuario, empleado, rol, activo });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(root(), "Error al cargar usuarios: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Integer getSelectedUsuarioId() {
        if (tblUsuarios == null || modelo == null) return null;
        int viewRow = tblUsuarios.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = tblUsuarios.convertRowIndexToModel(viewRow);
        Object v = modelo.getValueAt(modelRow, 0);
        return (v instanceof Integer) ? (Integer) v : Integer.valueOf(v.toString());
    }

    private void eliminarUsuarioSeleccionado() {
        Integer id = getSelectedUsuarioId();
        if (id == null) { JOptionPane.showMessageDialog(panelMain, "Selecciona un usuario para eliminar."); return; }
        if (id == usuarioId) {
            JOptionPane.showMessageDialog(panelMain, "No puedes eliminar tu propio usuario mientras estás logueado.");
            return;
        }

        int ok = JOptionPane.showConfirmDialog(
                panelMain,
                "¿Eliminar definitivamente al usuario?\nLa bitácora conservará el historial (usuario_id = NULL).",
                "Confirmar eliminación",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (ok != JOptionPane.YES_OPTION) return;

        final String sqlDelete = "DELETE FROM Usuarios WHERE id = ?";
        try (Connection con = DB.get()) {
            if (usuarioId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_user_id=?")) {
                p.setInt(1, usuarioId); p.executeUpdate();
            }
            if (sucursalId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_sucursal_id=?")) {
                p.setInt(1, sucursalId); p.executeUpdate();
            }

            try (PreparedStatement ps = con.prepareStatement(sqlDelete)) {
                ps.setInt(1, id);
                int n = ps.executeUpdate();
                if (n > 0) {
                    JOptionPane.showMessageDialog(panelMain, "Usuario eliminado.");
                    cargarUsuarios();
                } else {
                    JOptionPane.showMessageDialog(panelMain, "No se encontró el usuario.");
                }
            }
        } catch (SQLException e) {
            if (isForeignKeyViolation(e)) {
                int choose = JOptionPane.showConfirmDialog(
                        panelMain,
                        "El usuario está referenciado por otros registros.\n¿Deseas DESACTIVARLO (activo=0) en lugar de eliminar?",
                        "Usuario referenciado",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );
                if (choose == JOptionPane.YES_OPTION) softDeleteUsuario(id);
            } else {
                JOptionPane.showMessageDialog(panelMain, "Error al eliminar: " + e.getMessage());
            }
        }
    }

    private static boolean isForeignKeyViolation(SQLException e) {
        return "23000".equals(e.getSQLState())
                || e.getErrorCode() == 1451
                || (e.getMessage() != null && e.getMessage().toLowerCase().contains("foreign key"));
    }

    private void softDeleteUsuario(int id) {
        final String sql = "UPDATE Usuarios SET activo=0, actualizado_en=NOW() WHERE id=?";
        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            int n = ps.executeUpdate();
            JOptionPane.showMessageDialog(panelMain, n > 0 ? "Usuario desactivado." : "No se pudo desactivar el usuario.");
            cargarUsuarios();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(panelMain, "Error al desactivar: " + ex.getMessage());
        }
    }

    // ===================== Estilo (como CRUDServicios) =====================
    private void compactLookAndFeel() {
        // Botones grandes + colores
        makeBig(btnAgregarUsuario);
        makeBig(btnModificarUsuario);
        makeBig(btnEliminarUsuario);
        makeBig(btnCancelar);

        stylePrimaryButton(btnAgregarUsuario);
        stylePrimaryButton(btnModificarUsuario);
        styleExitButton(btnEliminarUsuario);
        styleExitButton(btnCancelar);
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

    // ====== Render para “zebra” y alineaciones ======
    static class ZebraRenderer extends javax.swing.table.DefaultTableCellRenderer {
        private final Color even = Color.WHITE;
        private final Color odd  = new Color(236, 253, 245); // verde pastel muy ligero

        ZebraRenderer(JTable table) {}

        @Override
        public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);

            if (!isSelected) setBackground((row % 2 == 0) ? even : odd);

            // Alineaciones: Usuario/Nombre/Rol -> izquierda, Activo -> centro
            if (column == 4) setHorizontalAlignment(CENTER);
            else setHorizontalAlignment(LEFT);

            return this;
        }
    }

    // ====== Botón moderno ======
    static class ModernButtonUI extends BasicButtonUI {
        private final Color bg, hover, press, fg;
        private final int arc;
        private final boolean filled;

        ModernButtonUI(Color bg, Color hover, Color press, Color fg, int arc, boolean filled) {
            this.bg = bg; this.hover = hover; this.press = press; this.fg = fg; this.arc = arc; this.filled = filled;
        }

        @Override public void installUI(JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setOpaque(false);
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

    // ===================== Helpers =====================
    private Container root() {
        if (panelMain != null) return panelMain;
        if (panelTabla != null && panelTabla.getParent() != null) return panelTabla.getParent();
        if (panelTabla != null) return panelTabla;
        if (scrTablaUsuarios != null) return scrTablaUsuarios;
        JPanel r = new JPanel(new BorderLayout());
        if (panelTabla != null) r.add(panelTabla, BorderLayout.CENTER);
        if (panelBotones != null) r.add(panelBotones, BorderLayout.SOUTH);
        return r;
    }
    private Window getOwnerWindow() {
        if (owner != null) return owner;
        Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (active != null) return active;
        for (Frame f : Frame.getFrames()) if (f.isVisible()) return f;
        return JOptionPane.getRootFrame();
    }
    private void stylePrimaryButton(JButton b) {
        if (b == null) return;
        b.setUI(new HerramientasAdmin.ModernButtonUI(GREEN_BASE, GREEN_SOFT, GREEN_DARK, Color.WHITE, 14, true));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
    }
    private void styleExitButton(JButton b) {
        if (b == null) return;
        b.setUI(new HerramientasAdmin.ModernButtonUI(RED_BASE, RED_HOV, RED_PR, Color.WHITE, 14, true));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
    }
    //ESTILO COMUN
    private void decorateAsCard(JComponent c) {
        if (c == null) return;
        c.setOpaque(true);
        c.setBackground(CARD_BG);
        c.setBorder(new PantallaAdmin.CompoundRoundShadowBorder(14, BORDER_SOFT, new Color(0,0,0,28)));
    }
    private static String nvl(String s) { return s != null ? s : ""; }
}
