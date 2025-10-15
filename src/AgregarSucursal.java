import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AgregarSucursal {
    private JPanel panelMain;
    private JPanel panelInfo;
    private JPanel panelDatos;
    private JPanel panelBotones;
    private JButton btnAgregar;
    private JButton btnSalir;
    private JTextField txtNombre;
    private JTextField txtDireccion;
    private JTextField txtTelefono;
    private JComboBox<String> cmbActivo;

    // --- contexto ---
    private final int usuarioId;
    private final int sucursalIdActual; // sucursal desde donde opera el usuario
    private Runnable onSaved;           // callback opcional para refrescar tablas

    public AgregarSucursal(int usuarioId, int sucursalIdActual) {
        this.usuarioId = usuarioId;
        this.sucursalIdActual = sucursalIdActual;

        // Modelo combo
        if (cmbActivo != null && cmbActivo.getItemCount() == 0) {
            cmbActivo.setModel(new DefaultComboBoxModel<>(new String[]{"Sí", "No"}));
            cmbActivo.setSelectedIndex(0);
        }

        // Listeners
        if (btnAgregar != null) btnAgregar.addActionListener(e -> guardar());
        if (btnSalir   != null) btnSalir.addActionListener(e -> cerrarDialogo());
    }

    public void setOnSaved(Runnable onSaved) { this.onSaved = onSaved; }

    /** Crea un JDialog con este formulario (útil desde botones de CRUDSucursales). */
    public static JDialog createDialog(Window owner, int usuarioId, int sucursalIdActual, Runnable onSaved) {
        AgregarSucursal ui = new AgregarSucursal(usuarioId, sucursalIdActual);
        ui.setOnSaved(onSaved);

        JDialog d = new JDialog(owner, "Agregar sucursal", Dialog.ModalityType.APPLICATION_MODAL);
        d.setContentPane(ui.panelMain);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setMinimumSize(new Dimension(620, 360));
        d.pack();
        d.setLocationRelativeTo(owner);
        return d;
    }

    // ================== lógica ==================
    private void guardar() {
        String nombre    = safe(txtNombre);
        String direccion = safe(txtDireccion);
        String telefono  = safe(txtTelefono);
        int activo       = parseActivo(cmbActivo);

        if (nombre.isBlank() || direccion.isBlank() || telefono.isBlank()) {
            JOptionPane.showMessageDialog(panelMain, "Completa nombre, dirección y teléfono.", "Faltan datos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final String sql = "INSERT INTO sucursales (nombre, direccion, telefono, activo) VALUES (?,?,?,?)";

        try (Connection con = DB.get()) {

            // Variables de sesión para los triggers de bitácora:
            if (usuarioId > 0) {
                try (PreparedStatement ps = con.prepareStatement("SET @app_user_id = ?")) {
                    ps.setInt(1, usuarioId);
                    ps.executeUpdate();
                }
            }
            if (sucursalIdActual > 0) {
                try (PreparedStatement ps = con.prepareStatement("SET @app_sucursal_id = ?")) {
                    ps.setInt(1, sucursalIdActual);
                    ps.executeUpdate();
                }
            }

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, nombre);
                ps.setString(2, direccion);
                ps.setString(3, telefono);
                ps.setInt(4, activo); // 1/0
                int n = ps.executeUpdate();
                if (n > 0) {
                    JOptionPane.showMessageDialog(panelMain, "Sucursal agregada correctamente.");
                    if (onSaved != null) onSaved.run(); // refresca la tabla si te pasaron callback
                    cerrarDialogo();
                } else {
                    JOptionPane.showMessageDialog(panelMain, "No se pudo agregar la sucursal.", "Sin cambios", JOptionPane.WARNING_MESSAGE);
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(panelMain, "Error al guardar: " + ex.getMessage(), "BD", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void cerrarDialogo() {
        Window w = SwingUtilities.getWindowAncestor(panelMain);
        if (w instanceof JDialog d) d.dispose();
    }

    private static String safe(JTextField tf) { return tf != null && tf.getText() != null ? tf.getText().trim() : ""; }

    private static int parseActivo(JComboBox<String> combo) {
        if (combo == null || combo.getSelectedItem() == null) return 1;
        String s = combo.getSelectedItem().toString().trim().toLowerCase();
        return (s.startsWith("s") || s.equals("1") || s.equals("true")) ? 1 : 0;
    }
}
