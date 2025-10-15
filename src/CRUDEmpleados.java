import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.sql.*;

public class CRUDEmpleados {
    // --- UI (de tu .form) ---
    private JPanel panelMain;
    private JPanel panelBotones;
    private JButton btnAgregarEmpleado;
    private JButton btnModificarEmpleado;
    private JButton btnEliminarEmpleado;
    private JButton btnCancelar;
    private JPanel panelTabla;
    private JScrollPane scrTablaEmpleados;
    private JTable tblEmpleados;

    // --- contexto ---
    private final Window owner;     // <<<<<<<<<<  NUEVO
    private final int usuarioId;
    private final int sucursalId;

    // --- tabla ---
    private DefaultTableModel modelo;
    private TableRowSorter<DefaultTableModel> sorter;

    // ====== ctor ======
    public CRUDEmpleados(Window owner, int usuarioId, int sucursalId) {
        this.owner = owner;         // <<<<<<<<<<  guardamos el owner
        this.usuarioId = usuarioId;
        this.sucursalId = sucursalId;

        configurarTabla();
        cargarEmpleados();

        if (btnCancelar != null) {
            btnCancelar.addActionListener(e -> {
                Window w = getOwnerWindow();
                if (w instanceof JDialog d) d.dispose();
            });
        }
        if (btnAgregarEmpleado != null) {
            btnAgregarEmpleado.addActionListener(e -> {
                JDialog d = AgregarTrabajador.createDialog(
                        getOwnerWindow(), usuarioId, sucursalId, this::cargarEmpleados);
                d.setVisible(true);
            });
        }
        if (btnModificarEmpleado != null) {
            btnModificarEmpleado.addActionListener(e -> {
                Integer id = getSelectedEmpleadoId();
                if (id == null) {
                    JOptionPane.showMessageDialog(root(), "Selecciona un empleado para modificar.");
                    return;
                }
                JDialog d = ModificarTrabajador.createDialog(
                        getOwnerWindow(), id, usuarioId, sucursalId, this::cargarEmpleados);
                d.setVisible(true);
            });
        }
        if (btnEliminarEmpleado != null) {
            btnEliminarEmpleado.addActionListener(e -> eliminarEmpleadoSeleccionado());
        }
    }

    /** Crea y devuelve el diálogo contenedor. */
    public static JDialog createDialog(Window owner, int usuarioId, int sucursalId) {
        CRUDEmpleados ui = new CRUDEmpleados(owner, usuarioId, sucursalId); // <<<<<<<<
        JDialog d = new JDialog(owner, "Empleados", Dialog.ModalityType.MODELESS);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(ui.root());   // usa fallback si panelMain es null
        d.setMinimumSize(new Dimension(980, 560));
        d.pack();
        d.setLocationRelativeTo(owner);
        return d;
    }

