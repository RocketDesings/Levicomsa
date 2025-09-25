import javax.swing.*;
import java.awt.event.KeyEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ModificarCliente {
    private final Refrescable refrescable;
    private final int usuarioId;
    private final String rfcOriginal; // Identifica al cliente a actualizar

    // UI (del .form)
    private JPanel panel1;
    private JTextField txtNombre;
    private JTextField txtTelefono;
    private JTextField txtCurp;
    private JTextField txtRFC;
    private JTextField txtCorreo;
    private JLabel lblIcono;
    private JButton btnConfirmar;
    private JButton btnCancelar;
    private JComboBox<String> cmbPensionado;
    private JTextField txtNSS;

    private JFrame frame;

    // ===== Constructor recomendado (incluye NSS) =====
    public ModificarCliente(Refrescable refrescable,
                            String nombre,
                            String telefono,
                            String curp,
                            String rfc,
                            String correo,
                            String pensionado,
                            String nss,
                            int usuarioId) {
        this.refrescable = refrescable;
        this.usuarioId   = usuarioId;
        this.rfcOriginal = rfc;

        construirVentana();

        if (cmbPensionado.getItemCount() == 0) {
            cmbPensionado.addItem("Sí");
            cmbPensionado.addItem("No");
        }

        // Carga de datos
        txtNombre.setText(nullSafe(nombre));
        txtTelefono.setText(nullSafe(telefono));
        txtCurp.setText(nullSafe(curp));
        txtRFC.setText(nullSafe(rfc));
        txtCorreo.setText(nullSafe(correo));
        txtNSS.setText(nullSafe(nss)); // ← NSS
        cmbPensionado.setSelectedItem(pensionado != null ? pensionado : "No");

        instalarAtajosYAcciones();

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // ===== Constructor legacy (sin NSS): lo lee de la BD por RFC) =====
    public ModificarCliente(Refrescable refrescable,
                            String nombre,
                            String telefono,
                            String curp,
                            String rfc,
                            String correo,
                            String pensionado,
                            int usuarioId) {
        this(refrescable,
                nombre, telefono, curp, rfc, correo, pensionado,
                leerNSSporRFC(rfc),        // ← obtiene NSS desde BD
                usuarioId);
    }

    private void construirVentana() {
        frame = new JFrame("Modificar cliente");
        frame.setUndecorated(true);
        frame.setContentPane(panel1);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    private void instalarAtajosYAcciones() {
        btnConfirmar.addActionListener(e -> confirmar());
        btnCancelar.addActionListener(e -> frame.dispose());

        // ENTER confirma, ESC cierra
        panel1.registerKeyboardAction(e -> confirmar(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        panel1.registerKeyboardAction(e -> frame.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private static String leerNSSporRFC(String rfc) {
        if (rfc == null || rfc.isBlank()) return "";
        final String sql = "SELECT NSS FROM Clientes WHERE RFC = ? LIMIT 1";
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, rfc.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return nullSafe(rs.getString(1));
            }
        } catch (SQLException ignored) {}
        return "";
    }

    private void confirmar() {
        if (!validar()) return;

        final String sql = """
            UPDATE Clientes
               SET nombre=?,
                   telefono=?,
                   CURP=?,
                   RFC=?,
                   correo=?,
                   pensionado=?,
                   NSS=?
             WHERE RFC=?
            """;

        try (Connection con = DB.get()) {
            con.setAutoCommit(false);

            // Para bitácora de clientes si tus triggers usan @app_user_id
            if (usuarioId > 0) {
                try (PreparedStatement ps = con.prepareStatement("SET @app_user_id = ?")) {
                    ps.setInt(1, usuarioId);
                    ps.executeUpdate();
                }
            }

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, txtNombre.getText().trim());
                ps.setString(2, txtTelefono.getText().trim());
                ps.setString(3, txtCurp.getText().trim());
                ps.setString(4, txtRFC.getText().trim());
                ps.setString(5, txtCorreo.getText().trim());
                ps.setBoolean(6, isSi(cmbPensionado.getSelectedItem()));
                ps.setString(7, txtNSS.getText().trim());    // ← NSS
                ps.setString(8, rfcOriginal);

                int upd = ps.executeUpdate();
                con.commit();

                if (upd > 0) {
                    JOptionPane.showMessageDialog(frame, "Cliente modificado correctamente.");
                    if (refrescable != null) refrescable.refrescarDatos();
                    frame.dispose();
                } else {
                    JOptionPane.showMessageDialog(frame, "No se encontró el cliente a actualizar (RFC).");
                }
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Error al modificar cliente:\n" + ex.getMessage());
        }
    }

    private boolean validar() {
        if (vacio(txtNombre))  { warn("El nombre es obligatorio.", txtNombre);   return false; }
        if (vacio(txtTelefono)){ warn("El teléfono es obligatorio.", txtTelefono); return false; }
        if (vacio(txtCurp))    { warn("La CURP es obligatoria.", txtCurp);        return false; }
        if (vacio(txtRFC))     { warn("El RFC es obligatorio.", txtRFC);          return false; }
        if (vacio(txtCorreo))  { warn("El correo es obligatorio.", txtCorreo);    return false; }
        // NSS opcional: si lo rellenas, valida formato simple (11 dígitos)
        String nss = txtNSS.getText().trim();
        if (!nss.isEmpty() && !nss.matches("\\d{11}")) {
            warn("El NSS debe tener 11 dígitos (o déjalo vacío).", txtNSS);
            return false;
        }
        return true;
    }

    private boolean vacio(JTextField tf) { return tf.getText() == null || tf.getText().trim().isEmpty(); }

    private void warn(String msg, JComponent focus) {
        JOptionPane.showMessageDialog(frame, msg);
        if (focus != null) focus.requestFocusInWindow();
    }

    private boolean isSi(Object val) {
        if (val == null) return false;
        String s = val.toString().trim().toLowerCase();
        return s.equals("sí") || s.equals("si") || s.equals("yes") || s.equals("true");
    }
}