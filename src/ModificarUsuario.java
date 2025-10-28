import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.Objects;

public class ModificarUsuario {
    private JPanel panelInfo;
    private JPanel panelDatos;
    private JTextField txtNombre;       // <- nombre de usuario (login)
    private JTextField txtContrasena;   // <- si lo dejas vacío, no se cambia
    private JComboBox cmbActivo;
    private JComboBox cmbUsuario;       // lista de usuarios a editar
    private JPanel panelBotones;
    private JButton btnModificar;
    private JButton btnSalir;
    private JPanel panelMain;

    // ===== contexto =====
    private final Window owner;
    private final int actorUsuarioId;   // @app_user_id
    private final int actorSucursalId;  // @app_sucursal_id
    private Runnable onSaved;

    // seleccionado
    private Integer currentUserId = null;     // Usuarios.id del seleccionado

    public ModificarUsuario(Window owner, int actorUsuarioId, int actorSucursalId) {
        this.owner = owner;
        this.actorUsuarioId = actorUsuarioId;
        this.actorSucursalId = actorSucursalId;

        if (cmbActivo != null && cmbActivo.getItemCount() == 0) {
            cmbActivo.setModel(new DefaultComboBoxModel<>(new String[]{"Sí", "No"}));
        }

        cargarUsuariosEnCombo();

        if (cmbUsuario != null) {
            cmbUsuario.addActionListener(e -> cargarDatosSeleccionado());
        }
        if (btnModificar != null) btnModificar.addActionListener(e -> guardarCambios());
        if (btnSalir != null)     btnSalir.addActionListener(e -> cerrar());

        // disparar carga inicial
        if (cmbUsuario != null && cmbUsuario.getItemCount() > 0) {
            cmbUsuario.setSelectedIndex(0);
            cargarDatosSeleccionado();
        }
    }

    public void setOnSaved(Runnable r) { this.onSaved = r; }