    // ================= tabla =================
    private void configurarTabla() {
        if (tblEmpleados == null) return;
        String[] cols = {"ID","Nombre","Correo","Teléfono","Sucursal","Sucursal ID","Puesto",
                "Fecha contratación","Activo","Creado en","Actualizado en"};
        modelo = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r,int c){ return false; }
            @Override public Class<?> getColumnClass(int c){ return (c==0||c==5)?Integer.class:String.class; }
        };
        tblEmpleados.setModel(modelo);
        sorter = new TableRowSorter<>(modelo);
        tblEmpleados.setRowSorter(sorter);
        tblEmpleados.setRowHeight(26);
        tblEmpleados.setShowGrid(false);
        tblEmpleados.setIntercellSpacing(new Dimension(0,0));
        tblEmpleados.getTableHeader().setReorderingAllowed(false);

        JTableHeader h = tblEmpleados.getTableHeader();
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, 32));

        int[] widths = {60,200,240,120,220,95,140,140,70,160,160};
        for (int i = 0; i < Math.min(widths.length, tblEmpleados.getColumnCount()); i++) {
            tblEmpleados.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    public final void cargarEmpleados() {
        if (tblEmpleados == null) return;
        final String sql = """
            SELECT e.id, e.nombre, e.correo, e.telefono,
                   s.nombre AS sucursal, e.sucursal_id,
                   e.puesto, e.fecha_contratacion, e.activo,
                   e.creado_en, e.actualizado_en
            FROM trabajadores e
            LEFT JOIN sucursales s ON s.id = e.sucursal_id
            ORDER BY e.nombre
        """;
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            modelo.setRowCount(0);
            while (rs.next()) {
                modelo.addRow(new Object[]{
                        rs.getInt("id"),
                        nvl(rs.getString("nombre")),
                        nvl(rs.getString("correo")),
                        nvl(rs.getString("telefono")),
                        nvl(rs.getString("sucursal")),
                        rs.getInt("sucursal_id"),
                        nvl(rs.getString("puesto")),
                        rs.getDate("fecha_contratacion")!=null? rs.getDate("fecha_contratacion").toLocalDate().toString() : "",
                        rs.getInt("activo")==1? "Sí":"No",
                        ts(rs.getTimestamp("creado_en")),
                        ts(rs.getTimestamp("actualizado_en"))
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(root(), "Error al cargar empleados: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Integer getSelectedEmpleadoId() {
        if (tblEmpleados == null || modelo == null) return null;
        int viewRow = tblEmpleados.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = tblEmpleados.convertRowIndexToModel(viewRow);
        Object v = modelo.getValueAt(modelRow, 0);
        return (v instanceof Integer) ? (Integer) v : Integer.valueOf(v.toString());
    }

    private void eliminarEmpleadoSeleccionado() {
        Integer id = getSelectedEmpleadoId();
        if (id == null) { JOptionPane.showMessageDialog(root(), "Selecciona un empleado para eliminar."); return; }
        int ok = JOptionPane.showConfirmDialog(root(),
                "¿Seguro que deseas eliminar el empleado ID " + id + "?", "Confirmar",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;

        final String sql = "DELETE FROM trabajadores WHERE id=?"; // correcto
        try (Connection con = DB.get()) {
            if (usuarioId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_user_id=?")) { p.setInt(1, usuarioId); p.executeUpdate(); }
            if (sucursalId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_sucursal_id=?")) { p.setInt(1, sucursalId); p.executeUpdate(); }
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, id);
                int n = ps.executeUpdate();
                if (n > 0) { JOptionPane.showMessageDialog(root(), "Empleado eliminado."); cargarEmpleados(); }
                else      { JOptionPane.showMessageDialog(root(), "No se encontró el empleado."); }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(root(), "No se pudo eliminar (puede estar referenciado).\n" + e.getMessage());
            e.printStackTrace();
        }
    }

    // ===== helpers =====
    /** Devuelve el panel raíz existente: nunca null. */
    private Container root() {
        if (panelMain != null) return panelMain;
        if (panelTabla != null && panelTabla.getParent() != null) return panelTabla.getParent();
        if (panelTabla != null) return panelTabla;
        if (scrTablaEmpleados != null) return scrTablaEmpleados;
        JPanel r = new JPanel(new BorderLayout());
        if (panelTabla != null) r.add(panelTabla, BorderLayout.CENTER);
        if (panelBotones != null) r.add(panelBotones, BorderLayout.SOUTH);
        return r;
    }

    /** Siempre devuelve un Window válido sin tocar SwingUtilities con null. */
    private Window getOwnerWindow() {
        if (owner != null) return owner;                             // el que recibimos del que abre
        Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (active != null) return active;
        for (Frame f : Frame.getFrames()) if (f.isVisible()) return f;
        return JOptionPane.getRootFrame();
    }

    private static String nvl(String s) { return s != null ? s : ""; }
    private static String ts(Timestamp t) { return t != null ? t.toString() : ""; }
}
