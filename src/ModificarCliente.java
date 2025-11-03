import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.sql.*;

public class ModificarCliente {
    private final Refrescable refrescable;
    private final int usuarioId;
    private final String rfcOriginal;

    // UI (.form)
    private JPanel panelContenedor;
    private JTextField txtNombre;
    private JTextField txtTelefono;
    private JTextField txtCurp;
    private JTextField txtRFC;
    private JTextField txtCorreo;
    private JButton btnConfirmar;
    private JButton btnCancelar;
    private JComboBox<String> cmbPensionado;
    private JTextField txtNSS;
    private JPanel panelMain;
    private JPanel panelDatos;
    private JPanel panelLabels;
    private JPanel panelBotones;

    private JFrame frame;

    // ======= PALETA & FUENTES (según indicaciones) =======
    private static final Color BG_TOP       = new Color(0x052E16);
    private static final Color BG_BOT       = new Color(0x064E3B);
    private static final Color TEXT_MUTED   = new Color(0x67676E);
    private static final Color TABLE_ALT    = new Color(0xF9FAFB);
    private static final Color TABLE_SEL_BG = new Color(0xE6F7EE);
    private static final Color BORDER_SOFT  = new Color(0x535353);
    private static final Color CARD_BG      = new Color(255, 255, 255);
    private static final Color GREEN_DARK   = new Color(0x0A6B2A);
    private static final Color GREEN_BASE   = new Color(0x16A34A);
    private static final Color GREEN_SOFT   = new Color(0x22C55E);
    private static final Color TEXT_PRIMARY = new Color(0x111827);
    private static final Color BORDER_FOCUS = new Color(0x059669);
    private final Font fText   = new Font("Segoe UI", Font.PLAIN, 16);
    private final Font fTitle  = new Font("Segoe UI", Font.BOLD, 22);

    // ===== Constructores =====
    public ModificarCliente(Refrescable refrescable,
                            String nombre, String telefono, String curp, String rfc,
                            String correo, String pensionado, String nss, int usuarioId) {
        this.refrescable = refrescable;
        this.usuarioId   = usuarioId;
        this.rfcOriginal = rfc;

        construirVentana();
        aplicarEstilo(); // <-- SOLO ESTILO

        if (cmbPensionado.getItemCount() == 0) { cmbPensionado.addItem("Sí"); cmbPensionado.addItem("No"); }

        // Datos
        txtNombre.setText(nullSafe(nombre));
        txtTelefono.setText(nullSafe(telefono));
        txtCurp.setText(nullSafe(curp));
        txtRFC.setText(nullSafe(rfc));
        txtCorreo.setText(nullSafe(correo));
        txtNSS.setText(nullSafe(nss));
        cmbPensionado.setSelectedItem(pensionado != null ? pensionado : "No");

        instalarAtajosYAcciones();

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public ModificarCliente(Refrescable refrescable,
                            String nombre, String telefono, String curp, String rfc,
                            String correo, String pensionado, int usuarioId) {
        this(refrescable, nombre, telefono, curp, rfc, correo, pensionado, leerNSSporRFC(rfc), usuarioId);
    }

    private void construirVentana() {
        frame = new JFrame("Modificar cliente");
        frame.setUndecorated(true);
        frame.setContentPane(panelContenedor);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    private void instalarAtajosYAcciones() {
        btnConfirmar.addActionListener(e -> confirmar());
        btnCancelar.addActionListener(e -> frame.dispose());

        panelContenedor.registerKeyboardAction(e -> confirmar(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        panelContenedor.registerKeyboardAction(e -> frame.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    // ==================== ESTILO (solo UI) ====================
    private void aplicarEstilo() {
        // Fondo general (suave)
        if (panelContenedor != null) panelContenedor.setBackground(new Color(0xF3F4F6));

        // Tarjetas
        decorateAsCard(panelMain);
        decorateAsCard(panelDatos);
        decorateAsCard(panelLabels);
        decorateAsCard(panelBotones);
        decorateAsCard(panelContenedor);

        // Tipografías básicas
        setFontIfNotNull(txtNombre, fText);
        setFontIfNotNull(txtTelefono, fText);
        setFontIfNotNull(txtCurp, fText);
        setFontIfNotNull(txtRFC, fText);
        setFontIfNotNull(txtCorreo, fText);
        setFontIfNotNull(txtNSS, fText);
        setFontIfNotNull(cmbPensionado, fText);

        // Campos
        styleTextField(txtNombre);
        styleTextField(txtTelefono);
        styleTextField(txtCurp);
        styleTextField(txtRFC);
        styleTextField(txtCorreo);
        styleTextField(txtNSS);
        styleCombo(cmbPensionado);

        // Botones
        stylePrimaryButton(btnConfirmar);
        styleExitButton(btnCancelar);
    }

    private void setFontIfNotNull(JComponent c, Font f) { if (c != null) c.setFont(f); }

    private void styleTextField(JTextField tf) {
        if (tf == null) return;
        tf.setBackground(Color.WHITE);
        tf.setForeground(TEXT_PRIMARY);
        tf.setCaretColor(TEXT_PRIMARY);
        tf.setBorder(new CompoundBorder(new LineBorder(BORDER_SOFT, 1, true),
                new EmptyBorder(10, 12, 10, 12)));
        tf.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                tf.setBorder(new CompoundBorder(new LineBorder(BORDER_FOCUS, 2, true),
                        new EmptyBorder(10, 12, 10, 12)));
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                tf.setBorder(new CompoundBorder(new LineBorder(BORDER_SOFT, 1, true),
                        new EmptyBorder(10, 12, 10, 12)));
            }
        });
    }

    private void styleCombo(JComboBox<?> cb) {
        if (cb == null) return;
        cb.setBackground(Color.WHITE);
        cb.setForeground(TEXT_PRIMARY);
        cb.setBorder(new MatteBorder(1,1,1,1,BORDER_SOFT));
        cb.setFont(fText);
        //if (cb.getItemCount() > 0) cb.setPrototypeDisplayValue(cb.getItemAt(0));
    }

    // Botón verde principal (usa ModernButtonUI de PantallaAdmin)
    private void stylePrimaryButton(JButton b) {
        if (b == null) return;
        b.setUI(new PantallaAdmin.ModernButtonUI(GREEN_DARK, GREEN_SOFT, GREEN_DARK, Color.WHITE, 15, true));
        b.setBorder(new EmptyBorder(10,18,10,28));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // Botón rojo (usa ModernButtonUI de Login)
    private void styleExitButton(JButton b) {
        if (b == null) return;
        Color ROJO_BASE    = new Color(0xDC2626);
        Color GRIS_HOVER   = new Color(0xD1D5DB);
        Color GRIS_PRESSED = new Color(0x9CA3AF);
        b.setUI(new Login.ModernButtonUI(ROJO_BASE, GRIS_HOVER, GRIS_PRESSED, Color.BLACK, 22, true));
        b.setBorder(new EmptyBorder(10,18,10,28));
        b.setForeground(Color.WHITE);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void decorateAsCard(JComponent c) {
        if (c == null) return;
        c.setOpaque(true);
        c.setBackground(CARD_BG);
        c.setBorder(new PantallaAdmin.CompoundRoundShadowBorder(14, BORDER_SOFT, new Color(0,0,0,28)));
    }

    // ==================== LÓGICA ORIGINAL (sin cambios) ====================
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
                ps.setString(7, txtNSS.getText().trim());
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
