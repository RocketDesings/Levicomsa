import javax.swing.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ModificarCliente {
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

    private String rfcOriginal; // Para identificar al cliente en la BD

    public ModificarCliente(Refrescable refrescable, String nombre, String telefono, String curp, String rfc, String correo, String pensionado) {
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
            actualizarCliente(
                    txtNombre.getText(),
                    txtTelefono.getText(),
                    txtCurp.getText(),
                    txtRFC.getText(),
                    txtCorreo.getText(),
                    cmbPensionado.getSelectedItem().toString(),
                    rfcOriginal
            );
            JOptionPane.showMessageDialog(frame, "Cliente modificado correctamente.");
            if (this.refrescable != null) {
                this.refrescable.refrescarDatos();  // Actualiza la tabla
            }
            frame.dispose();
        });

        btnCancelar.addActionListener(e -> frame.dispose());
    }

    private void actualizarCliente(String nombre, String telefono, String curp, String rfcNuevo, String correo, String pensionado, String rfcOriginal) {
        String sql = "UPDATE Clientes SET nombre=?, telefono=?, curp=?, RFC=?, correo=?, pensionado=? WHERE RFC=?";

        try (Connection conn = JDBC.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, txtNombre.getText());
            ps.setString(2, txtTelefono.getText());
            ps.setString(3, txtCurp.getText());
            ps.setString(4, txtRFC.getText());
            ps.setString(5, txtCorreo.getText());

            // 👇 conversión de "Sí"/"No" → boolean
            String pensionadoTexto = (String) cmbPensionado.getSelectedItem();
            ps.setBoolean(6, pensionadoTexto.equals("Sí"));

            ps.setString(7, rfcOriginal);

            ps.executeUpdate();

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Error al modificar cliente: " + e.getMessage());
        }

    }
}
