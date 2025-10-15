import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public class AgregarTrabajador {
    private JPanel panelDatos;
    private JTextField txtNombre;
    private JTextField txtDireccion; // <- si tu formulario tiene “Correo” pero el bound field se llama txtDireccion, lo usamos como fallback
    private JTextField txtTelefono;
    private JComboBox cmbActivo;
    private JPanel panelInfo;
    private JPanel panelBotones;
    private JButton btnAgregar;
    private JButton btnSalir;
    private JPanel panelMain;
    private JComboBox cmbSucursal;
    private JComboBox cmbPuesto;

    // **AGREGA** este campo en el .form si puedes (lo ideal es que el bound field real se llame txtCorreo)
    private JTextField txtCorreo; // si no existe en tu .form, no pasa nada: usamos txtDireccion como correo

    private final int usuarioId;
    private final int sucursalIdActual;
    private Runnable onSaved;

    public AgregarTrabajador(int usuarioId, int sucursalIdActual) {
        this.usuarioId = usuarioId;
        this.sucursalIdActual = sucursalIdActual;

        initCombos();

        if (btnAgregar != null) btnAgregar.addActionListener(e -> guardar());
        if (btnSalir   != null) btnSalir.addActionListener(e -> cerrarDialogo());
    }

    public void setOnSaved(Runnable onSaved) { this.onSaved = onSaved; }

    public static JDialog createDialog(Window owner, int usuarioId, int sucursalIdActual, Runnable onSaved) {
        AgregarTrabajador ui = new AgregarTrabajador(usuarioId, sucursalIdActual);
        ui.setOnSaved(onSaved);
        JDialog d = new JDialog(owner, "Agregar trabajador", Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(ui.panelMain);
        d.setMinimumSize(new Dimension(720, 460));
        d.pack();
        d.setLocationRelativeTo(owner);
        return d;
    }

    private void initCombos() {
        if (cmbActivo != null && cmbActivo.getItemCount() == 0) {
            cmbActivo.setModel(new DefaultComboBoxModel<>(new String[]{"Sí", "No"}));
            cmbActivo.setSelectedIndex(1); // por si quieres default “No”; cámbialo a 0 si quieres “Sí”
        }
        if (cmbPuesto != null && cmbPuesto.getItemCount() == 0) {
            for (String p : Arrays.asList("Administrador", "Cajero/Asesor", "Asesor", "Asesor/Contador", "Cajero/Contador/Asesor"))
                cmbPuesto.addItem(p);
            cmbPuesto.setSelectedIndex(0);
        }
        cargarSucursalesCombo();
    }

    private void cargarSucursalesCombo() {
        if (cmbSucursal == null) return;
        cmbSucursal.removeAllItems();
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id, nombre FROM sucursales WHERE activo=1 ORDER BY nombre");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                cmbSucursal.addItem(new ComboItem(rs.getInt(1), rs.getString(2)));
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(panelMain, "Error al cargar sucursales: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void guardar() {
        String nombre   = tx(txtNombre);
        // correo: toma txtCorreo; si no existe en tu .form, toma txtDireccion como “correo” (fallback)
        String correo   = !tx(txtCorreo).isBlank() ? tx(txtCorreo) : tx(txtDireccion);
        String telefono = tx(txtTelefono);
        ComboItem suc   = (ComboItem) (cmbSucursal != null ? cmbSucursal.getSelectedItem() : null);
        int sucursalId  = (suc != null ? suc.id : -1);
        String puesto   = (cmbPuesto != null && cmbPuesto.getSelectedItem()!=null) ? cmbPuesto.getSelectedItem().toString().trim() : "";
        int activo      = parseActivo(cmbActivo);

        if (nombre.isBlank() || correo.isBlank() || telefono.isBlank() || sucursalId <= 0 || puesto.isBlank()) {
            JOptionPane.showMessageDialog(panelMain, "Completa nombre, correo, teléfono, sucursal y puesto.");
            return;
        }

        // (opcional) validación simple de correo
        if (!correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            JOptionPane.showMessageDialog(panelMain, "Correo inválido.");
            return;
        }

        final String sql = """
            INSERT INTO trabajadores
            (nombre, correo, telefono, sucursal_id, puesto, fecha_contratacion, activo)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        LocalDate hoy = LocalDate.now();

        try (Connection con = DB.get()) {
            if (usuarioId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_user_id=?")) { p.setInt(1, usuarioId); p.executeUpdate(); }
            if (sucursalIdActual > 0) try (PreparedStatement p = con.prepareStatement("SET @app_sucursal_id=?")) { p.setInt(1, sucursalIdActual); p.executeUpdate(); }

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, nombre);
                ps.setString(2, correo);                 // <--- ya no va NULL
                ps.setString(3, telefono);
                ps.setInt(4, sucursalId);
                ps.setString(5, puesto);
                ps.setDate(6, Date.valueOf(hoy));
                ps.setInt(7, activo);

                int n = ps.executeUpdate();
                if (n > 0) {
                    JOptionPane.showMessageDialog(panelMain, "Trabajador agregado correctamente.");
                    if (onSaved != null) onSaved.run();
                    cerrarDialogo();
                } else {
                    JOptionPane.showMessageDialog(panelMain, "No se pudo agregar el trabajador.");
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(panelMain, "Error BD: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void cerrarDialogo() {
        Window w = SwingUtilities.getWindowAncestor(panelMain);
        if (w instanceof JDialog d) d.dispose();
    }

    private static String tx(JTextField t){ return (t!=null && t.getText()!=null) ? t.getText().trim() : ""; }
    private static int parseActivo(JComboBox combo){
        if (combo == null || combo.getSelectedItem()==null) return 1;
        String s = combo.getSelectedItem().toString().trim().toLowerCase();
        return (s.startsWith("s") || s.equals("1") || s.equals("true")) ? 1 : 0;
    }

    static class ComboItem {
        final int id; final String nombre;
        ComboItem(int id, String nombre){ this.id=id; this.nombre=nombre; }
        @Override public String toString(){ return nombre + " (id " + id + ")"; }
    }
}
