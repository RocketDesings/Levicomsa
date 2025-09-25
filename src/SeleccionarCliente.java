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
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class SeleccionarCliente implements Refrescable {
    private final Refrescable refrescable;                 // opcional, por si quieres refrescar afuera
    private final BiConsumer<Integer, String> onPick;      // devuelve (clienteId, nombre)

    // UI (del .form)
    private JPanel panel1;
    private JTable tblClientes;
    private JButton btnCancelar;

    // Búsqueda global (si existe en tu .form, se usará)
    private JTextField txtBuscar;     // <— crea este campo en el .form
    private JButton buscarButton;     // (opcional)

    private JFrame frame;
    private DefaultTableModel modelo;
    private TableRowSorter<DefaultTableModel> sorter;

    // Índices en el MODELO
    private static final int COL_ID         = 0;
    private static final int COL_NOMBRE     = 1;
    private static final int COL_TELEFONO   = 2;
    private static final int COL_CURP       = 3;
    private static final int COL_PENSIONADO = 4;
    private static final int COL_RFC        = 5;
    private static final int COL_NSS        = 6;
    private static final int COL_CORREO     = 7;

    public SeleccionarCliente(BiConsumer<Integer,String> onPick) { this(null, onPick); }

    public SeleccionarCliente(Refrescable parent, BiConsumer<Integer,String> onPick) {
        this.refrescable = parent;
        this.onPick = onPick;

        frame = new JFrame("Seleccionar Cliente");
        frame.setUndecorated(true);
        frame.setContentPane(panel1);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        configurarTabla();
        cargarClientesDesdeBD();
        cablearEventos();
        cablearBusqueda(); // usa txtBuscar si existe

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void configurarTabla() {
        // Col 0 = ID (oculta en la vista)
        String[] cols = {"ID","Nombre","Teléfono","CURP","Pensionado","RFC","NSS","Correo"};
        modelo = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tblClientes.setModel(modelo);

        sorter = new TableRowSorter<>(modelo);
        tblClientes.setRowSorter(sorter);

        if (tblClientes.getColumnModel().getColumnCount() > 0) {
            tblClientes.removeColumn(tblClientes.getColumnModel().getColumn(0)); // oculta ID
        }

        tblClientes.setRowHeight(26);
        tblClientes.getTableHeader().setReorderingAllowed(false);
        tblClientes.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    }

    private void cablearEventos() {
        // Doble clic
        tblClientes.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) seleccionarActualYSalir();
            }
        });

        // ENTER selecciona
        tblClientes.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("ENTER"), "pick");
        tblClientes.getActionMap().put("pick", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { seleccionarActualYSalir(); }
        });

        // Cancelar y ESC cierran
        btnCancelar.addActionListener(e -> frame.dispose());
        panel1.registerKeyboardAction(
                e -> frame.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        );
    }

    /** Búsqueda global por todas las columnas visibles (si txtBuscar existe). */
    private void cablearBusqueda() {
        if (txtBuscar == null) return; // si no lo tienes en el .form, no pasa nada

        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e){ aplicarFiltroGlobal(); }
            public void removeUpdate(DocumentEvent e){ aplicarFiltroGlobal(); }
            public void changedUpdate(DocumentEvent e){ aplicarFiltroGlobal(); }
        };
        txtBuscar.getDocument().addDocumentListener(dl);
        txtBuscar.addActionListener(e -> aplicarFiltroGlobal());

        if (buscarButton != null) buscarButton.addActionListener(e -> aplicarFiltroGlobal());

        // ESC limpia
        txtBuscar.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ESCAPE"), "clear");
        txtBuscar.getActionMap().put("clear", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { txtBuscar.setText(""); aplicarFiltroGlobal(); }
        });
    }

    private void aplicarFiltroGlobal() {
        if (sorter == null) return;
        String q = txtBuscar.getText().trim();
        if (q.isEmpty()) { sorter.setRowFilter(null); return; }

        String regex = "(?i)" + java.util.regex.Pattern.quote(q);
        List<RowFilter<Object,Object>> cols = new ArrayList<>();
        cols.add(RowFilter.regexFilter(regex, COL_NOMBRE));
        cols.add(RowFilter.regexFilter(regex, COL_TELEFONO));
        cols.add(RowFilter.regexFilter(regex, COL_CURP));
        cols.add(RowFilter.regexFilter(regex, COL_PENSIONADO));
        cols.add(RowFilter.regexFilter(regex, COL_RFC));
        cols.add(RowFilter.regexFilter(regex, COL_NSS));
        cols.add(RowFilter.regexFilter(regex, COL_CORREO));

        sorter.setRowFilter(RowFilter.orFilter(cols));
    }

    private void seleccionarActualYSalir() {
        int viewRow = tblClientes.getSelectedRow();
        if (viewRow == -1) return;
        int modelRow = tblClientes.convertRowIndexToModel(viewRow);

        int clienteId = Integer.parseInt(modelo.getValueAt(modelRow, COL_ID).toString());
        String nombre = modelo.getValueAt(modelRow, COL_NOMBRE).toString();

        if (onPick != null) onPick.accept(clienteId, nombre);
        frame.dispose();
    }

    public void cargarClientesDesdeBD() {
        final String sql = """
            SELECT
              id         AS Id,
              nombre     AS Nombre,
              telefono   AS Telefono,
              CURP       AS CURP,
              pensionado AS Pensionado,
              RFC        AS RFC,
              NSS        AS NSS,
              correo     AS Correo
            FROM Clientes
            ORDER BY nombre
            """;
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            modelo.setRowCount(0);
            while (rs.next()) {
                Object[] fila = {
                        rs.getInt("Id"),
                        rs.getString("Nombre"),
                        rs.getString("Telefono"),
                        rs.getString("CURP"),
                        rs.getBoolean("Pensionado") ? "Sí" : "No",
                        rs.getString("RFC"),
                        rs.getString("NSS"),
                        rs.getString("Correo")
                };
                modelo.addRow(fila);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Error al cargar clientes:\n" + ex.getMessage());
        }
    }

    @Override
    public void refrescarDatos() {
        cargarClientesDesdeBD();
    }
}
