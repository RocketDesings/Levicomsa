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
    private JTextField txtNotas; // YA está en tu .form

    private JFrame frame;

    // ======= PALETA & FUENTES =======
    private static final Color TEXT_PRIMARY = new Color(0x111827);
    private static final Color BORDER_SOFT  = new Color(0x535353);
    private static final Color CARD_BG      = new Color(255, 255, 255);
    private static final Color GREEN_DARK   = new Color(0x0A6B2A);
    private static final Color GREEN_SOFT   = new Color(0x22C55E);
    private static final Color BORDER_FOCUS = new Color(0x059669);
    private final Font fText   = new Font("Segoe UI", Font.PLAIN, 16);
    private final Font fTitle  = new Font("Segoe UI", Font.BOLD, 22);

    // ===== Constructores =====

    // **NUEVO y RECOMENDADO**: exactamente como los demás campos, ahora también recibimos "notas".
    public ModificarCliente(Refrescable refrescable,
                            String nombre, String telefono, String curp, String rfc,
                            String correo, String pensionado, String nss, String notas, int usuarioId) {
        this.refrescable = refrescable;
        this.usuarioId   = usuarioId;
        this.rfcOriginal = rfc;

        construirVentana();
        aplicarEstilo();

        if (cmbPensionado.getItemCount() == 0) { cmbPensionado.addItem("Sí"); cmbPensionado.addItem("No"); }

        // Seteo "igual que los demás"
        txtNombre.setText(nullSafe(nombre));
        txtTelefono.setText(nullSafe(telefono));
        txtCurp.setText(nullSafe(curp));
        txtRFC.setText(nullSafe(rfc));
        txtCorreo.setText(nullSafe(correo));
        txtNSS.setText(nullSafe(nss));
        cmbPensionado.setSelectedItem(pensionado != null ? pensionado : "No");
        if (txtNotas != null) txtNotas.setText(nullSafe(notas)); // ← AQUÍ la nota

        instalarAtajosYAcciones();

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // Compatibilidad con firmas existentes (si en algún lugar aún no pasas "notas")
    public ModificarCliente(Refrescable refrescable,
                            String nombre, String telefono, String curp, String rfc,
                            String correo, String pensionado, String nss, int usuarioId) {
        this(refrescable, nombre, telefono, curp, rfc, correo, pensionado, nss, /*notas*/"", usuarioId);
    }

    public ModificarCliente(Refrescable refrescable,
                            String nombre, String telefono, String curp, String rfc,
                            String correo, String pensionado, int usuarioId) {
        this(refrescable, nombre, telefono, curp, rfc, correo, pensionado, leerNSSporRFC(rfc), /*notas*/"", usuarioId);
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
        if (panelContenedor != null) panelContenedor.setBackground(new Color(0xF3F4F6));

        decorateAsCard(panelMain);
        decorateAsCard(panelDatos);
        decorateAsCard(panelLabels);
        decorateAsCard(panelBotones);
        decorateAsCard(panelContenedor);

        setFontIfNotNull(txtNombre, fText);
        setFontIfNotNull(txtTelefono, fText);
        setFontIfNotNull(txtCurp, fText);
        setFontIfNotNull(txtRFC, fText);
        setFontIfNotNull(txtCorreo, fText);
        setFontIfNotNull(txtNSS, fText);
        setFontIfNotNull(cmbPensionado, fText);
        setFontIfNotNull(txtNotas, fText);

        styleTextField(txtNombre);
        styleTextField(txtTelefono);
        styleTextField(txtCurp);
        styleTextField(txtRFC);
        styleTextField(txtCorreo);
        styleTextField(txtNSS);
        styleTextField(txtNotas); // ← estilizado igual que otros
        styleCombo(cmbPensionado);

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
    }

    private void stylePrimaryButton(JButton b) {
        if (b == null) return;
        b.setUI(new PantallaAdmin.ModernButtonUI(GREEN_DARK, GREEN_SOFT, GREEN_DARK, Color.WHITE, 15, true));
        b.setBorder(new EmptyBorder(10,18,10,28));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

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

    // ==================== LÓGICA ====================
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

        // Normaliza campos: vacío -> null; mayúsculas cuando aplique
        String nombre   = emptyToNull(txtNombre.getText());
        String telefono = txtTelefono.getText().trim(); // único obligatorio
        String curp     = toUpperOrNull(txtCurp.getText());
        String rfcNuevo = toUpperOrNull(txtRFC.getText());
        String correo   = emptyToNull(txtCorreo.getText());
        String nss      = emptyToNull(txtNSS.getText());
        String notas    = emptyToNull(txtNotas.getText());
        boolean pen     = isSi(cmbPensionado.getSelectedItem());

        // ¡CLAVE!: null-safe en el WHERE (coincide cuando ambos son NULL)
        final String sql = """
        UPDATE Clientes
           SET nombre=?,
               telefono=?,
               CURP=?,
               RFC=?,
               correo=?,
               pensionado=?,
               NSS=?,
               notas=?,
               actualizado_en=NOW()
         WHERE RFC <=> ?
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
                setStringOrNull(ps, 1, nombre);
                ps.setString(2, telefono);                // obligatorio
                setStringOrNull(ps, 3, curp);
                setStringOrNull(ps, 4, rfcNuevo);
                setStringOrNull(ps, 5, correo);
                ps.setBoolean(6, pen);
                setStringOrNull(ps, 7, nss);
                setStringOrNull(ps, 8, notas);

                // WHERE: usa el RFC original pero normalizado a NULL si venía vacío
                setStringOrNull(ps, 9, emptyToNull(rfcOriginal));

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
        // ÚNICO obligatorio: Teléfono
        if(vacio(txtNombre)){
            warn("El nombre es obligatorio.", txtNombre);
            return false;
        }
        if (vacio(txtTelefono)) {
            warn("El teléfono es obligatorio.", txtTelefono);
            return false;
        }

        // (Opcional) regla de formato para teléfono: 10–15 dígitos numéricos
        String tel = txtTelefono.getText().trim();
        if (!tel.matches("\\d{10,15}")) {
            warn("El teléfono debe ser numérico de 10 a 15 dígitos.", txtTelefono);
            return false;
        }

        // RFC, CURP y Correo ya NO son obligatorios
        // if (vacio(txtRFC))  ...  -> ELIMINADO
        // if (vacio(txtCurp)) ...  -> ELIMINADO
        // if (vacio(txtCorreo))... -> ELIMINADO

        // NSS: solo validar si lo escriben
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

    /* ===== Helpers ===== */
    private static void setStringOrNull(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null) ps.setNull(idx, java.sql.Types.VARCHAR); else ps.setString(idx, val);
    }
    private static String emptyToNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }
    private static String toUpperOrNull(String s) {
        s = emptyToNull(s);
        return s == null ? null : s.toUpperCase();
    }
}
