import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.FileWriter;
import java.sql.*;
import java.text.SimpleDateFormat;

public class ConsultarCortesCaja {
    // --- UI (del .form) ---
    private JPanel panelMain;
    private JPanel panelContenedor;
    private JPanel panelTabla;
    private JLabel lblTitulo;
    private JTable tblCortes;
    private JScrollPane scrTabla;
    private JButton exportarACSVButton;
    private JButton cerrarButton;
    private JLabel lblEtiqueta;

    // --- tabla ---
    private DefaultTableModel modelo;
    private TableRowSorter<DefaultTableModel> sorter;

    // --- ctor ---
    public ConsultarCortesCaja() {
        construirTabla();
        cargarCortes();

        if (exportarACSVButton != null) {
            exportarACSVButton.addActionListener(e -> exportarCorteSeleccionadoCSV());
        }
        if (cerrarButton != null) {
            cerrarButton.addActionListener(e -> {
                Window w = getOwnerWindow();
                if (w instanceof JDialog d) d.dispose();
                else if (w != null) w.dispose();
            });
        }

        // Estilito rápido
        if (panelMain != null) panelMain.setBorder(new EmptyBorder(10,10,10,10));
        if (lblTitulo != null) lblTitulo.setText("Cortes de caja");
        if (lblEtiqueta != null) lblEtiqueta.setVisible(false);
    }

    /** Crea y devuelve el diálogo listo para mostrarse. */
    public static JDialog createDialog(Window owner) {
        ConsultarCortesCaja ui = new ConsultarCortesCaja();
        JDialog d = new JDialog(owner, "Consultar cortes de caja", Dialog.ModalityType.MODELESS);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(ui.root());
        d.setMinimumSize(new Dimension(900, 520));
        d.pack();
        if (owner != null && owner.isShowing()) d.setLocationRelativeTo(owner);
        else d.setLocationRelativeTo(null);
        return d;
    }

    // ===================== Tabla =====================
    private void construirTabla() {
        if (tblCortes == null) return;
        String[] cols = {"Fecha", "ID", "Sucursal", "Usuario ID"};
        modelo = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return (c==1 || c==3) ? Integer.class : String.class;
            }
        };
        tblCortes.setModel(modelo);
        sorter = new TableRowSorter<>(modelo);
        tblCortes.setRowSorter(sorter);

        tblCortes.setRowHeight(26);
        tblCortes.setShowGrid(false);
        tblCortes.setIntercellSpacing(new Dimension(0,0));
        JTableHeader h = tblCortes.getTableHeader();
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, 32));

        int[] widths = {220, 90, 280, 110};
        for (int i = 0; i < Math.min(widths.length, tblCortes.getColumnCount()); i++) {
            tblCortes.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private void cargarCortes() {
        if (tblCortes == null) return;
        modelo.setRowCount(0);

        // NOTA: la tabla no guarda sucursal_id; derivamos sucursal del usuario (trabajador.sucursal_id).
        final String sql = """
            SELECT
              c.id,
              c.usuario_id,
              c.generado_en,
              COALESCE(s.nombre, '') AS sucursal
            FROM corte_caja_resumen c
            LEFT JOIN Usuarios u    ON u.id = c.usuario_id
            LEFT JOIN trabajadores t ON t.id = u.trabajador_id
            LEFT JOIN sucursales s    ON s.id = t.sucursal_id
            ORDER BY c.generado_en DESC, c.id DESC
        """;

        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            while (rs.next()) {
                int id         = rs.getInt("id");
                int usuarioId  = rs.getInt("usuario_id");
                Timestamp ts   = rs.getTimestamp("generado_en");
                String fecha   = (ts != null ? sdf.format(ts) : "");
                String sucursal= rs.getString("sucursal");
                modelo.addRow(new Object[]{fecha, id, sucursal, usuarioId});
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(root(), "Error al cargar cortes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Integer getSelectedCorteId() {
        int view = tblCortes.getSelectedRow();
        if (view < 0) return null;
        int modelRow = tblCortes.convertRowIndexToModel(view);
        Object v = modelo.getValueAt(modelRow, 1); // columna "ID"
        if (v == null) return null;
        return (v instanceof Integer) ? (Integer) v : Integer.parseInt(v.toString());
    }

    // ===================== Exportar =====================
    private void exportarCorteSeleccionadoCSV() {
        Integer id = getSelectedCorteId();
        if (id == null) {
            JOptionPane.showMessageDialog(root(), "Selecciona un corte para exportar.");
            return;
        }

        final String q = "SELECT * FROM corte_caja_resumen WHERE id = ?";
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(q)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    JOptionPane.showMessageDialog(root(), "No se encontró el corte ID " + id);
                    return;
                }
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();

                JFileChooser fc = new JFileChooser();
                fc.setDialogTitle("Exportar corte #" + id);
                fc.setSelectedFile(new java.io.File("corte_" + id + ".csv"));
                if (fc.showSaveDialog(root()) != JFileChooser.APPROVE_OPTION) return;

                try (FileWriter wr = new FileWriter(fc.getSelectedFile())) {
                    // encabezados
                    for (int c = 1; c <= cols; c++) {
                        wr.write(esc(md.getColumnLabel(c)));
                        if (c < cols) wr.write(",");
                    }
                    wr.write("\n");
                    // fila de datos
                    for (int c = 1; c <= cols; c++) {
                        Object v = rs.getObject(c);
                        wr.write(esc(v == null ? "" : v.toString()));
                        if (c < cols) wr.write(",");
                    }
                    wr.write("\n");
                }

                JOptionPane.showMessageDialog(root(),
                        "Exportado a:\n" + fc.getSelectedFile().getAbsolutePath());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(root(), "No se pudo exportar:\n" + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"","\"\"") + "\"";
        }
        return s;
    }

    // ===================== Helpers UI =====================
    private Container root() {
        if (panelMain != null) return panelMain;
        JPanel r = new JPanel(new BorderLayout());
        r.add(panelTabla != null ? panelTabla : new JScrollPane(new JTable()), BorderLayout.CENTER);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        if (exportarACSVButton != null) south.add(exportarACSVButton);
        if (cerrarButton != null) south.add(cerrarButton);
        r.add(south, BorderLayout.SOUTH);
        return r;
    }

    private Window getOwnerWindow() {
        Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (active != null) return active;
        for (Frame f : Frame.getFrames()) if (f.isVisible()) return f;
        return JOptionPane.getRootFrame();
    }
}
