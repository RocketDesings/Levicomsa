import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.RowFilter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SeleccionarCliente2 implements Refrescable {

    // Índices de columnas (modelo)
    private static final int COL_NOMBRE     = 0;
    private static final int COL_TELEFONO   = 1;
    private static final int COL_CURP       = 2;
    private static final int COL_PENSIONADO = 3;
    private static final int COL_RFC        = 4;
    private static final int COL_NSS        = 5; // ← NUEVO
    private static final int COL_CORREO     = 6;

    private final int usuarioId;
    private final Refrescable refrescable;

    // UI (del .form)
    private JPanel panel1;
    private JTable tblClientes;
    private JTextField txtBuscar;   // búsqueda global
    private JButton btnCancelar;

    private JFrame frame;
    private DefaultTableModel modelo;
    private TableRowSorter<DefaultTableModel> sorter;

    public SeleccionarCliente2(Refrescable parent, int usuarioId) {
        this.refrescable = parent;
        this.usuarioId = usuarioId;

        // Frame
        frame = new JFrame("Seleccionar Cliente");
        frame.setUndecorated(true);
        frame.setContentPane(panel1);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        configurarTabla();
        cablearBusquedaInline();
        cablearEventos();

        cargarClientesDesdeBD();

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // ---------------- UI / Tabla ----------------
    private void configurarTabla() {
        String[] columnas = {"Nombre", "Teléfono", "CURP", "Pensionado", "RFC", "NSS", "Correo"}; // ← NSS añadido
        modelo = new DefaultTableModel(columnas, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        tblClientes.setModel(modelo);

        sorter = new TableRowSorter<>(modelo);
        tblClientes.setRowSorter(sorter);
        tblClientes.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        tblClientes.getTableHeader().setReorderingAllowed(false);
        tblClientes.setRowHeight(26);
    }

    private void cablearEventos() {
        // Doble clic
        tblClientes.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) abrirModificarSeleccionado();
            }
        });

        // Enter abre selección
        tblClientes.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("ENTER"), "open");
        tblClientes.getActionMap().put("open", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                abrirModificarSeleccionado();
            }
        });

        // Cancelar
        btnCancelar.addActionListener(e -> frame.dispose());

        // ESC cierra
        panel1.registerKeyboardAction(
                e -> frame.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        );
    }

    // ---------------- Búsqueda tipo PantallaAsesor ----------------
    private void cablearBusquedaInline() {
        if (txtBuscar == null) return;

        txtBuscar.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { aplicarFiltro(txtBuscar.getText().trim()); }
            public void removeUpdate(DocumentEvent e)  { aplicarFiltro(txtBuscar.getText().trim()); }
            public void changedUpdate(DocumentEvent e) { aplicarFiltro(txtBuscar.getText().trim()); }
        });

        txtBuscar.addActionListener(e -> aplicarFiltro(txtBuscar.getText().trim()));

        // ESC para limpiar
        txtBuscar.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "clear");
        txtBuscar.getActionMap().put("clear", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                txtBuscar.setText("");
                aplicarFiltro("");
            }
        });
    }

    private void aplicarFiltro(String q) {
        if (sorter == null) return;
        if (q == null || q.isBlank()) {
            sorter.setRowFilter(null);
            return;
        }
        String regex = "(?i)" + java.util.regex.Pattern.quote(q);
        List<RowFilter<Object,Object>> cols = new ArrayList<>();
        // filtra en todas las columnas visibles
        for (int c = 0; c < modelo.getColumnCount(); c++) {
            cols.add(RowFilter.regexFilter(regex, c));
        }
        sorter.setRowFilter(RowFilter.orFilter(cols));
    }

    private void abrirModificarSeleccionado() {
        int viewRow = tblClientes.getSelectedRow();
        if (viewRow < 0) return;

        int row = tblClientes.convertRowIndexToModel(viewRow);

        String nombre     = getStr(row, COL_NOMBRE);
        String telefono   = getStr(row, COL_TELEFONO);
        String curp       = getStr(row, COL_CURP);
        String pensionado = getStr(row, COL_PENSIONADO);
        String rfc        = getStr(row, COL_RFC);
        String correo     = getStr(row, COL_CORREO);
        // Nota: NSS se muestra/filtra, pero no lo pasamos al formulario (tu constructor actual no lo pide)

        new ModificarCliente(refrescable, nombre, telefono, curp, rfc, correo, pensionado, usuarioId);
    }

    private String getStr(int row, int col) {
        Object v = modelo.getValueAt(row, col);
        return v == null ? "" : v.toString();
    }

    // ---------------- Datos ----------------
    public void cargarClientesDesdeBD() {
        final String sql = "SELECT nombre, telefono, CURP, pensionado, RFC, NSS, correo FROM Clientes ORDER BY nombre";

        try (Connection conn = DB.get();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            modelo.setRowCount(0);

            while (rs.next()) {
                String nombre     = rs.getString(1);
                String telefono   = rs.getString(2);
                String curp       = rs.getString(3);
                String pensionado = rs.getBoolean(4) ? "Sí" : "No";
                String rfc        = rs.getString(5);
                String nss        = rs.getString(6);   // ← NUEVO
                String correo     = rs.getString(7);
                modelo.addRow(new Object[]{nombre, telefono, curp, pensionado, rfc, nss, correo});
            }

        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(panel1, "Error al cargar clientes: " + e.getMessage());
        }
    }

    // ---------------- Refrescable ----------------
    @Override
    public void refrescarDatos() {
        cargarClientesDesdeBD();
    }
}
