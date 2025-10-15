import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ModificarSucursal {
    private JPanel panelDatos;
    private JTextField txtNombre;
    private JTextField txtDireccion;
    private JTextField txtTelefono;
    private JComboBox<String> cmbActivo;
    private JPanel panelInfo;
    private JPanel panelBotones;
    private JButton btnModificar;   // en tu .form se llama así: lo reutilizamos como "Modificar"
    private JButton btnSalir;
    private JPanel panelMain;

    // --- contexto ---
    private final int sucursalIdEditar;
    private final int usuarioId;
    private final int sucursalIdActual;
    private Runnable onSaved;

    public ModificarSucursal(int sucursalIdEditar, int usuarioId, int sucursalIdActual) {
        this.sucursalIdEditar = sucursalIdEditar;
        this.usuarioId = usuarioId;
        this.sucursalIdActual = sucursalIdActual;

        // Modelo del combo (Sí/No)
        if (cmbActivo != null && cmbActivo.getItemCount() == 0) {
            cmbActivo.setModel(new DefaultComboBoxModel<>(new String[]{"Sí", "No"}));
        }

        // Cambiamos el texto del botón para que sea claro
        if (btnModificar != null) btnModificar.setText("Modificar");

        // Listeners
        if (btnModificar != null) btnModificar.addActionListener(e -> guardarCambios());
        if (btnSalir   != null) btnSalir.addActionListener(e -> cerrarDialogo());

        // Cargar datos actuales de la sucursal
        cargarSucursal();
    }

    public void setOnSaved(Runnable onSaved) { this.onSaved = onSaved; }

    /** Crea un JDialog modal con este formulario. */
    public static JDialog createDialog(Window owner, int sucursalIdEditar, int usuarioId, int sucursalIdActual, Runnable onSaved) {
        ModificarSucursal ui = new ModificarSucursal(sucursalIdEditar, usuarioId, sucursalIdActual);
        ui.setOnSaved(onSaved);

        JDialog d = new JDialog(owner, "Modificar sucursal", Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(ui.panelMain);
        d.setMinimumSize(new Dimension(640, 380));
        d.pack();
        d.setLocationRelativeTo(owner);
        return d;
    }

    // ================= Lógica =================
    private void cargarSucursal() {
        final String sql = "SELECT nombre, direccion, telefono, activo FROM sucursales WHERE id = ?";

        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, sucursalIdEditar);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    JOptionPane.showMessageDialog(panelMain, "No se encontró la sucursal (id=" + sucursalIdEditar + ").");
                    cerrarDialogo();
                    return;
                }
                if (txtNombre    != null) txtNombre.setText(nvl(rs.getString("nombre")));
                if (txtDireccion != null) txtDireccion.setText(nvl(rs.getString("direccion")));
                if (txtTelefono  != null) txtTelefono.setText(nvl(rs.getString("telefono")));
                if (cmbActivo    != null) cmbActivo.setSelectedIndex(rs.getInt("activo") == 1 ? 0 : 1);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(panelMain, "Error al cargar sucursal: " + ex.getMessage());
            ex.printStackTrace();
            cerrarDialogo();
        }
    }

    private void guardarCambios() {
        String nombre    = safe(txtNombre);
        String direccion = safe(txtDireccion);
        String telefono  = safe(txtTelefono);
        int activo       = parseActivo(cmbActivo); // 1/0

        if (nombre.isBlank() || direccion.isBlank() || telefono.isBlank()) {
            JOptionPane.showMessageDialog(panelMain, "Completa nombre, dirección y teléfono.", "Faltan datos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final String sql = "UPDATE sucursales SET nombre=?, direccion=?, telefono=?, activo=? WHERE id=?";

        try (Connection con = DB.get()) {

            // Variables de sesión para triggers de bitácora
            if (usuarioId > 0) {
                try (PreparedStatement p = con.prepareStatement("SET @app_user_id = ?")) {
                    p.setInt(1, usuarioId);
                    p.executeUpdate();
                }
            }
            if (sucursalIdActual > 0) {
                try (PreparedStatement p = con.prepareStatement("SET @app_sucursal_id = ?")) {
                    p.setInt(1, sucursalIdActual);
                    p.executeUpdate();
                }
            }

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, nombre);
                ps.setString(2, direccion);
                ps.setString(3, telefono);
                ps.setInt   (4, activo);
                ps.setInt   (5, sucursalIdEditar);

                int n = ps.executeUpdate();
                if (n > 0) {
                    JOptionPane.showMessageDialog(panelMain, "Sucursal modificada correctamente.");
                    if (onSaved != null) onSaved.run(); // refresca la tabla en CRUD
                    cerrarDialogo();
                } else {
                    JOptionPane.showMessageDialog(panelMain, "No hubo cambios.");
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(panelMain, "Error al modificar: " + ex.getMessage(), "BD", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void cerrarDialogo() {
        Window w = SwingUtilities.getWindowAncestor(panelMain);
        if (w instanceof JDialog d) d.dispose();
    }

    // ==== helpers ====
    private static String safe(JTextField tf) { return tf != null && tf.getText() != null ? tf.getText().trim() : ""; }
    private static String nvl(String s) { return s != null ? s : ""; }
    private static int parseActivo(JComboBox<String> combo) {
        if (combo == null || combo.getSelectedItem() == null) return 1;
        String s = combo.getSelectedItem().toString().trim().toLowerCase();
        return (s.startsWith("s") || s.equals("1") || s.equals("true")) ? 1 : 0;
    }
}
