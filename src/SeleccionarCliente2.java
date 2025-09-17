import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class SeleccionarCliente2 implements Refrescable {

    private final int usuarioId;
    private final Refrescable refrescable;

    // UI (del .form)
    private JPanel panel1;
    private JTable tblClientes;
    private JTextField txtRFC;
    private JTextField txtCURP;
    private JButton btnCancelar;

    private JFrame frame;
    private TableRowSorter<DefaultTableModel> sorter;

    public SeleccionarCliente2(Refrescable parent, int usuarioId) {
        this.refrescable = parent;
        this.usuarioId = usuarioId;

        // Frame
        frame = new JFrame("Seleccionar Cliente");
        frame.setUndecorated(true);
        frame.setContentPane(panel1);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);

        // Tabla y datos
        configurarTabla();
        cargarClientesDesdeBD();

        // Listeners
        btnCancelar.addActionListener(e -> frame.dispose());
        instalarFiltrosEnVivo();
        instalarDobleClickAbrirModificar();
        registrarESCparaCerrar();

        frame.setVisible(true);
    }

    // ---------------- UI/Tabla ----------------

    private void configurarTabla() {
        String[] columnas = {"Nombre", "Teléfono", "CURP", "Pensionado", "RFC", "Correo"};
        DefaultTableModel modelo = new DefaultTableModel(columnas, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        tblClientes.setModel(modelo);

        sorter = new TableRowSorter<>(modelo);
        tblClientes.setRowSorter(sorter);
        tblClientes.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    }

    private void instalarFiltrosEnVivo() {
        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { filtrarTabla(); }
            public void removeUpdate(DocumentEvent e)  { filtrarTabla(); }
            public void changedUpdate(DocumentEvent e) { filtrarTabla(); }
        };
        txtRFC.getDocument().addDocumentListener(dl);
        txtCURP.getDocument().addDocumentListener(dl);
    }

    private void instalarDobleClickAbrirModificar() {
        tblClientes.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    int row = tblClientes.getSelectedRow();
                    if (row < 0) return;
                    row = tblClientes.convertRowIndexToModel(row);

                    DefaultTableModel model = (DefaultTableModel) tblClientes.getModel();
                    String nombre     = asString(model.getValueAt(row, 0));
                    String telefono   = asString(model.getValueAt(row, 1));
                    String curp       = asString(model.getValueAt(row, 2));
                    String pensionado = asString(model.getValueAt(row, 3));
                    String rfc        = asString(model.getValueAt(row, 4));
                    String correo     = asString(model.getValueAt(row, 5));

                    // Abre el formulario de modificación (tu clase ya acepta usuarioId)
                    new ModificarCliente(refrescable, nombre, telefono, curp, rfc, correo, pensionado, usuarioId);
                }
            }
        });
    }

    private void registrarESCparaCerrar() {
        JRootPane root = frame.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "close");
        root.getActionMap().put("close", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { frame.dispose(); }
        });
    }

    private static String asString(Object o) { return o == null ? "" : o.toString(); }

    // ---------------- Datos ----------------

    public void cargarClientesDesdeBD() {
        final String sql = "SELECT nombre, telefono, CURP, pensionado, RFC, correo FROM Clientes ORDER BY nombre";

        try (Connection conn = DB.get();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            DefaultTableModel modelo = (DefaultTableModel) tblClientes.getModel();
            modelo.setRowCount(0);

            while (rs.next()) {
                String nombre     = rs.getString(1);
                String telefono   = rs.getString(2);
                String curp       = rs.getString(3);
                String pensionado = rs.getBoolean(4) ? "Sí" : "No";
                String rfc        = rs.getString(5);
                String correo     = rs.getString(6);
                modelo.addRow(new Object[]{nombre, telefono, curp, pensionado, rfc, correo});
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(panel1, "Error al cargar clientes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void filtrarTabla() {
        if (sorter == null) return;

        String filtroRFC  = txtRFC.getText().trim();
        String filtroCURP = txtCURP.getText().trim();

        List<RowFilter<Object, Object>> filtros = new ArrayList<>();

        if (!filtroRFC.isEmpty()) {
            filtros.add(RowFilter.regexFilter("(?i)" + Pattern.quote(filtroRFC), 4)); // RFC (col 4)
        }
        if (!filtroCURP.isEmpty()) {
            filtros.add(RowFilter.regexFilter("(?i)" + Pattern.quote(filtroCURP), 2)); // CURP (col 2)
        }

        if (filtros.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.andFilter(filtros));
        }
    }

    @Override
    public void refrescarDatos() {
        cargarClientesDesdeBD();
    }
}