    /** Crea y devuelve el diálogo (úsalo desde CRUDUsuarios). */
    public static JDialog createDialog(Window owner, int actorUsuarioId, int actorSucursalId, Runnable onSaved) {
        ModificarUsuario ui = new ModificarUsuario(owner, actorUsuarioId, actorSucursalId);
        ui.setOnSaved(onSaved);

        JDialog d = new JDialog(owner, "Modificar usuario", Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(ui.panelMain);
        d.setMinimumSize(new Dimension(720, 460));
        d.pack();
        d.setLocationRelativeTo(owner);
        return d;
    }

    // ================== Carga de datos ==================
    private void cargarUsuariosEnCombo() {
        if (cmbUsuario == null) return;
        cmbUsuario.removeAllItems();

        final String sql = "SELECT id, usuario, nombre FROM Usuarios ORDER BY usuario";
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String login = nvl(rs.getString("usuario"));
                String nombre = nvl(rs.getString("nombre"));
                ((JComboBox<UserItem>) (JComboBox<?>) cmbUsuario).addItem(new UserItem(id, login, nombre));
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(panelMain, "Error al cargar usuarios: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void cargarDatosSeleccionado() {
        UserItem it = getSelectedUserItem();
        if (it == null) return;
        currentUserId = it.id;

        final String sql = "SELECT usuario, `contraseña` AS pass, activo FROM Usuarios WHERE id = ?";
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, currentUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String login = nvl(rs.getString("usuario"));
                    String pass  = rs.getString("pass"); // puede ser NULL si migraste a hash
                    int    act   = rs.getInt("activo");

                    if (txtNombre != null)     txtNombre.setText(login);
                    if (txtContrasena != null) txtContrasena.setText(pass != null ? pass : "");
                    if (cmbActivo != null)     cmbActivo.setSelectedItem(act == 1 ? "Sí" : "No");
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(panelMain, "Error al cargar datos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private UserItem getSelectedUserItem() {
        if (cmbUsuario == null || cmbUsuario.getSelectedItem() == null) return null;
        Object o = cmbUsuario.getSelectedItem();
        if (o instanceof UserItem) return (UserItem) o;
        // por si el diseñador dejó sin genéricos
        return null;
    }

    // ================== Guardar ==================
    private void guardarCambios() {
        if (currentUserId == null) {
            JOptionPane.showMessageDialog(panelMain, "Selecciona un usuario.");
            return;
        }

        String nuevoUsuario = tx(txtNombre);
        String nuevaPass    = tx(txtContrasena);
        int    activo       = parseActivo(cmbActivo);

        if (nuevoUsuario.isBlank()) {
            JOptionPane.showMessageDialog(panelMain, "El nombre de usuario no puede estar vacío.");
            return;
        }
        if (existeUsuarioConLoginExcepto(nuevoUsuario, currentUserId)) {
            JOptionPane.showMessageDialog(panelMain, "Ese nombre de usuario ya existe. Elige otro.");
            return;
        }

        // Construye el UPDATE dinámico (no cambiar contraseña si viene vacía)
        String sql;
        String hashNueva = null;
        if (nuevaPass.isBlank()) {
            sql = "UPDATE Usuarios SET usuario=?, activo=?, actualizado_en=NOW() WHERE id=?";
        } else {
            hashNueva = Passwords.hash(nuevaPass.toCharArray());
            sql = "UPDATE Usuarios SET usuario=?, password_hash=?, `contraseña`='', "
                    + "must_change_password=1, last_password_change=NULL, "
                    + "activo=?, actualizado_en=NOW() WHERE id=?";
        }

        try (Connection con = DB.get()) {
            // variables de sesión para triggers/bitácora (si las usas)
            if (actorUsuarioId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_user_id=?")) {
                p.setInt(1, actorUsuarioId); p.executeUpdate();
            }
            if (actorSucursalId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_sucursal_id=?")) {
                p.setInt(1, actorSucursalId); p.executeUpdate();
            }

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                int idx = 1;
                ps.setString(idx++, nuevoUsuario);
                if (!nuevaPass.isBlank()) ps.setString(idx++, hashNueva);
                ps.setInt(idx++, activo);
                ps.setInt(idx  , currentUserId);

                int n = ps.executeUpdate();
                if (n > 0) {
                    if (nuevaPass.isBlank()) {
                        JOptionPane.showMessageDialog(panelMain, "Usuario modificado correctamente.");
                    } else {
                        JOptionPane.showMessageDialog(panelMain,
                                "Contraseña temporal asignada.\nEl usuario deberá cambiarla al iniciar sesión.");
                    }
                    if (onSaved != null) onSaved.run();
                    cerrar();
                } else {
                    JOptionPane.showMessageDialog(panelMain, "No se modificó ningún registro.");
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(panelMain, "Error BD: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Limpia el campo visual por seguridad
            txtContrasena.setText("");
            // (opcional) borra el String de la variable local
            nuevaPass = "";
        }
    }

    private boolean existeUsuarioConLoginExcepto(String login, int excluirId) {
        final String sql = "SELECT 1 FROM Usuarios WHERE usuario = ? AND id <> ? LIMIT 1";
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, login);
            ps.setInt(2, excluirId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            // si algo falla, para no romper la UX asumimos que "existe"
            return true;
        }
    }

    private void cerrar() {
        Window w = SwingUtilities.getWindowAncestor(panelMain);
        if (w instanceof JDialog d) d.dispose();
    }

    // ===== helpers =====
    private static String tx(JTextField t) { return (t != null && t.getText() != null) ? t.getText().trim() : ""; }
    private static int parseActivo(JComboBox c) {
        if (c == null || c.getSelectedItem() == null) return 1;
        String s = Objects.toString(c.getSelectedItem(), "").trim().toLowerCase();
        return (s.startsWith("s") || s.equals("1") || s.equals("true")) ? 1 : 0;
    }
    private static String nvl(String s) { return s != null ? s : ""; }

    // item para combo
    static class UserItem {
        final int id; final String usuario; final String nombre;
        UserItem(int id, String usuario, String nombre) { this.id=id; this.usuario=usuario; this.nombre=nombre; }
        @Override public String toString() { return usuario + " — " + nombre + " (id " + id + ")"; }
    }
}
