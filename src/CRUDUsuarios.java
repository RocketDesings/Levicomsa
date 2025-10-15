import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.sql.*;

public class CRUDUsuarios {
    // --- UI (de tu .form) ---
    private JPanel panelMain;
    private JPanel panelTabla;
    private JScrollPane scrTablaUsuarios;
    private JTable tblUsuarios;
    private JPanel panelBotones;
    private JButton btnAgregarUsuario;
    private JButton btnModificarUsuario;
    private JButton btnEliminarUsuario;
    private JButton btnCancelar;

    // --- contexto ---
    private final Window owner;
    private final int usuarioId;   // quién opera (para triggers si los usas)
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

        if (btnCancelar != null) {
            btnCancelar.addActionListener(e -> {
                Window w = getOwnerWindow();
                if (w instanceof JDialog d) d.dispose();
            });
        }
        if (btnAgregarUsuario != null) {
            btnAgregarUsuario.addActionListener(e -> {
                JDialog d = AgregarUsuario.createDialog(
                        getOwnerWindow(),   // owner del diálogo
                        usuarioId,          // @app_user_id (para triggers, si tienes)
                        sucursalId,         // @app_sucursal_id
                        this::cargarUsuarios// refresca la tabla al guardar
                );
                d.setVisible(true);
            });
        }
        if (btnModificarUsuario != null) {
            btnModificarUsuario.addActionListener(e -> {
                JDialog d = ModificarUsuario.createDialog(
                        getOwnerWindow(),     // o tu referencia a Window/JFrame
                        usuarioId,            // @app_user_id (quien está logueado)
                        sucursalId,           // @app_sucursal_id
                        this::cargarUsuarios  // refresca la tabla al guardar
                );
                d.setVisible(true);
            });
        }

        if (btnEliminarUsuario != null) {
            btnEliminarUsuario.addActionListener(e -> eliminarUsuarioSeleccionado());
        }
    }

    /** Factory para abrir como diálogo MODELESS. */
    public static JDialog createDialog(Window owner, int usuarioId, int sucursalId) {
        CRUDUsuarios ui = new CRUDUsuarios(owner, usuarioId, sucursalId);
        JDialog d = new JDialog(owner, "Usuarios", Dialog.ModalityType.MODELESS);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(ui.root());
        d.setMinimumSize(new Dimension(900, 560));
        d.pack();
        d.setLocationRelativeTo(owner);
        return d;
    }

    // ===================== Tabla =====================
    private void configurarTabla() {
        if (tblUsuarios == null) return;

        // Col 0 = ID (oculta), visibles: Usuario, Nombre empleado, Rol, Activo
        String[] cols = {"#", "Usuario", "Nombre empleado", "Rol", "Activo"};
        modelo = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return (c == 0) ? Integer.class : String.class;
            }
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

        int[] widths = {0, 160, 260, 160, 80};
        for (int i = 1; i < Math.min(widths.length, tblUsuarios.getColumnCount()); i++) {
            tblUsuarios.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    /** Carga y llena: Usuario, Nombre empleado, Rol (nombre), Activo. */
    public final void cargarUsuarios() {
        if (tblUsuarios == null) return;

        final String sql = """
            SELECT u.id,
                   u.usuario,
                   /* nombre del empleado: toma de trabajadores si existe; si no, el de Usuarios */
                   COALESCE(t.nombre, u.nombre) AS empleado,
                   /* nombre del rol: de tabla roles; si no, muestra 'Rol <id>' */
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

    // Dentro de CRUDUsuarios
    private void eliminarUsuarioSeleccionado() {
        Integer id = getSelectedUsuarioId();
        if (id == null) {
            JOptionPane.showMessageDialog(panelMain, "Selecciona un usuario para eliminar.");
            return;
        }
        // opcional: no permitir que el usuario actual se borre a sí mismo
        if (id == usuarioId) {
            JOptionPane.showMessageDialog(panelMain, "No puedes eliminar tu propio usuario mientras estás logueado.");
            return;
        }

        int ok = JOptionPane.showConfirmDialog(
                panelMain,
                "¿Eliminar definitivamente al usuario?\n" +
                        "La bitácora conservará el historial (usuario_id = NULL).",
                "Confirmar eliminación",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (ok != JOptionPane.YES_OPTION) return;

        final String sqlDelete = "DELETE FROM Usuarios WHERE id = ?";
        try (Connection con = DB.get()) {
            // variables de sesión para triggers/bitácora
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
                    cargarUsuarios(); // refresca la tabla
                } else {
                    JOptionPane.showMessageDialog(panelMain, "No se encontró el usuario.");
                }
            }
        } catch (SQLException e) {
            // Si otra FK distinta a las bitácoras bloquea, ofrece desactivar como plan B
            if (isForeignKeyViolation(e)) {
                int choose = JOptionPane.showConfirmDialog(
                        panelMain,
                        "El usuario está referenciado por otros registros.\n" +
                                "¿Deseas DESACTIVARLO (activo=0) en lugar de eliminar?",
                        "Usuario referenciado",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );
                if (choose == JOptionPane.YES_OPTION) {
                    softDeleteUsuario(id);
                }
            } else {
                JOptionPane.showMessageDialog(panelMain, "Error al eliminar: " + e.getMessage());
            }
        }
    }

    private static boolean isForeignKeyViolation(SQLException e) {
        // MySQL: SQLState 23000 / errorCode 1451 o mensaje conteniendo 'foreign key'
        return "23000".equals(e.getSQLState())
                || e.getErrorCode() == 1451
                || (e.getMessage() != null && e.getMessage().toLowerCase().contains("foreign key"));
    }

    private void softDeleteUsuario(int id) {
        final String sql = "UPDATE Usuarios SET activo=0, actualizado_en=NOW() WHERE id=?";
        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            int n = ps.executeUpdate();
            if (n > 0) {
                JOptionPane.showMessageDialog(panelMain, "Usuario desactivado.");
                cargarUsuarios();
            } else {
                JOptionPane.showMessageDialog(panelMain, "No se pudo desactivar el usuario.");
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(panelMain, "Error al desactivar: " + ex.getMessage());
        }
    }

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
    private static String nvl(String s) { return s != null ? s : ""; }
}
