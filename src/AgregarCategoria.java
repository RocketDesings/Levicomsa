import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AgregarCategoria {
    // ----- componentes del .form -----
    private JPanel panelDatos;
    private JTextField txtNombre;
    private JComboBox<String> cmbActivo;
    private JPanel panelBotones;
    private JButton btnAgregar;
    private JButton btnSalir;
    private JPanel panelInfo;
    private JPanel panelMain;

    // ----- contexto -----
    private final int usuarioId;
    private final int sucursalId;
    private final Runnable onSaved;

    // ====== constructores ======
    public AgregarCategoria(int usuarioId, int sucursalId, Runnable onSaved) {
        this.usuarioId = usuarioId;
        this.sucursalId = sucursalId;
        this.onSaved = onSaved;

        configurarUI();

        if (btnAgregar != null) btnAgregar.addActionListener(e -> guardar());
        if (btnSalir != null) btnSalir.addActionListener(e -> close());
    }

    // ====== fábrica de diálogo ======
    public static JDialog createDialog(Window owner, int usuarioId, int sucursalId, Runnable onSaved) {
        AgregarCategoria ui = new AgregarCategoria(usuarioId, sucursalId, onSaved);

        JDialog d = new JDialog(owner, "Agregar categoría", Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(ui.wrapCompact(ui.panelMain));
        d.pack();
        d.setResizable(false);
        d.setLocationRelativeTo(owner != null && owner.isShowing() ? owner : null);

        if (ui.btnAgregar != null) d.getRootPane().setDefaultButton(ui.btnAgregar);
        return d;
    }

    // ====== UI / estilo ======
    private void configurarUI() {
        if (panelMain != null) panelMain.setOpaque(false);
        if (panelInfo != null) panelInfo.setOpaque(false);
        if (panelDatos != null) panelDatos.setOpaque(false);
        if (panelBotones != null) panelBotones.setOpaque(false);

        // Combo Activo
        if (cmbActivo != null) {
            cmbActivo.setModel(new DefaultComboBoxModel<>(new String[]{"Sí", "No"}));
            cmbActivo.setSelectedIndex(0);
        }

        // Campos con borde redondeado y placeholder
        if (txtNombre != null) {
            styleTextField(txtNombre);
            setPlaceholder(txtNombre, "Nombre de la categoría");
        }

        // Botones
        if (btnAgregar != null) styleSolidButton(btnAgregar, new Color(0x20A93D)); // verde
        if (btnSalir != null)   styleSolidButton(btnSalir,   new Color(0xEF4040)); // rojo
    }

    /** Envuelve el contenido con padding para que pack() quede compacto. */
    private JComponent wrapCompact(JComponent content) {
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Tarjeta blanca con sombra suave
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(true);
        card.setBackground(Color.WHITE);
        card.setBorder(new EmptyBorder(18, 18, 18, 18));

        card.add(content, BorderLayout.CENTER);
        root.add(card, BorderLayout.CENTER);

        // Tamaño sugerido si el .form no lo tiene
        if (content.getPreferredSize() == null ||
                content.getPreferredSize().width == 0 ||
                content.getPreferredSize().height == 0) {
            content.setPreferredSize(new Dimension(520, 240));
        }
        return root;
    }

    private void styleTextField(JTextField tf) {
        tf.setOpaque(true);
        tf.setBackground(Color.WHITE);
        tf.setForeground(Color.BLACK);
        tf.setCaretColor(Color.BLACK);
        tf.setBorder(new CompoundBorderRounded(new Color(0xCBD5E1), 10, 1, new Insets(10, 12, 10, 12)));
        tf.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                tf.setBorder(new CompoundBorderRounded(new Color(0x20A93D), 10, 2, new Insets(10, 12, 10, 12)));
            }
            @Override public void focusLost(FocusEvent e) {
                tf.setBorder(new CompoundBorderRounded(new Color(0xCBD5E1), 10, 1, new Insets(10, 12, 10, 12)));
            }
        });
    }

    private void setPlaceholder(JTextField tf, String placeholder) {
        Color muted = new Color(0x6B7280);
        tf.setForeground(muted);
        tf.setText(placeholder);
        tf.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (tf.getText().equals(placeholder)) {
                    tf.setText(""); tf.setForeground(Color.BLACK);
                }
            }
            @Override public void focusLost(FocusEvent e) {
                if (tf.getText().isEmpty()) {
                    tf.setForeground(muted); tf.setText(placeholder);
                }
            }
        });
    }

    private void styleSolidButton(JButton b, Color bg) {
        Color hover   = bg.darker();
        Color pressed = new Color(
                Math.max(bg.getRed()-35,0),
                Math.max(bg.getGreen()-35,0),
                Math.max(bg.getBlue()-35,0)
        );
        b.setUI(new ModernButtonUI(bg, hover, pressed, Color.WHITE, 12, true));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(b.getFont().deriveFont(Font.BOLD, 14f));
    }

    // ====== acciones ======
    private void guardar() {
        String nombre = (txtNombre != null ? txtNombre.getText().trim() : "");
        if (nombre.isEmpty() || "Nombre de la categoría".equalsIgnoreCase(nombre)) {
            JOptionPane.showMessageDialog(panelMain, "Escribe el nombre de la categoría.");
            if (txtNombre != null) txtNombre.requestFocus();
            return;
        }
        int activo = (cmbActivo != null && "Sí".equals(cmbActivo.getSelectedItem())) ? 1 : 0;

        final String sql = "INSERT INTO categorias_servicio (nombre, activo) VALUES (?, ?)";

        try (Connection con = DB.get()) {
            // Variables de sesión para triggers (si los tienes)
            if (usuarioId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_user_id=?")) { p.setInt(1, usuarioId); p.executeUpdate(); }
            if (sucursalId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_sucursal_id=?")) { p.setInt(1, sucursalId); p.executeUpdate(); }

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, nombre);
                ps.setInt(2, activo);
                int n = ps.executeUpdate();
                if (n > 0) {
                    JOptionPane.showMessageDialog(panelMain, "Categoría agregada.");
                    if (onSaved != null) onSaved.run();
                    close();
                } else {
                    JOptionPane.showMessageDialog(panelMain, "No se pudo agregar la categoría.");
                }
            }
        } catch (SQLException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.toLowerCase().contains("duplicate")) {
                JOptionPane.showMessageDialog(panelMain, "Ya existe una categoría con ese nombre.");
            } else {
                JOptionPane.showMessageDialog(panelMain, "Error BD: " + msg);
            }
            e.printStackTrace();
        }
    }

    private void close() {
        Window w = SwingUtilities.getWindowAncestor(panelMain);
        if (w instanceof JDialog d) d.dispose();
    }

    // ====== helpers de borde/botón ======
    static class ModernButtonUI extends javax.swing.plaf.basic.BasicButtonUI {
        private final Color bg, hover, press, fg;
        private final int arc;
        private final boolean filled;

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
            }
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
}
