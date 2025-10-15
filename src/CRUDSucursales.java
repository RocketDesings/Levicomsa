import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CRUDSucursales {
    private JPanel panelMain;
    private JTable tblSucursales;
    private JButton btnCancelar;
    private JScrollPane scrTablaSucursales;
    private JPanel panelTabla;
    private JPanel panelBotones;
    private JButton btnAgregarSucursal;
    private JButton btnModificarSucursal;
    private JButton btnEliminarSucursal;

    // ===== contexto de sesión =====
    private final int usuarioId;
    private final int sucursalId; // sucursal “actual” del usuario

    // ===== tabla =====
    private DefaultTableModel modelo;
    private TableRowSorter<DefaultTableModel> sorter;

    public CRUDSucursales(int usuarioId, int sucursalId) {
        this.usuarioId = usuarioId;
        this.sucursalId = sucursalId;

        configurarTabla();
        cargarSucursales();

        // acciones
        if (btnCancelar != null) {
            btnCancelar.addActionListener(e -> {
                Window w = SwingUtilities.getWindowAncestor(panelMain);
                if (w instanceof JDialog d) d.dispose();
            });
        }
        if (btnAgregarSucursal != null) {
            btnAgregarSucursal.addActionListener(e -> {
                JDialog dialog = AgregarSucursal.createDialog(
                        SwingUtilities.getWindowAncestor(panelMain),
                        usuarioId,           // pásale el usuario logueado
                        sucursalId,          // sucursal desde la que opera
                        this::cargarSucursales  // callback para refrescar al cerrar con éxito
                );
                dialog.setVisible(true);
            });
        }
        if (btnModificarSucursal != null) {
            btnModificarSucursal.addActionListener(e -> {
                Integer id = getSelectedSucursalId();
                if (id == null) {
                    JOptionPane.showMessageDialog(panelMain, "Selecciona una sucursal para modificar.");
                    return;
                }
                JDialog d = ModificarSucursal.createDialog(
                        SwingUtilities.getWindowAncestor(panelMain),
                        id,            // id de la sucursal a editar
                        usuarioId,     // quien edita (para trigger)
                        sucursalId     // sucursal desde la que opera (para trigger)
                        , this::cargarSucursales       // callback para refrescar tabla al guardar
                );
                d.setVisible(true);
            });
        }
        if (btnEliminarSucursal != null) {
            btnEliminarSucursal.addActionListener(e -> eliminarSucursalSeleccionada());
        }
    }

    /** Devuelve el panel raíz (útil si embebes este UI en otro contenedor). */
    public JComponent getComponent() { return panelMain; }

    /** Crea un JDialog MODELESS conteniendo este panel. */
    public static JDialog createDialog(Window owner, int usuarioId, int sucursalId) {
        CRUDSucursales ui = new CRUDSucursales(usuarioId, sucursalId);

        JDialog d = new JDialog(owner, "Sucursales", Dialog.ModalityType.MODELESS);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(ui.panelMain);
        d.setMinimumSize(new Dimension(800, 480));
        d.pack();
        d.setLocationRelativeTo(owner);

        // si necesitas recargar al mostrar, descomenta:
        // d.addWindowListener(new WindowAdapter() {
        //     @Override public void windowActivated(WindowEvent e) { ui.cargarSucursales(); }
        // });

        return d;
    }

    // ================= tabla =================
    private void configurarTabla() {
        String[] cols = {"ID", "Nombre", "Dirección", "Teléfono", "Activo", "Creado en"};
        modelo = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return switch (c) {
                    case 0 -> Integer.class;  // id
                    case 4 -> String.class;   // "Sí"/"No"
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
        tblSucursales.getTableHeader().setReorderingAllowed(false);

        JTableHeader h = tblSucursales.getTableHeader();
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, 32));

        // anchos sugeridos
        int[] widths = {60, 220, 340, 140, 70, 160};
        for (int i = 0; i < Math.min(widths.length, tblSucursales.getColumnCount()); i++) {
            tblSucursales.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    /** Carga los datos desde la BD y llena el modelo. */
    public final void cargarSucursales() {
        final String sql = """
                SELECT id, nombre, direccion, telefono, activo, creado_en
                FROM sucursales
                ORDER BY nombre
                """;
        try (Connection con = DB.get()) {

            // Variables de sesión para triggers / auditoría (si las usas)
            if (usuarioId > 0) {
                try (PreparedStatement psVar = con.prepareStatement("SET @app_user_id = ?")) {
                    psVar.setInt(1, usuarioId);
                    psVar.executeUpdate();
                }
            }
            if (sucursalId > 0) {
                try (PreparedStatement psVar = con.prepareStatement("SET @app_sucursal_id = ?")) {
                    psVar.setInt(1, sucursalId);
                    psVar.executeUpdate();
                }
            }

            try (PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                modelo.setRowCount(0);
                while (rs.next()) {
                    int id          = rs.getInt("id");
                    String nombre   = rs.getString("nombre");
                    String dir      = rs.getString("direccion");
                    String tel      = rs.getString("telefono");
                    int activoInt   = rs.getInt("activo"); // 0/1
                    String activoTx = (activoInt == 1) ? "Sí" : "No";
                    String creado   = String.valueOf(rs.getTimestamp("creado_en"));

                    modelo.addRow(new Object[]{id, nombre, dir, tel, activoTx, creado});
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(panelMain, "Error al cargar sucursales: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Integer getSelectedSucursalId() {
        int viewRow = tblSucursales.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = tblSucursales.convertRowIndexToModel(viewRow);
        Object v = modelo.getValueAt(modelRow, 0);
        return (v instanceof Integer) ? (Integer) v : Integer.valueOf(v.toString());
    }

    private void eliminarSucursalSeleccionada() {
        Integer id = getSelectedSucursalId();
        if (id == null) {
            JOptionPane.showMessageDialog(panelMain, "Selecciona una sucursal para eliminar.");
            return;
        }
        int ok = JOptionPane.showConfirmDialog(panelMain,
                "¿Seguro que deseas eliminar la sucursal ID " + id + "?\n" +
                        "Esta acción no se puede deshacer.",
                "Confirmar eliminación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;

        final String sql = "DELETE FROM sucursales WHERE id = ?";
        try (Connection con = DB.get()) {
            if (usuarioId > 0) {
                try (PreparedStatement psVar = con.prepareStatement("SET @app_user_id = ?")) {
                    psVar.setInt(1, usuarioId);
                    psVar.executeUpdate();
                }
            }
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, id);
                int n = ps.executeUpdate();
                if (n > 0) {
                    JOptionPane.showMessageDialog(panelMain, "Sucursal eliminada.");
                    cargarSucursales();
                } else {
                    JOptionPane.showMessageDialog(panelMain, "No se encontró la sucursal.");
                }
            }
        } catch (SQLException e) {
            // FK o integridad referencial, etc.
            JOptionPane.showMessageDialog(panelMain,
                    "No se pudo eliminar la sucursal (puede estar referenciada).\n" + e.getMessage());
            e.printStackTrace();
        }
    }
}
