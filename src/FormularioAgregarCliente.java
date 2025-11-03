import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.JLayer;
import javax.swing.plaf.LayerUI;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;
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

    // ===== Paleta (igual que Login) =====
    private static final Color GREEN_DARK   = new Color(0x0A6B2A);
    private static final Color GREEN_BASE   = new Color(0x16A34A);
    private static final Color GREEN_SOFT   = new Color(0x22C55E);
    private static final Color BG_TOP       = new Color(0x052E16);
    private static final Color BG_BOT       = new Color(0x064E3B);
    private static final Color CARD_BG      = new Color(255, 255, 255);
    private static final Color TEXT_PRIMARY = new Color(0x111827);
    private static final Color TEXT_MUTED   = new Color(0x67676E);
    private static final Color BORDER_SOFT  = new Color(0x535353);
    private static final Color BORDER_FOCUS = new Color(0x059669);

    public FormularioAgregarCliente(Refrescable pantallaPrincipal, int usuarioId) {
        this.pantallaPrincipal = pantallaPrincipal;
        this.usuarioId = usuarioId;
        JFrame frame = new JFrame("Login");
        frame.setUndecorated(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setTitle("Agregar Cliente");
        frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Fuente global consistente
        try { setUIFont(new Font("Segoe UI", Font.PLAIN, 13)); } catch (Exception ignored) {}

        // ===== Contenido estilo Login: gradiente + card redondeada =====
        GradientPanel root = new GradientPanel(BG_TOP, BG_BOT);
        root.setLayout(new GridBagLayout());

        // Tarjeta con sombra y esquinas redondeadas que envuelve tu mainPanel
        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                frame.setShape(new java.awt.geom.RoundRectangle2D.Double(
                        0, 0, frame.getWidth(), frame.getHeight(), 24, 24
                ));
            }
        });
        JLayer<JComponent> roundedLayer = new JLayer<>(mainPanel, new Login.RoundedClipUI(10)); // 20 = radio de esquina
        frame.setContentPane(roundedLayer);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // ===== Tipografía básica =====
        Font fText = new Font("Segoe UI", Font.PLAIN, 16);
        if (txtNombre    != null) txtNombre.setFont(fText);
        if (txtTelefono  != null) txtTelefono.setFont(fText);
        if (txtCurp      != null) txtCurp.setFont(fText);
        if (txtRFC       != null) txtRFC.setFont(fText);
        if (txtCorreo    != null) txtCorreo.setFont(fText);
        if (txtNSS       != null) txtNSS.setFont(fText);
        if (btnAgregar   != null) btnAgregar.setFont(fText);
        if (btnCancelar  != null) btnCancelar.setFont(fText);

        // ===== Campos con borde reactivo (igual que Login) =====

        styleTextField(txtNombre);
        styleTextField(txtTelefono);
        styleTextField(txtCurp);
        styleTextField(txtRFC);
        styleTextField(txtCorreo);
        styleTextField(txtNSS);

        // ===== Botones (consistentes con Login) =====
        if (btnAgregar != null) {
            stylePrimaryButton(btnAgregar);
            btnAgregar.setBorder(new EmptyBorder(12, 18, 12, 18));
            btnAgregar.addActionListener(e -> agregarCliente());
        }
        if (btnCancelar != null) {
            styleExitButton(btnCancelar); // <- rojo sólido
            btnCancelar.setBorder(new EmptyBorder(12, 18, 12, 18));
            btnCancelar.addActionListener(e -> {
                dispose();
                if (pantallaPrincipal != null) pantallaPrincipal.refrescarDatos();
            });
        }

        // ESC para cerrar (tu lógica original)
        if (mainPanel != null) {
            mainPanel.registerKeyboardAction(
                    e -> { dispose(); if (pantallaPrincipal != null) pantallaPrincipal.refrescarDatos(); },
                    KeyStroke.getKeyStroke("ESCAPE"),
                    JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
            );
        }
    }

    // ======================= ESTILO (igual que Login) =======================

    private void styleTextField(JTextField tf) {
        if (tf == null) return;
        tf.setOpaque(true);
        tf.setBackground(Color.WHITE);
        tf.setForeground(TEXT_PRIMARY);
        tf.setCaretColor(TEXT_PRIMARY);
        tf.setBorder(new CompoundBorderRounded(BORDER_SOFT, 12, 1, new Insets(10, 12, 10, 12)));
        tf.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                tf.setBorder(new CompoundBorderRounded(BORDER_FOCUS, 12, 2, new Insets(10,12,10,12)));
            }
            @Override public void focusLost(FocusEvent e) {
                tf.setBorder(new CompoundBorderRounded(BORDER_SOFT, 12, 1, new Insets(10,12,10,12)));
            }
        });
    }

    private void stylePrimaryButton(JButton b) {
        b.setUI(new ModernButtonUI(GREEN_BASE, GREEN_SOFT, GREEN_DARK, Color.WHITE, 12, true));
        b.setForeground(Color.WHITE);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void styleOutlineButton(JButton b) {
        // Outline sobrio (igual patrón que en Admin/Asesor)
        b.setUI(new ModernButtonUI(new Color(0,0,0,0), new Color(0,0,0,25), new Color(0,0,0,45), TEXT_PRIMARY, 12, false));
        b.setForeground(TEXT_PRIMARY);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // “Card” blanca con sombra y borde sutil (idéntica a Login)
    private void decorateAsCard(JComponent c) {
        if (c == null) return;
        c.setOpaque(true);
        c.setBackground(CARD_BG);
        c.setBorder(new CompoundRoundShadowBorder(14, BORDER_SOFT, new Color(0,0,0,28)));
    }

    // ======= Helpers de estilo reutilizados =======

    // Clip redondeado sin tocar la jerarquía
    static class RoundedClipUI extends LayerUI<JComponent> {
        private final int arc;
        RoundedClipUI(int arc) { this.arc = arc; }
        @Override public void paint(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape clip = new RoundRectangle2D.Float(0, 0, c.getWidth(), c.getHeight(), arc * 2f, arc * 2f);
            Shape old = g2.getClip();
            g2.setClip(clip);
            super.paint(g2, c);
            g2.setClip(old);
            g2.dispose();
        }
    }

    // Fondo gradiente (igual que Login)
    static class GradientPanel extends JPanel {
        private final Color top, bot;
        GradientPanel(Color top, Color bot) { this.top = top; this.bot = bot; setOpaque(true); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            GradientPaint gp = new GradientPaint(0, 0, top, 0, getHeight(), bot);
            g2.setPaint(gp);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // Botón moderno
    static class ModernButtonUI extends BasicButtonUI {
        private final Color bg, hover, press, fg;
        private final int arc; private final boolean filled;
        ModernButtonUI(Color bg, Color hover, Color press, Color fg, int arc, boolean filled) {
            this.bg = bg; this.hover = hover; this.press = press; this.fg = fg; this.arc = arc; this.filled = filled;
        }
        @Override public void installUI(JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setOpaque(false);
            b.setBorder(new EmptyBorder(12, 18, 12, 18));
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
            if (filled || fill.getAlpha() > 0) {
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), arc, arc);
            } else {
                g2.setColor(new Color(255, 255, 255, 100));
                g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, arc, arc);
            }
            g2.dispose();
            super.paint(g, c);
        }
    }

    // Borde para inputs (igual que Login)
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
            for (int i = 0; i < thickness; i++) g2.drawRoundRect(x + i, y + i, w - 1 - 2 * i, h - 1 - 2 * i, arc, arc);
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) { return new Insets(thickness, thickness, thickness, thickness); }
        @Override public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(thickness, thickness, thickness, thickness); return insets;
        }
    }

    // “Card” con sombra sutil
    static class CompoundRoundShadowBorder extends EmptyBorder {
        private final int arc; private final Color border; private final Color shadow;
        public CompoundRoundShadowBorder(int arc, Color border, Color shadow) {
            super(12,12,12,12);
            this.arc = arc; this.border = border; this.shadow = shadow;
        }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(shadow);
            for (int i=0;i<8;i++) {
                float alpha = 0.08f;
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2.fillRoundRect(x+2+i, y+4+i, w-4, h-6, arc, arc);
            }
            g2.setComposite(AlphaComposite.SrcOver);
            g2.setColor(border);
            g2.drawRoundRect(x+1, y+1, w-3, h-3, arc, arc);
            g2.dispose();
        }
    }

    private void styleExitButton(JButton b) {
        Color ROJO_BASE    = new Color(0xDC2626); // rojo principal
        Color ROJO_HOVER   = new Color(0xEF4444); // hover (más claro)
        Color ROJO_PRESSED = new Color(0xB91C1C); // pressed (más oscuro)
        b.setUI(new ModernButtonUI(ROJO_BASE, ROJO_HOVER, ROJO_PRESSED, Color.WHITE, 12, true));
        b.setForeground(Color.WHITE);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // ======================= LÓGICA ORIGINAL (SIN CAMBIOS) =======================
    private void agregarCliente() {
        // (… tu mismo código original sin tocar …)
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

        String nss = txtNSS != null ? txtNSS.getText().trim() : "";
        if (!nss.isEmpty()) {
            if (!nss.matches("\\d{11,15}")) {
                mostrarError("El NSS debe contener solo dígitos (11 a 15).", txtNSS);
                return;
            }
        } else {
            nss = null;
        }

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
    }
    private void mostrarError(String mensaje, JTextField campo) {
        JOptionPane.showMessageDialog(this, mensaje, "Validación", JOptionPane.WARNING_MESSAGE);
        if (campo != null) campo.requestFocus();
    }

    // ===== Fuente global (igual que Login) =====
    private void setUIFont(Font f) {
        var keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object k = keys.nextElement();
            Object v = UIManager.get(k);
            if (v instanceof Font) UIManager.put(k, f);
        }
    }
}
