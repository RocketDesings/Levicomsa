import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;

public class CambiarContrasenaDialog extends JDialog {

    // ===== Contexto =====
    private final int usuarioId;
    private final boolean forzado;

    // ===== UI =====
    private JPasswordField txtActual;
    private JPasswordField txtNueva;
    private JPasswordField txtRepetir;
    private JButton btnGuardar;
    private JButton btnCancelar;

    // Paleta alineada con “Agregar Cliente”
    private static final Color GREEN_DARK   = new Color(0x0A6B2A);
    private static final Color GREEN_BASE   = new Color(0x16A34A);
    private static final Color GREEN_SOFT   = new Color(0x22C55E);
    private static final Color BG_CANVAS    = new Color(0xF3F4F6);
    private static final Color CARD_BG      = Color.WHITE;
    private static final Color TEXT_PRIMARY = new Color(0x111827);
    private static final Color TEXT_MUTED   = new Color(0x6B7280);
    private static final Color BORDER_SOFT  = new Color(0xE5E7EB);
    private static final Color BORDER_FOCUS = new Color(0x059669);

    public CambiarContrasenaDialog(int usuarioId, boolean forzado) {
        super((Frame) null, "Cambiar contraseña", true);
        this.usuarioId = usuarioId;
        this.forzado   = forzado;

        setDefaultCloseOperation(forzado ? DO_NOTHING_ON_CLOSE : DISPOSE_ON_CLOSE);
        setResizable(false);
        setUIFont(new Font("Segoe UI", Font.PLAIN, 13));
        setContentPane(buildContent());
        pack();
        setMinimumSize(new Dimension(560, 380));
        setLocationRelativeTo(null);

        // Atajos
        getRootPane().setDefaultButton(btnGuardar);
        if (!forzado) {
            getRootPane().registerKeyboardAction(
                    e -> dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_IN_FOCUSED_WINDOW
            );
        }
    }

