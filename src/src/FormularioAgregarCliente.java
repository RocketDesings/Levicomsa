import javax.swing.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class FormularioAgregarCliente extends JFrame {
    private int usuarioId;
    public JPanel mainPanel;
    private JTextField txtNombre;
    private JTextField txtTelefono;
    private JTextField txtCurp;
    private JCheckBox chechPensionado;
    private JTextField txtRFC;
    private JTextField txtCorreo;
    public JButton btnAgregar;
    public JButton btnCancelar;

    private Refrescable pantallaPrincipal;

    public FormularioAgregarCliente(Refrescable pantallaPrincipal, int usuarioId) {
        this.pantallaPrincipal = pantallaPrincipal;
        this.usuarioId = usuarioId;
        setTitle("Agregar Cliente");
        setContentPane(mainPanel);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        btnAgregar.addActionListener(e -> agregarCliente());

        btnCancelar.addActionListener(e -> {
            dispose();
            pantallaPrincipal.refrescarDatos();
        });
    }

    private void agregarCliente() {
        // Validaciones
        if (!ValidarJTextField.validarNoVacio(txtNombre)) {
            mostrarError("El nombre no puede estar vacío.", txtNombre);
            return;
        }
        if (!ValidarJTextField.validarSoloLetras(txtNombre)) {
            mostrarError("El nombre solo debe contener letras.", txtNombre);
            return;
        }
        if (!ValidarJTextField.validarLongitudMaxima(txtNombre, 100)) {
            mostrarError("El nombre no debe superar los 100 caracteres.", txtNombre);
            return;
        }

        if (!ValidarJTextField.validarNoVacio(txtTelefono)) {
            mostrarError("El teléfono no puede estar vacío.", txtTelefono);
            return;
        }
        if (!ValidarJTextField.validarSoloNumeros(txtTelefono)) {
            mostrarError("El teléfono solo debe contener números.", txtTelefono);
            return;
        }
        if (!ValidarJTextField.validarLongitudMaxima(txtTelefono, 15)) {
            mostrarError("El teléfono no debe exceder los 15 dígitos.", txtTelefono);
            return;
        }

        if (!ValidarJTextField.validarNoVacio(txtCurp)) {
            mostrarError("La CURP no puede estar vacía.", txtCurp);
            return;
        }
        if (!ValidarJTextField.validarLongitudExacta(txtCurp, 18)) {
            mostrarError("La CURP debe tener exactamente 18 caracteres.", txtCurp);
            return;
        }
        if (!ValidarJTextField.validarCURP(txtCurp)) {
            mostrarError("La CURP no tiene un formato válido.", txtCurp);
            return;
        }

        if (!ValidarJTextField.validarNoVacio(txtRFC)) {
            mostrarError("El RFC no puede estar vacío.", txtRFC);
            return;
        }
        if (!ValidarJTextField.validarLongitudExacta(txtRFC, 13)) {
            mostrarError("El RFC debe tener exactamente 13 caracteres.", txtRFC);
            return;
        }
        if (!ValidarJTextField.validarRFC(txtRFC)) {
            mostrarError("El RFC no tiene un formato válido.", txtRFC);
            return;
        }

        if (!ValidarJTextField.validarNoVacio(txtCorreo)) {
            mostrarError("El correo no puede estar vacío.", txtCorreo);
            return;
        }
        if (!ValidarJTextField.validarEmail(txtCorreo)) {
            mostrarError("El correo no tiene un formato válido.", txtCorreo);
            return;
        }
        if (!ValidarJTextField.validarLongitudMaxima(txtCorreo, 100)) {
            mostrarError("El correo no debe superar los 100 caracteres.", txtCorreo);
            return;
        }

        // Si todas las validaciones pasan, proceder a insertar en la base de datos
        String nombre = txtNombre.getText().trim();
        String telefono = txtTelefono.getText().trim();
        String curp = txtCurp.getText().trim().toUpperCase();
        int pensionado = chechPensionado.isSelected() ? 1 : 0;
        String rfc = txtRFC.getText().trim().toUpperCase();
        String correo = txtCorreo.getText().trim();

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
                pantallaPrincipal.refrescarDatos();
            } else {
                JOptionPane.showMessageDialog(this, "Error al agregar cliente.");
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error en la base de datos: " + ex.getMessage());
            ex.printStackTrace();
        }
    }//fin del metodo agregarCliente

    private void mostrarError(String mensaje, JTextField campo) {
        JOptionPane.showMessageDialog(this, mensaje, "Validación", JOptionPane.WARNING_MESSAGE);
        campo.requestFocus();
    }
}
