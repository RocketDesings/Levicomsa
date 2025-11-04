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
    private JPanel panelContenedor;

    // ----- contexto -----
    private final int usuarioId;
    private final int sucursalId;
    private final Runnable onSaved;

    // ====== PALETA / TIPOGRAFÍA (solo UI) ======
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
        // Fondos base
        if (panelMain != null) panelMain.setOpaque(false);
        if (panelContenedor != null) panelContenedor.setOpaque(false);
        if (panelInfo != null) panelInfo.setOpaque(false);
        if (panelDatos != null) panelDatos.setOpaque(false);
        if (panelBotones != null) panelBotones.setOpaque(false);

        // Tarjetas
        decorateAsCard(panelContenedor);
        decorateAsCard(panelInfo);
        decorateAsCard(panelDatos);
        decorateAsCard(panelBotones);

        // Tipos
        if (txtNombre != null) {
            txtNombre.setFont(fText);
            styleTextField(txtNombre);
            setPlaceholder(txtNombre, "Nombre de la categoría");
        }
        if (cmbActivo != null) {
            cmbActivo.setModel(new DefaultComboBoxModel<>(new String[]{"Sí", "No"}));
            cmbActivo.setSelectedIndex(0);
            cmbActivo.setFont(fText);
        }

        // Botones (usa tus UIs estándar)
        if (btnAgregar != null) stylePrimaryButton(btnAgregar);
        if (btnSalir != null)   styleExitButton(btnSalir);
    }

    /** Envuelve el contenido con padding para que pack() quede compacto. */
    private JComponent wrapCompact(JComponent content) {
        JPanel root = new JPanel(new BorderLayout());

        // Tarjeta blanca con sombra (consistente con tus cards)
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(true);
        card.setBackground(CARD_BG);
        card.setBorder(new PantallaAdmin.CompoundRoundShadowBorder(14, BORDER_SOFT, new Color(0,0,0,28)));

        card.add(content, BorderLayout.CENTER);
        root.add(card, BorderLayout.CENTER);

        // Tamaño sugerido si el .form no lo tiene
        if (content.getPreferredSize() == null ||
                content.getPreferredSize().width == 0 ||
                content.getPreferredSize().height == 0) {
        }
        return root;
    }

    // ====== estilos de campo y botones ======
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

    private void setPlaceholder(JTextField tf, String placeholder) {
        Color muted = TEXT_MUTED;
        tf.setForeground(muted);
        tf.setText(placeholder);
        tf.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (tf.getText().equals(placeholder)) {
                    tf.setText(""); tf.setForeground(TEXT_PRIMARY);
                }
            }
            @Override public void focusLost(FocusEvent e) {
                if (tf.getText().isEmpty()) {
                    tf.setForeground(muted); tf.setText(placeholder);
                }
            }
        });
    }

    private void stylePrimaryButton(JButton b) {
        // Igual que pantallaCajero: usa ModernButtonUI de PantallaAdmin
        b.setUI(new PantallaAdmin.ModernButtonUI(GREEN_DARK, GREEN_SOFT, GREEN_DARK, Color.WHITE, 15, true));
        b.setBorder(new EmptyBorder(10,18,10,28));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setForeground(Color.WHITE);
    }

    private void styleExitButton(JButton b) {
        // Botón rojo consistente con tu estilo
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

    // ====== helpers de borde ======
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
