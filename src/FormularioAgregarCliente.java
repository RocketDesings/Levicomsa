import javax.swing.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

public class FormularioAgregarCliente extends JFrame {
    private final int usuarioId;

    public JPanel mainPanel;
    private JTextField txtNombre;
    private JTextField txtTelefono;
    private JTextField txtCurp;
    private JCheckBox chechPensionado;
    private JTextField txtRFC;
    private JTextField txtCorreo;
    public JButton btnAgregar;
    public JButton btnCancelar;
    private JTextField txtNSS;

    private final Refrescable pantallaPrincipal;

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
            if (pantallaPrincipal != null) pantallaPrincipal.refrescarDatos();
        });

        // ESC para cerrar
        mainPanel.registerKeyboardAction(
                e -> { dispose(); if (pantallaPrincipal != null) pantallaPrincipal.refrescarDatos(); },
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        );
    }

    private void agregarCliente() {
        // ===== Validaciones =====
        if (!ValidarJTextField.validarNoVacio(txtNombre)) {
            mostrarError("El nombre no puede estar vacío.", txtNombre); return;
        }
        if (!ValidarJTextField.validarSoloLetras(txtNombre)) {
            mostrarError("El nombre solo debe contener letras.", txtNombre); return;
        }
        if (!ValidarJTextField.validarLongitudMaxima(txtNombre, 100)) {
            mostrarError("El nombre no debe superar los 100 caracteres.", txtNombre); return;
        }

        if (!ValidarJTextField.validarNoVacio(txtTelefono)) {
            mostrarError("El teléfono no puede estar vacío.", txtTelefono); return;
        }
        if (!ValidarJTextField.validarSoloNumeros(txtTelefono)) {
            mostrarError("El teléfono solo debe contener números.", txtTelefono); return;
        }
        if (!ValidarJTextField.validarLongitudMaxima(txtTelefono, 15)) {
            mostrarError("El teléfono no debe exceder los 15 dígitos.", txtTelefono); return;
        }

        if (!ValidarJTextField.validarNoVacio(txtCurp)) {
            mostrarError("La CURP no puede estar vacía.", txtCurp); return;
        }
        if (!ValidarJTextField.validarLongitudExacta(txtCurp, 18)) {
            mostrarError("La CURP debe tener exactamente 18 caracteres.", txtCurp); return;
        }
        if (!ValidarJTextField.validarCURP(txtCurp)) {
            mostrarError("La CURP no tiene un formato válido.", txtCurp); return;
        }

        if (!ValidarJTextField.validarNoVacio(txtRFC)) {
            mostrarError("El RFC no puede estar vacío.", txtRFC); return;
        }
        if (!ValidarJTextField.validarLongitudExacta(txtRFC, 13)) {
            mostrarError("El RFC debe tener exactamente 13 caracteres.", txtRFC); return;
        }
        if (!ValidarJTextField.validarRFC(txtRFC)) {
            mostrarError("El RFC no tiene un formato válido.", txtRFC); return;
        }

        if (!ValidarJTextField.validarNoVacio(txtCorreo)) {
            mostrarError("El correo no puede estar vacío.", txtCorreo); return;
        }
        if (!ValidarJTextField.validarEmail(txtCorreo)) {
            mostrarError("El correo no tiene un formato válido.", txtCorreo); return;
        }
        if (!ValidarJTextField.validarLongitudMaxima(txtCorreo, 100)) {
            mostrarError("El correo no debe superar los 100 caracteres.", txtCorreo); return;
        }

        // NSS (opcional). Si viene, debe ser dígitos y largo razonable (11 a 15)
        String nss = txtNSS != null ? txtNSS.getText().trim() : "";
        if (!nss.isEmpty()) {
            if (!nss.matches("\\d{11,15}")) {
                mostrarError("El NSS debe contener solo dígitos (11 a 15).", txtNSS);
                return;
            }
        } else {
            nss = null; // guardamos NULL si lo dejan vacío
        }

        // ===== Datos listos =====
        String nombre   = txtNombre.getText().trim();
        String telefono = txtTelefono.getText().trim();
        String curp     = txtCurp.getText().trim().toUpperCase();
        boolean pensionado = chechPensionado != null && chechPensionado.isSelected();
        String rfc      = txtRFC.getText().trim().toUpperCase();
        String correo   = txtCorreo.getText().trim();

        final String sql = "INSERT INTO Clientes (nombre, telefono, CURP, pensionado, RFC, NSS, correo) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DB.get()) {
            conn.setAutoCommit(false);

            // Para que tus triggers de bitácora registren el usuario (si los usas)
            if (usuarioId > 0) {
                try (PreparedStatement ps = conn.prepareStatement("SET @app_user_id = ?")) {
                    ps.setInt(1, usuarioId);
                    ps.executeUpdate();
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, nombre);
                ps.setString(2, telefono);
                ps.setString(3, curp);
                ps.setBoolean(4, pensionado);
                ps.setString(5, rfc);
                if (nss == null) ps.setNull(6, java.sql.Types.VARCHAR);
                else             ps.setString(6, nss);
                ps.setString(7, correo);

                int resultado = ps.executeUpdate();
                conn.commit();

                if (resultado > 0) {
                    JOptionPane.showMessageDialog(this, "Cliente agregado correctamente.");
                    dispose();
                    if (pantallaPrincipal != null) pantallaPrincipal.refrescarDatos();
                } else {
                    JOptionPane.showMessageDialog(this, "No se pudo agregar el cliente.");
                }
            } catch (SQLIntegrityConstraintViolationException dup) {
                conn.rollback();
                String msg = dup.getMessage();
                if (msg != null) {
                    if (msg.contains("uk_clientes_rfc")) {
                        mostrarError("Ya existe un cliente con ese RFC.", txtRFC);
                    } else if (msg.contains("uk_clientes_curp")) {
                        mostrarError("Ya existe un cliente con esa CURP.", txtCurp);
                    } else if (msg.contains("uk_clientes_nss")) {
                        mostrarError("Ya existe un cliente con ese NSS.", txtNSS);
                    } else {
                        JOptionPane.showMessageDialog(this, "Dato duplicado en cliente.\n" + msg);
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "Dato duplicado en cliente.");
                }
            } catch (SQLException ex) {
                conn.rollback();
                JOptionPane.showMessageDialog(this, "Error en la base de datos: " + ex.getMessage());
                ex.printStackTrace();
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error de conexión: " + ex.getMessage());
            ex.printStackTrace();
        }
    } // fin agregarCliente

    private void mostrarError(String mensaje, JTextField campo) {
        JOptionPane.showMessageDialog(this, mensaje, "Validación", JOptionPane.WARNING_MESSAGE);
        if (campo != null) campo.requestFocus();
    }
}
