import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.Objects;

public class AgregarServicio {
    private JPanel panelDatos;
    private JTextField txtNombre;
    private JTextField txtDescripcion;
    private JComboBox<String> cmbActivo;
    private JComboBox<CategoriaItem> cmbCategoria;
    private JTextField txtPrecio;
    private JPanel panelBotones;
    private JButton btnAgregar;
    private JButton btnSalir;
    private JPanel panelInfo;
    private JPanel panelMain;

    // contexto (para bitácoras/triggers)
    private final int usuarioId;
    private final int sucursalId;

    // callback para refrescar la tabla del CRUD
    private final Runnable onSaved;

    // ---------- constructores ----------
    public AgregarServicio() {
        this(-1, -1, null);
    }
    public AgregarServicio(int usuarioId, int sucursalId, Runnable onSaved) {
        this.usuarioId = usuarioId;
        this.sucursalId = sucursalId;
        this.onSaved    = onSaved;

        configurarUI();
        cargarCategorias();
        cargarActivo();

        // acciones
        if (btnAgregar != null) btnAgregar.addActionListener(e -> guardar());
        if (btnSalir   != null) btnSalir.addActionListener(e -> {
            Window w = SwingUtilities.getWindowAncestor(panelMain);
            if (w instanceof JDialog d) d.dispose();
        });
    }

    /** Abre como diálogo listo para usar desde CRUDServicios. */
    public static JDialog createDialog(Window owner, int usuarioId, int sucursalId, Runnable onSaved) {
        AgregarServicio ui = new AgregarServicio(usuarioId, sucursalId, onSaved);
        JDialog d = new JDialog(owner, "Agregar servicio", Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(ui.panelMain);
        d.setMinimumSize(new Dimension(720, 420));
        d.pack();
        d.setLocationRelativeTo(owner);
        return d;
    }

    // ===================== UI =====================
    private void configurarUI() {
        if (panelMain != null) panelMain.setBorder(new EmptyBorder(12,12,12,12));
        if (txtPrecio != null) {
            // hint simple
            txtPrecio.setToolTipText("Ejemplo: 0, 10.50, 99");
        }
    }

    private void cargarActivo() {
        if (cmbActivo == null) return;
        DefaultComboBoxModel<String> m = new DefaultComboBoxModel<>();
        m.addElement("Sí");
        m.addElement("No");
        cmbActivo.setModel(m);
        cmbActivo.setSelectedIndex(0);
    }

    private void cargarCategorias() {
        if (cmbCategoria == null) return;
        DefaultComboBoxModel<CategoriaItem> m = new DefaultComboBoxModel<>();
        final String sql = "SELECT id, nombre FROM categorias_servicio WHERE activo=1 ORDER BY nombre";
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                m.addElement(new CategoriaItem(rs.getInt("id"), nvl(rs.getString("nombre"))));
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(panelMain, "Error al cargar categorías: " + e.getMessage());
        }
        cmbCategoria.setModel(m);
        if (m.getSize() > 0) cmbCategoria.setSelectedIndex(0);
    }

    // ===================== Guardar =====================
    private void guardar() {
        String nombre = safe(txtNombre);
        String descr  = safe(txtDescripcion);
        String precioTx = safe(txtPrecio);
        CategoriaItem cat = (CategoriaItem) (cmbCategoria != null ? cmbCategoria.getSelectedItem() : null);
        int activo = (Objects.equals(cmbActivo != null ? cmbActivo.getSelectedItem() : "Sí", "Sí")) ? 1 : 0;

        if (nombre.isBlank()) {
            JOptionPane.showMessageDialog(panelMain, "El nombre es obligatorio.");
            if (txtNombre != null) txtNombre.requestFocus();
            return;
        }
        if (cat == null) {
            JOptionPane.showMessageDialog(panelMain, "Selecciona una categoría.");
            if (cmbCategoria != null) cmbCategoria.requestFocus();
            return;
        }

        BigDecimal precio = parsePrecio(precioTx);
        if (precio == null) {
            JOptionPane.showMessageDialog(panelMain, "Precio no válido. Usa números, ej. 0, 10.50");
            if (txtPrecio != null) txtPrecio.requestFocus();
            return;
        }
        if (precio.signum() < 0) {
            JOptionPane.showMessageDialog(panelMain, "El precio no puede ser negativo.");
            if (txtPrecio != null) txtPrecio.requestFocus();
            return;
        }

        final String sql = """
        INSERT INTO servicios (categoria_id, nombre, descripcion, precio, activo)
        VALUES (?,?,?,?,?)
        """;
        try (Connection con = DB.get()) {
            // variables de sesión para triggers/bitácora (si existen)
            if (usuarioId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_user_id=?")) { p.setInt(1, usuarioId); p.executeUpdate(); }
            if (sucursalId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_sucursal_id=?")) { p.setInt(1, sucursalId); p.executeUpdate(); }

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, cat.id);
                ps.setString(2, nombre);
                ps.setString(3, descr.isBlank() ? null : descr);
                ps.setBigDecimal(4, precio);
                ps.setInt(5, activo);

                ps.executeUpdate();
            }

            JOptionPane.showMessageDialog(panelMain, "Servicio agregado correctamente.");
            if (onSaved != null) onSaved.run();
            // cerrar
            Window w = SwingUtilities.getWindowAncestor(panelMain);
            if (w instanceof JDialog d) d.dispose();

        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Duplicate")) {
                JOptionPane.showMessageDialog(panelMain, "Ya existe un servicio con ese nombre.");
            } else {
                JOptionPane.showMessageDialog(panelMain, "Error BD: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    // ===================== helpers =====================
    private static BigDecimal parsePrecio(String s) {
        if (s == null) return BigDecimal.ZERO;
        s = s.trim();
        if (s.isEmpty()) return BigDecimal.ZERO;
        // admite coma o punto
        s = s.replace(",", ".");
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String safe(JTextField tf) { return tf != null ? tf.getText().trim() : ""; }
    private static String nvl(String s) { return s != null ? s : ""; }

    // item para el combo de categorías
    static class CategoriaItem {
        final int id; final String nombre;
        CategoriaItem(int id, String nombre) { this.id = id; this.nombre = nombre; }
        @Override public String toString() { return nombre; }
    }
}
