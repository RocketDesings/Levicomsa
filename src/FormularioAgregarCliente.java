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
    private JPanel panelDatos;
    private JPanel panelInfo;
    private JPanel panelBotones;
    private JTextField txtNotas;

    private final Refrescable pantallaPrincipal;

    // ===== Paleta (igual que Login) =====
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
        styleTextField(txtNotas);
        decorateAsCard(panelBotones);
        decorateAsCard(panelDatos);
        decorateAsCard(mainPanel);
        decorateAsCard(panelInfo);
        styleCheckBox(chechPensionado);



        // ===== Botones (consistentes con Login) =====
        if (btnAgregar != null) {
            stylePrimaryButton(btnAgregar);
            btnAgregar.addActionListener(e -> agregarCliente());
        }
        if (btnCancelar != null) {
            styleExitButton(btnCancelar); // <- rojo sólido
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

    private void styleOutlineButton(JButton b) {
        // Outline sobrio (igual patrón que en Admin/Asesor)
        b.setUI(new ModernButtonUI(new Color(0,0,0,0), new Color(0,0,0,25), new Color(0,0,0,45), TEXT_PRIMARY, 12, false));
        b.setForeground(TEXT_PRIMARY);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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

    // ======================= LÓGICA ORIGINAL (SIN CAMBIOS) =======================
    private void agregarCliente() {
        // ===== VALIDACIONES (solo teléfono obligatorio) =====
        // Teléfono: obligatorio, solo números, máx 15
        if (!ValidarJTextField.validarNoVacio(txtTelefono)) {
            mostrarError("El teléfono no puede estar vacío.", txtTelefono); return;
        }
        if (!ValidarJTextField.validarSoloNumeros(txtTelefono)) {
            mostrarError("El teléfono solo debe contener números.", txtTelefono); return;
        }
        if (!ValidarJTextField.validarLongitudMaxima(txtTelefono, 15)) {
            mostrarError("El teléfono no debe exceder los 15 dígitos.", txtTelefono); return;
        }

        // Nombre: OPCIONAL (si se escribe, validar)
        if (!ValidarJTextField.validarLongitudMaxima(txtNombre, 100)) {
            mostrarError("El nombre no debe superar los 100 caracteres.", txtNombre); return;
        }
        String nombre = txtNombre != null ? txtNombre.getText().trim() : "";
        if (!nombre.isEmpty() && !ValidarJTextField.validarSoloLetras(txtNombre)) {
            mostrarError("El nombre solo debe contener letras.", txtNombre); return;
        }

        // CURP: OPCIONAL (si se escribe, validar formato/longitud; vacío -> NULL)
        String curp = txtCurp != null ? txtCurp.getText().trim().toUpperCase() : "";
        if (curp.isEmpty()) {
            curp = null;
        } else {
            if (!ValidarJTextField.validarLongitudExacta(txtCurp, 18)) {
                mostrarError("La CURP debe tener exactamente 18 caracteres.", txtCurp); return;
            }
            if (!ValidarJTextField.validarCURP(txtCurp)) {
                mostrarError("La CURP no tiene un formato válido.", txtCurp); return;
            }
        }

        // RFC: OPCIONAL (si se escribe, validar; vacío -> NULL)
        String rfc = txtRFC != null ? txtRFC.getText().trim().toUpperCase() : "";
        if (rfc.isEmpty()) {
            rfc = null;
        } else {
            if (!ValidarJTextField.validarLongitudExacta(txtRFC, 13)) {
                mostrarError("El RFC debe tener exactamente 13 caracteres.", txtRFC); return;
            }
            if (!ValidarJTextField.validarRFC(txtRFC)) {
                mostrarError("El RFC no tiene un formato válido.", txtRFC); return;
            }
        }

        // Correo: OPCIONAL (si se escribe, validar; vacío -> NULL)
        String correo = txtCorreo != null ? txtCorreo.getText().trim() : "";
        if (correo.isEmpty()) {
            correo = null;
        } else {
            if (!ValidarJTextField.validarEmail(txtCorreo)) {
                mostrarError("El correo no tiene un formato válido.", txtCorreo); return;
            }
            if (!ValidarJTextField.validarLongitudMaxima(txtCorreo, 100)) {
                mostrarError("El correo no debe superar los 100 caracteres.", txtCorreo); return;
            }
        }

        // NSS: OPCIONAL (si se escribe, validar dígitos 11-15; vacío -> NULL)
        String nss = txtNSS != null ? txtNSS.getText().trim() : "";
        if (!nss.isEmpty()) {
            if (!nss.matches("\\d{11,15}")) {
                mostrarError("El NSS debe contener solo dígitos (11 a 15).", txtNSS);
                return;
            }
        } else {
            nss = null;
        }

        // Notas: OPCIONAL (vacío -> NULL)
        String notas = (txtNotas != null) ? txtNotas.getText().trim() : "";
        if (notas.isEmpty()) notas = null;

        // Pensionado (checkbox)
        boolean pensionado = chechPensionado != null && chechPensionado.isSelected();

        // Teléfono (obligatorio, ya validado)
        String telefono = txtTelefono.getText().trim();

        final String sql = "INSERT INTO Clientes (nombre, telefono, CURP, pensionado, RFC, NSS, correo, notas) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DB.get()) {
            conn.setAutoCommit(false);

            if (usuarioId > 0) {
                try (PreparedStatement ps = conn.prepareStatement("SET @app_user_id = ?")) {
                    ps.setInt(1, usuarioId);
                    ps.executeUpdate();
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                // 1) nombre (si lo quieres NULL cuando viene vacío, cambia a setNull)
                ps.setString(1, nombre);

                // 2) telefono (obligatorio)
                ps.setString(2, telefono);

                // 3) CURP (NULL si vacío)
                if (curp == null) ps.setNull(3, java.sql.Types.VARCHAR);
                else              ps.setString(3, curp);

                // 4) pensionado
                ps.setBoolean(4, pensionado);

                // 5) RFC (NULL si vacío)
                if (rfc == null)  ps.setNull(5, java.sql.Types.VARCHAR);
                else              ps.setString(5, rfc);

                // 6) NSS (NULL si vacío)
                if (nss == null)  ps.setNull(6, java.sql.Types.VARCHAR);
                else              ps.setString(6, nss);

                // 7) correo (NULL si vacío)
                if (correo == null) ps.setNull(7, java.sql.Types.VARCHAR);
                else                ps.setString(7, correo);

                // 8) notas (NULL si vacío)
                if (notas == null) ps.setNull(8, java.sql.Types.VARCHAR);
                else               ps.setString(8, notas);

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
    private void stylePrimaryButton(JButton b) {
        // Igual que pantallaCajero: usa ModernButtonUI de PantallaAdmin
        b.setUI(new PantallaAdmin.ModernButtonUI(GREEN_DARK, GREEN_SOFT, GREEN_DARK, Color.WHITE, 15, true));
        b.setBorder(new EmptyBorder(10,18,10,28));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setForeground(Color.WHITE);
    }

    // Botón rojo consistente con tu estilo
    private void styleExitButton(JButton b) {
        Color ROJO_BASE    = new Color(0xDC2626);
        Color GRIS_HOVER   = new Color(0xD1D5DB);
        Color GRIS_PRESSED = new Color(0x9CA3AF);
        b.setUI(new Login.ModernButtonUI(ROJO_BASE, GRIS_HOVER, GRIS_PRESSED, Color.BLACK, 22, true));
        b.setBorder(new EmptyBorder(10,18,10,28));
        b.setForeground(Color.WHITE);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
    }

    private void decorateAsCard(JComponent c) {
        if (c == null) return;
        c.setOpaque(true);
        c.setBackground(CARD_BG);
        c.setBorder(new PantallaAdmin.CompoundRoundShadowBorder(14, BORDER_SOFT, new Color(0,0,0,28)));
    }

    private void styleTextField(JTextField tf) {
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
    // ======================= SOLO ESTILO CHECKBOX =======================

    private void styleCheckBox(JCheckBox cb) {
        if (cb == null) return;
        cb.setOpaque(false);
        cb.setFocusPainted(false);
        cb.setRolloverEnabled(true);
        cb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cb.setForeground(TEXT_PRIMARY);
        cb.setFont(fText);
        cb.setIconTextGap(10);

        // Iconos personalizados (normal, seleccionado y sus variantes hover)
        cb.setIcon(new CheckIcon(false));
        cb.setSelectedIcon(new CheckIcon(true));
        cb.setRolloverIcon(new CheckIcon(false, true));
        cb.setRolloverSelectedIcon(new CheckIcon(true, true));

        // Borde/foco visual alrededor del texto cuando toma foco (no cambia lógica)
        cb.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { cb.repaint(); }
            @Override public void focusLost(FocusEvent e) { cb.repaint(); }
        });
    }

    /* Icono minimalista para JCheckBox con esquinas redondeadas, hover y foco. */
    static class CheckIcon implements Icon {
        private final int size;
        private final boolean selected;
        private final boolean hoverVariant;

        // Paleta (usa los mismos colores que ya tienes en la clase)
        private static final Color BOX_BG         = Color.WHITE;
        private static final Color BOX_BORDER     = new Color(0x535353); // BORDER_SOFT
        private static final Color BOX_BG_HOVER   = new Color(0xF3F4F6);
        private static final Color BOX_BG_SELECTED= new Color(0x16A34A); // GREEN_BASE
        private static final Color BOX_BG_SEL_HOV = new Color(0x22C55E); // GREEN_SOFT
        private static final Color CHECK_COLOR    = Color.WHITE;
        private static final Color FOCUS_RING     = new Color(0x059669); // BORDER_FOCUS

        CheckIcon(boolean selected) { this(selected, false); }
        CheckIcon(boolean selected, boolean hoverVariant) {
            this.size = 18; // tamaño del cuadro (HiDPI friendly)
            this.selected = selected;
            this.hoverVariant = hoverVariant;
        }

        @Override public int getIconWidth()  { return size; }
        @Override public int getIconHeight() { return size; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            AbstractButton b = (AbstractButton) c;
            boolean rollover = b.getModel().isRollover() || hoverVariant;
            boolean focus = b.isFocusOwner();

            int arc = Math.round(size * 0.35f); // radio redondeado
            int pad = 1;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Fondo caja
            Color fill;
            if (selected) {
                fill = rollover ? BOX_BG_SEL_HOV : BOX_BG_SELECTED;
            } else {
                fill = rollover ? BOX_BG_HOVER : BOX_BG;
            }
            g2.setColor(fill);
            g2.fillRoundRect(x + pad, y + pad, size - pad*2, size - pad*2, arc, arc);

            // Borde caja (solo cuando NO está seleccionada; seleccionada ya es sólida)
            if (!selected) {
                g2.setColor(BOX_BORDER);
                g2.drawRoundRect(x + pad, y + pad, size - pad*2, size - pad*2, arc, arc);
            }

            // Anillo de foco sutil
            if (focus) {
                g2.setColor(new Color(FOCUS_RING.getRed(), FOCUS_RING.getGreen(), FOCUS_RING.getBlue(), 90));
                int fpad = pad - 1; // un poco más grande
                g2.drawRoundRect(x + fpad, y + fpad, size - fpad*2, size - fpad*2, arc + 4, arc + 4);
            }

            // Check (dibujamos solo si está seleccionado)
            if (selected) {
                g2.setStroke(new BasicStroke(Math.max(2f, size * 0.13f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(CHECK_COLOR);

                // Trazo en "✔": empieza 1/4, baja a 1/2 y sube a 3/4 del ancho
                int cx = x + pad;
                int cy = y + pad;
                int w  = size - pad*2;
                int h  = size - pad*2;

                int x1 = cx + Math.round(w * 0.22f);
                int y1 = cy + Math.round(h * 0.55f);
                int x2 = cx + Math.round(w * 0.42f);
                int y2 = cy + Math.round(h * 0.73f);
                int x3 = cx + Math.round(w * 0.78f);
                int y3 = cy + Math.round(h * 0.27f);

                g2.drawLine(x1, y1, x2, y2);
                g2.drawLine(x2, y2, x3, y3);
            }

            g2.dispose();
        }
    }

}
