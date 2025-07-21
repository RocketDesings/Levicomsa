import javax.swing.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class FormularioAgregarCliente extends JFrame {
    public JPanel mainPanel;
    private JTextField txtNombre;
    private JTextField txtTelefono;
    private JTextField txtCurp;
    private JCheckBox chechPensionado;
    private JTextField txtRFC;
    private JTextField txtCorreo;
    public JButton btnAgregar;
    public JButton btnCancelar;
    private PantallaAsesor pantallaPrincipal;

    public FormularioAgregarCliente(PantallaAsesor pantallaPrincipal) {
        this.pantallaPrincipal = pantallaPrincipal;

        setTitle("Agregar Cliente");
        setContentPane(mainPanel);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        btnAgregar.addActionListener(e -> {
            agregarCliente();
        });

        btnCancelar.addActionListener(e -> {
            dispose();
            pantallaPrincipal.mostrar();
        });
    }

    private void agregarCliente() {
        String nombre = txtNombre.getText().trim();
        String telefono = txtTelefono.getText().trim();
        String curp = txtCurp.getText().trim();
        int pensionado = chechPensionado.isSelected() ? 1 : 0;
        String rfc = txtRFC.getText().trim();
        String correo = txtCorreo.getText().trim();

        if (nombre.isEmpty() || telefono.isEmpty() || curp.isEmpty() || rfc.isEmpty() || correo.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Por favor, complete todos los campos.");
            return;
        }
        if (curp.length() != 18) {
            JOptionPane.showMessageDialog(this, "La CURP debe tener exactamente 18 caracteres.");
            txtCurp.requestFocus();
            return;
        }


        String sql = "INSERT INTO Clientes (nombre, telefono, CURP, pensionado, RFC, correo) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = JDBC.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, nombre);
            ps.setString(2, telefono);
            ps.setString(3, curp);
            ps.setInt(4, pensionado);
            ps.setString(5, rfc);
            ps.setString(6, correo);

            int resultado = ps.executeUpdate();

            if (resultado > 0) {
                JOptionPane.showMessageDialog(this, "Cliente agregado correctamente.");
                dispose();
                pantallaPrincipal.mostrar();
            } else {
                JOptionPane.showMessageDialog(this, "Error al agregar cliente.");
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error en la base de datos: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