    // ================== Construcción UI (estilo Agregar Cliente) ==================
    private JComponent buildContent() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_CANVAS);

        // Título grande centrado
        JLabel title = new JLabel("CAMBIAR CONTRASEÑA", SwingConstants.CENTER);
        title.setForeground(TEXT_PRIMARY);
        title.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 22));
        title.setBorder(new EmptyBorder(12, 12, 4, 12));
        root.add(title, BorderLayout.NORTH);

        // Panel formulario (card blanca)
        JPanel formCard = new JPanel(new GridBagLayout());
        formCard.setOpaque(true);
        formCard.setBackground(CARD_BG);
        formCard.setBorder(new MatteBorder(1,1,1,1, BORDER_SOFT));
        root.add(formCard, BorderLayout.CENTER);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10, 14, 0, 14);
        c.anchor = GridBagConstraints.WEST;
        c.fill   = GridBagConstraints.HORIZONTAL;

        // ===== Fila 1: Actual =====
        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        formCard.add(label("Contraseña actual:"), c);

        c.gridx = 1; c.weightx = 1.0;
        txtActual = new JPasswordField();
        stylePasswordField(txtActual);
        formCard.add(txtActual, c);

        c.gridx = 2; c.weightx = 0;
        formCard.add(crearChkMostrar(txtActual), c);

        // ===== Fila 2: Nueva =====
        c.gridx = 0; c.gridy++; c.weightx = 0;
        formCard.add(label("Nueva contraseña:"), c);

        c.gridx = 1; c.weightx = 1.0;
        txtNueva = new JPasswordField();
        stylePasswordField(txtNueva);
        formCard.add(txtNueva, c);

        c.gridx = 2; c.weightx = 0;
        formCard.add(crearChkMostrar(txtNueva), c);

        // ===== Fila 3: Repetir =====
        c.gridx = 0; c.gridy++; c.weightx = 0;
        formCard.add(label("Repetir contraseña:"), c);

        c.gridx = 1; c.weightx = 1.0;
        txtRepetir = new JPasswordField();
        stylePasswordField(txtRepetir);
        formCard.add(txtRepetir, c);

        c.gridx = 2; c.weightx = 0;
        formCard.add(crearChkMostrar(txtRepetir), c);

        // Tips
        c.gridx = 1; c.gridy++; c.gridwidth = 2;
        JLabel tips = new JLabel("Mínimo 8 caracteres. Usa mayúsculas, minúsculas y números.");
        tips.setForeground(TEXT_MUTED);
        tips.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tips.setBorder(new EmptyBorder(4, 2, 8, 2));
        formCard.add(tips, c);

        // ===== Botones al pie (estilo Agregar/Cancelar) =====
        JPanel footer = new JPanel(new GridBagLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.add(footer, BorderLayout.SOUTH);

        GridBagConstraints f = new GridBagConstraints();
        f.gridx = 0; f.gridy = 0; f.weightx = 1; f.insets = new Insets(0, 6, 0, 6);
        f.fill = GridBagConstraints.HORIZONTAL;

        btnGuardar = new JButton("Guardar");
        stylePrimaryButton(btnGuardar);
        footer.add(btnGuardar, f);

        if (!forzado) {
            f.gridx = 1;
            btnCancelar = new JButton("Cancelar");
            styleDangerButton(btnCancelar);
            footer.add(btnCancelar, f);
        }

        // Acciones
        btnGuardar.addActionListener(e -> onGuardar());
        if (!forzado) btnCancelar.addActionListener(e -> dispose());

        // Igualar tamaños
        equalizeButtons(btnGuardar, btnCancelar);

        return root;
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_PRIMARY);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        return l;
    }

    private void equalizeButtons(JButton primary, JButton secondary) {
        Dimension ref = primary.getPreferredSize();
        if (secondary != null) {
            Dimension r2 = secondary.getPreferredSize();
            int w = Math.max(ref.width, r2.width);
            int h = Math.max(ref.height, r2.height);
            ref = new Dimension(w, h);
            secondary.setPreferredSize(ref);
        }
        primary.setPreferredSize(ref);
    }

    // ================ Lógica Guardar =================
    private void onGuardar() {
        String actual  = new String(txtActual.getPassword());
        String nueva   = new String(txtNueva.getPassword());
        String repetir = new String(txtRepetir.getPassword());

        if (actual.isBlank() || nueva.isBlank() || repetir.isBlank()) {
            alert("Completa todos los campos.");
            return;
        }
        if (nueva.length() < 8) {
            alert("La nueva contraseña debe tener al menos 8 caracteres.");
            txtNueva.requestFocusInWindow();
            return;
        }
        if (!nueva.equals(repetir)) {
            alert("La nueva contraseña y su repetición no coinciden.");
            txtRepetir.requestFocusInWindow();
            return;
        }
        if (nueva.equals(actual)) {
            alert("La nueva contraseña debe ser distinta a la actual.");
            txtNueva.requestFocusInWindow();
            return;
        }

        // Verificar contraseña actual
        String hash = null, plano = null;
        final String sqlSel = "SELECT password_hash, `contraseña` FROM Usuarios WHERE id=? LIMIT 1";
        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(sqlSel)) {
            ps.setInt(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    hash  = rs.getString(1);
                    plano = rs.getString(2);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            alert("No se pudo verificar la contraseña actual.");
            return;
        }

        if (!comprobarPassword(actual, hash, plano)) {
            alert("La contraseña actual no es correcta.");
            txtActual.requestFocusInWindow();
            return;
        }

        // Guardar nueva: PBKDF2 y limpiar columna legada
        String nuevoHash = Passwords.hash(nueva.toCharArray());
        final String sqlUpd = "UPDATE Usuarios SET password_hash=?, `contraseña`=NULL WHERE id=?";
        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(sqlUpd)) {
            ps.setString(1, nuevoHash);
            ps.setInt(2, usuarioId);
            int upd = ps.executeUpdate();
            if (upd > 0) {
                JOptionPane.showMessageDialog(this, "Contraseña actualizada correctamente.");
                dispose();
            } else {
                alert("No se pudo actualizar la contraseña.");
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            alert("Error al actualizar la contraseña:\n" + ex.getMessage());
        }
    }

    // =========== Utilidades de verificación ===========
    private boolean comprobarPassword(String plain, String hash, String planoLegado) {
        if (hash != null && !hash.isBlank()) {
            if (hash.startsWith("pbkdf2$")) {
                return Passwords.verify(plain.toCharArray(), hash);
            }
            if (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$")) {
                try {
                    Class<?> c = Class.forName("org.mindrot.jbcrypt.BCrypt");
                    java.lang.reflect.Method checkpw = c.getMethod("checkpw", String.class, String.class);
                    Object ok = checkpw.invoke(null, plain, hash);
                    return (ok instanceof Boolean) && (Boolean) ok;
                } catch (ClassNotFoundException cnfe) {
                    alert("El usuario tiene hash BCrypt pero falta la librería jBCrypt (org.mindrot:jbcrypt:0.4).");
                    return false;
                } catch (Exception e) { return false; }
            }
            return plain.equals(hash); // fallback (no debería)
        }
        return planoLegado != null && plain.equals(planoLegado);
    }

    // ================= Estilo reutilizable =================
    private void stylePasswordField(JPasswordField pf) {
        pf.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        pf.setForeground(TEXT_PRIMARY);
        pf.setBackground(Color.WHITE);
        pf.setCaretColor(TEXT_PRIMARY);
        pf.setBorder(new CompoundBorderRounded(BORDER_SOFT, 12, 1, new Insets(10, 12, 10, 12)));
        pf.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                pf.setBorder(new CompoundBorderRounded(BORDER_FOCUS, 12, 2, new Insets(10,12,10,12)));
            }
            @Override public void focusLost(FocusEvent e) {
                pf.setBorder(new CompoundBorderRounded(BORDER_SOFT, 12, 1, new Insets(10,12,10,12)));
            }
        });
    }

    private JCheckBox crearChkMostrar(JPasswordField target) {
        // guarda el echoChar actual para restaurarlo
        final char[] defEcho = { target.getEchoChar() };
        if (defEcho[0] == 0) {
            Object o = UIManager.get("PasswordField.echoChar");
            defEcho[0] = (o instanceof Character) ? (Character) o : '\u2022';
        }

        JCheckBox chk = new JCheckBox("Mostrar");
        chk.setOpaque(false);
        chk.setForeground(TEXT_PRIMARY);
        chk.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chk.addActionListener(e -> {
            if (chk.isSelected()) target.setEchoChar((char)0);
            else target.setEchoChar(defEcho[0]);
        });
        return chk;
    }

    private void stylePrimaryButton(JButton b) {
        b.setUI(new ModernButtonUI(GREEN_BASE, GREEN_SOFT, GREEN_DARK, Color.WHITE, 12, true));
        b.setBorder(new EmptyBorder(12,18,12,18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    private void styleDangerButton(JButton b) {
        Color ROJO = new Color(0xDC2626);
        b.setUI(new ModernButtonUI(ROJO, ROJO.brighter(), ROJO.darker(), Color.WHITE, 12, true));
        b.setBorder(new EmptyBorder(12,18,12,18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // ================= Clases de estilo =================
    static class ModernButtonUI extends BasicButtonUI {
        private final Color bg, hover, press, fg; private final int arc; private final boolean filled;
        ModernButtonUI(Color bg, Color hover, Color press, Color fg, int arc, boolean filled) {
            this.bg=bg; this.hover=hover; this.press=press; this.fg=fg; this.arc=arc; this.filled=filled;
        }
        @Override public void installUI(JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setOpaque(false);
            b.setRolloverEnabled(true);
            b.setFocusPainted(false);
            b.setForeground(fg);
        }
        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ButtonModel m = b.getModel();
            Color fill = m.isPressed() ? press : (m.isRollover() ? hover : bg);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), arc, arc);
            g2.dispose();
            super.paint(g, c);
        }
    }
    static class CompoundBorderRounded extends javax.swing.border.CompoundBorder {
        CompoundBorderRounded(Color line, int arc, int thickness, Insets innerPad) {
            super(new RoundedLineBorder(line, arc, thickness), new EmptyBorder(innerPad));
        }
    }
    static class RoundedLineBorder extends javax.swing.border.AbstractBorder {
        private final Color color; private final int arc; private final int thickness;
        RoundedLineBorder(Color color, int arc, int thickness) { this.color = color; this.arc = arc; this.thickness = thickness; }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            for (int i = 0; i < thickness; i++) {
                g2.drawRoundRect(x + i, y + i, w - 1 - 2 * i, h - 1 - 2 * i, arc, arc);
            }
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) { return new Insets(thickness, thickness, thickness, thickness); }
        @Override public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(thickness, thickness, thickness, thickness); return insets;
        }
    }

    private void setUIFont(Font f) {
        try {
            var keys = UIManager.getDefaults().keys();
            while (keys.hasMoreElements()) {
                Object k = keys.nextElement();
                Object v = UIManager.get(k);
                if (v instanceof Font) UIManager.put(k, f);
            }
        } catch (Exception ignored) {}
    }

    private void alert(String msg) { JOptionPane.showMessageDialog(this, msg); }
}
