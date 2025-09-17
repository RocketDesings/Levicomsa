import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.event.MouseAdapter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

    public class SeleccionarCliente implements Refrescable{
        private Refrescable refrescable;
        private JPanel panel1;
        private JTable tblClientes;
        private JTextField txtRFC;
        private JTextField txtCURP;
        private JButton btnCancelar;

        public SeleccionarCliente() {
            JFrame frame = new JFrame("Seleccionar Cliente");
            frame.setUndecorated(true);
            frame.setContentPane(panel1);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            configurarTabla();
            cargarClientesDesdeBD();

            btnCancelar.addActionListener(e -> frame.dispose());

            txtRFC.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) {
                    filtrarTabla();
                }
                public void removeUpdate(DocumentEvent e) {
                    filtrarTabla();
                }
                public void changedUpdate(DocumentEvent e) {
                    filtrarTabla();
                }
            });

            txtCURP.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) {
                    filtrarTabla();
                }
                public void removeUpdate(DocumentEvent e) {
                    filtrarTabla();
                }
                public void changedUpdate(DocumentEvent e) {
                    filtrarTabla();
                }
            });

            tblClientes.addMouseListener(new MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent evt) {
                    if (evt.getClickCount() == 2) { // Doble clic
                        int row = tblClientes.getSelectedRow();
                        if (row != -1) {
                            row = tblClientes.convertRowIndexToModel(row); // Por si hay filtro

                            DefaultTableModel model = (DefaultTableModel) tblClientes.getModel();

                            String nombre = model.getValueAt(row, 0).toString();
                            String telefono = model.getValueAt(row, 1).toString();
                            String curp = model.getValueAt(row, 2).toString();
                            String pensionado = model.getValueAt(row, 3).toString();
                            String rfc = model.getValueAt(row, 4).toString();
                            String correo = model.getValueAt(row, 5).toString();

                            new ClienteSeleccionado(refrescable, nombre, telefono, curp, rfc, correo, pensionado);


                        }
                    }
                }
            });


        }//FIN CONSTRUCTOR

        public void configurarTabla() {
            String[] columnas = {"Nombre", "Teléfono", "CURP", "Pensionado", "RFC", "Correo"};

            DefaultTableModel modelo = new DefaultTableModel(columnas, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

            tblClientes.setModel(modelo);
        } //FIN CONFIGURAR TABLA


        public void cargarClientesDesdeBD() {
            String sql = "SELECT nombre, telefono, CURP, pensionado, RFC, correo FROM Clientes";

            try (Connection conn = JDBC.obtenerConexion();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                DefaultTableModel modelo = (DefaultTableModel) tblClientes.getModel();
                modelo.setRowCount(0); // Limpiar tabla

                while (rs.next()) {
                    String nombre = rs.getString("Nombre");
                    String telefono = rs.getString("Telefono");
                    String curp = rs.getString("CURP");
                    boolean pensionadoBool = rs.getBoolean("Pensionado");
                    String pensionado = pensionadoBool ? "Sí" : "No";
                    String rfc = rs.getString("RFC");
                    String correo = rs.getString("Correo");
                    Object[] fila = {nombre, telefono, curp, pensionado, rfc, correo};
                    modelo.addRow(fila);
                }

            } catch (SQLException e) {
                JOptionPane.showMessageDialog(null, "Error al cargar clientes: " + e.getMessage());
                e.printStackTrace();
            }
        }//FIN CARGAR CLIENTES DESDE BD
        private void filtrarTabla() {
            String filtroRFC = txtRFC.getText().trim().toLowerCase();
            String filtroCURP = txtCURP.getText().trim().toLowerCase();

            DefaultTableModel modelo = (DefaultTableModel) tblClientes.getModel();
            TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(modelo);
            tblClientes.setRowSorter(sorter);

            List<RowFilter<Object, Object>> filtros = new ArrayList<>();

            if (!filtroRFC.isEmpty()) {
                filtros.add(RowFilter.regexFilter("(?i)" + filtroRFC, 4)); // Columna 4 = RFC
            }

            if (!filtroCURP.isEmpty()) {
                filtros.add(RowFilter.regexFilter("(?i)" + filtroCURP, 2)); // Columna 2 = CURP
            }

            RowFilter<Object, Object> filtroFinal = RowFilter.andFilter(filtros);
            sorter.setRowFilter(filtroFinal);
        }

        @Override
        public void refrescarDatos() {
            cargarClientesDesdeBD();
        }
    }//FIN CLASE SELECCIONAR CLIENTE
