import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.Arrays;
import java.util.List;

public class ModificarTrabajador {
    private JPanel panelInfo;
    private JPanel panelBotones;
    private JButton btnModificar;
    private JButton btnSalir;
    private JPanel panelMain;
    private JComboBox<ComboItem> cmbSucursal;
    private JComboBox<String> cmbPuesto;
    private JTextField txtCorreo;
    private JTextField txtNombre;
    private JTextField txtTelefono;
    private JComboBox<String> cmbActivo;

    // ---- contexto ----
    private final int trabajadorId;     // ID a editar (trabajadores.id)
    private final int usuarioId;        // para @app_user_id
    private final int sucursalIdActual; // para @app_sucursal_id
    private Runnable onSaved;           // callback para refrescar tablas al guardar

    public ModificarTrabajador(int trabajadorId, int usuarioId, int sucursalIdActual) {
        this.trabajadorId = trabajadorId;
        this.usuarioId = usuarioId;
        this.sucursalIdActual = sucursalIdActual;

        initCombos();
        cablearEventos();
        cargarTrabajador(); // rellena campos
    }

    public void setOnSaved(Runnable onSaved) { this.onSaved = onSaved; }

    /** Abre el formulario como diálogo modal. */
    public static JDialog createDialog(Window owner, int trabajadorId, int usuarioId, int sucursalIdActual, Runnable onSaved) {
        ModificarTrabajador ui = new ModificarTrabajador(trabajadorId, usuarioId, sucursalIdActual);
        ui.setOnSaved(onSaved);

        JDialog d = new JDialog(owner, "Modificar trabajador", Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(ui.panelMain);
        d.setMinimumSize(new Dimension(720, 460));
        d.pack();
        d.setLocationRelativeTo(owner);
        return d;
    }

    // ====================== UI / eventos ======================
    private void initCombos() {
        // Activo
        if (cmbActivo != null && cmbActivo.getItemCount() == 0) {
            cmbActivo.setModel(new DefaultComboBoxModel<>(new String[]{"Sí", "No"}));
        }
        // Puestos (ajusta si necesitas otros)
        if (cmbPuesto != null && cmbPuesto.getItemCount() == 0) {
            List<String> puestos = Arrays.asList("Asesor", "Cajero", "DBA", "Administrador", "Supervisor");
            for (String p : puestos) cmbPuesto.addItem(p);
        }
        // Sucursales activas
        cargarSucursalesCombo(-1); // seleccionaremos la correcta al cargar datos
    }

    private void cablearEventos() {
        if (btnModificar != null) btnModificar.addActionListener(e -> guardarCambios());
        if (btnSalir != null) btnSalir.addActionListener(e -> cerrar());
    }

    // ====================== Carga ======================
    private void cargarTrabajador() {
        final String sql = "SELECT nombre, correo, telefono, sucursal_id, puesto, activo FROM trabajadores WHERE id = ?";
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, trabajadorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    JOptionPane.showMessageDialog(panelMain, "No se encontró el trabajador (id=" + trabajadorId + ").");
                    cerrar();
                    return;
                }
                if (txtNombre   != null) txtNombre.setText(nvl(rs.getString("nombre")));
                if (txtCorreo   != null) txtCorreo.setText(nvl(rs.getString("correo")));
                if (txtTelefono != null) txtTelefono.setText(nvl(rs.getString("telefono")));
                int sucId = rs.getInt("sucursal_id");
                cargarSucursalesCombo(sucId); // repobla y selecciona
                if (cmbPuesto != null) cmbPuesto.setSelectedItem(nvl(rs.getString("puesto")));
                if (cmbActivo != null) cmbActivo.setSelectedIndex(rs.getInt("activo") == 1 ? 0 : 1);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(panelMain, "Error al cargar trabajador: " + ex.getMessage());
            ex.printStackTrace();
            cerrar();
        }
    }

    private void cargarSucursalesCombo(int selectedId) {
        if (cmbSucursal == null) return;
        cmbSucursal.removeAllItems();
        ComboItem selectLater = null;
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement("SELECT id, nombre FROM sucursales WHERE activo=1 ORDER BY nombre");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ComboItem it = new ComboItem(rs.getInt(1), rs.getString(2));
                cmbSucursal.addItem(it);
                if (it.id == selectedId) selectLater = it;
            }
            if (selectLater != null) cmbSucursal.setSelectedItem(selectLater);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(panelMain, "Error al cargar sucursales: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ====================== Guardar ======================
    private void guardarCambios() {
        String nombre   = tx(txtNombre);
        String correo   = tx(txtCorreo);
        String telefono = tx(txtTelefono);
        ComboItem suc   = (ComboItem) (cmbSucursal != null ? cmbSucursal.getSelectedItem() : null);
        int sucursalId  = (suc != null ? suc.id : -1);
        String puesto   = (cmbPuesto != null && cmbPuesto.getSelectedItem()!=null) ? cmbPuesto.getSelectedItem().toString().trim() : "";
        int activo      = parseActivo(cmbActivo);

        if (nombre.isBlank() || correo.isBlank() || telefono.isBlank() || sucursalId <= 0 || puesto.isBlank()) {
            JOptionPane.showMessageDialog(panelMain, "Completa nombre, correo, teléfono, sucursal y puesto.");
            return;
        }
        if (!correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            JOptionPane.showMessageDialog(panelMain, "Correo inválido.");
            return;
        }

        final String sql = """
            UPDATE trabajadores
            SET nombre=?, correo=?, telefono=?, sucursal_id=?, puesto=?, activo=?, actualizado_en=CURRENT_TIMESTAMP
            WHERE id=?
            """;

        try (Connection con = DB.get()) {
            // variables de sesión para triggers de bitácora
            if (usuarioId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_user_id=?")) { p.setInt(1, usuarioId); p.executeUpdate(); }
            if (sucursalIdActual > 0) try (PreparedStatement p = con.prepareStatement("SET @app_sucursal_id=?")) { p.setInt(1, sucursalIdActual); p.executeUpdate(); }

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, nombre);
                ps.setString(2, correo);
                ps.setString(3, telefono);
                ps.setInt(4, sucursalId);
                ps.setString(5, puesto);
                ps.setInt(6, activo);
                ps.setInt(7, trabajadorId);

                int n = ps.executeUpdate();
                if (n > 0) {
                    JOptionPane.showMessageDialog(panelMain, "Trabajador modificado correctamente.");
                    if (onSaved != null) onSaved.run();
                    cerrar();
                } else {
                    JOptionPane.showMessageDialog(panelMain, "No hubo cambios.");
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(panelMain, "Error BD: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // ====================== util ======================
    private void cerrar() {
        Window w = SwingUtilities.getWindowAncestor(panelMain);
        if (w instanceof JDialog d) d.dispose();
    }
    private static String tx(JTextField t){ return (t!=null && t.getText()!=null) ? t.getText().trim() : ""; }
    private static String nvl(String s){ return s != null ? s : ""; }
    private static int parseActivo(JComboBox<String> c){
        if (c == null || c.getSelectedItem()==null) return 1;
        String s = c.getSelectedItem().toString().trim().toLowerCase();
        return (s.startsWith("s") || s.equals("1") || s.equals("true")) ? 1 : 0;
    }

    /** Item para el combo de sucursales. */
    static class ComboItem {
        final int id; final String nombre;
        ComboItem(int id, String nombre){ this.id=id; this.nombre=nombre; }
        @Override public String toString(){ return nombre + " (id " + id + ")"; }
    }
}
