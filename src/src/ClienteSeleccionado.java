import javax.swing.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

    public class ClienteSeleccionado {
        private Refrescable refrescable;
        private JPanel panel1;
        private JTextField txtNombre;
        private JTextField txtTelefono;
        private JTextField txtCurp;
        private JTextField txtRFC;
        private JTextField txtCorreo;
        private JLabel lblIcono;
        private JButton btnConfirmar;
        private JButton btnCancelar;
        private JComboBox cmbPensionado;

        private String rfcOriginal; // Para guardar el RFC y usarlo en el DELETE

        public ClienteSeleccionado(Refrescable refrescable, String nombre, String telefono, String curp, String rfc, String correo, String pensionado) {
            this.refrescable = refrescable;
            this.rfcOriginal = rfc;

            cmbPensionado.addItem("Sí");
            cmbPensionado.addItem("No");

            txtNombre.setText(nombre);
            txtTelefono.setText(telefono);
            txtCurp.setText(curp);
            txtRFC.setText(rfc);
            txtCorreo.setText(correo);
            cmbPensionado.setSelectedItem(pensionado);

            JFrame frame = new JFrame("Cliente Seleccionado");
            frame.setUndecorated(true);
            frame.setContentPane(panel1);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            btnConfirmar.addActionListener(e -> {
                eliminarCliente(rfcOriginal);
                JOptionPane.showMessageDialog(frame, "Cliente eliminado correctamente.");
                if (this.refrescable != null) {
                    this.refrescable.refrescarDatos();  // Actualiza la tabla
                }
                frame.dispose();
            });

            btnCancelar.addActionListener(e -> frame.dispose());
        }

        private void eliminarCliente(String rfc) {
            String sql = "DELETE FROM Clientes WHERE RFC = ?";

            try (Connection conn = JDBC.obtenerConexion();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, rfc);
                ps.executeUpdate();

            } catch (SQLException e) {
                JOptionPane.showMessageDialog(null, "Error al eliminar cliente: " + e.getMessage());
            }
        }
    }
