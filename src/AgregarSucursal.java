import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AgregarSucursal {
    private JPanel panelMain;
    private JPanel panelInfo;
    private JPanel panelDatos;
    private JPanel panelBotones;
    private JButton btnAgregar;
    private JButton btnSalir;
    private JTextField txtNombre;
    private JTextField txtDireccion;
    private JTextField txtTelefono;
    private JComboBox<String> cmbActivo;

    // --- contexto ---
    private final int usuarioId;
    private final int sucursalIdActual; // sucursal desde donde opera el usuario
    private Runnable onSaved;           // callback opcional para refrescar tablas

    // ======= PALETA/TIPOGRAFÍA (solo presentación) =======
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

    public AgregarSucursal(int usuarioId, int sucursalIdActual) {
        this.usuarioId = usuarioId;
        this.sucursalIdActual = sucursalIdActual;

        // Modelo combo
        if (cmbActivo != null && cmbActivo.getItemCount() == 0) {
            cmbActivo.setModel(new DefaultComboBoxModel<>(new String[]{"Sí", "No"}));
            cmbActivo.setSelectedIndex(0);
        }

        // Listeners
        if (btnAgregar != null) btnAgregar.addActionListener(e -> guardar());
        if (btnSalir   != null) btnSalir.addActionListener(e -> cerrarDialogo());

        // ==== SOLO DISEÑO ====
        applyTheme();
    }

    public void setOnSaved(Runnable onSaved) { this.onSaved = onSaved; }

    /** Crea un JDialog con este formulario (útil desde botones de CRUDSucursales). */
    public static JDialog createDialog(Window owner, int usuarioId, int sucursalIdActual, Runnable onSaved) {
        AgregarSucursal ui = new AgregarSucursal(usuarioId, sucursalIdActual);
        ui.setOnSaved(onSaved);

        JDialog d = new JDialog(owner, "Agregar sucursal", Dialog.ModalityType.APPLICATION_MODAL);
        d.setContentPane(ui.panelMain);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setMinimumSize(new Dimension(620, 360));
        d.pack();
        d.setLocationRelativeTo(owner);
        return d;
    }

    // ================== lógica ==================
    private void guardar() {
        String nombre    = safe(txtNombre);
        String direccion = safe(txtDireccion);
        String telefono  = safe(txtTelefono);
        int activo       = parseActivo(cmbActivo);

        if (nombre.isBlank() || direccion.isBlank() || telefono.isBlank()) {
            JOptionPane.showMessageDialog(panelMain, "Completa nombre, dirección y teléfono.", "Faltan datos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final String sql = "INSERT INTO sucursales (nombre, direccion, telefono, activo) VALUES (?,?,?,?)";

        try (Connection con = DB.get()) {

            // Variables de sesión para los triggers de bitácora:
            if (usuarioId > 0) {
                try (PreparedStatement ps = con.prepareStatement("SET @app_user_id = ?")) {
                    ps.setInt(1, usuarioId);
                    ps.executeUpdate();
                }
            }
            if (sucursalIdActual > 0) {
                try (PreparedStatement ps = con.prepareStatement("SET @app_sucursal_id = ?")) {
                    ps.setInt(1, sucursalIdActual);
                    ps.executeUpdate();
                }
            }

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, nombre);
                ps.setString(2, direccion);
                ps.setString(3, telefono);
                ps.setInt(4, activo); // 1/0
                int n = ps.executeUpdate();
                if (n > 0) {
                    JOptionPane.showMessageDialog(panelMain, "Sucursal agregada correctamente.");
                    if (onSaved != null) onSaved.run(); // refresca la tabla si te pasaron callback
                    cerrarDialogo();
                } else {
                    JOptionPane.showMessageDialog(panelMain, "No se pudo agregar la sucursal.", "Sin cambios", JOptionPane.WARNING_MESSAGE);
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(panelMain, "Error al guardar: " + ex.getMessage(), "BD", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void cerrarDialogo() {
        Window w = SwingUtilities.getWindowAncestor(panelMain);
        if (w instanceof JDialog d) d.dispose();
    }

    private static String safe(JTextField tf) { return tf != null && tf.getText() != null ? tf.getText().trim() : ""; }

    private static int parseActivo(JComboBox<String> combo) {
        if (combo == null || combo.getSelectedItem() == null) return 1;
        String s = combo.getSelectedItem().toString().trim().toLowerCase();
        return (s.startsWith("s") || s.equals("1") || s.equals("true")) ? 1 : 0;
    }

    // ===================== SOLO DISEÑO =====================
    private void applyTheme() {
        // padding general
        if (panelMain != null) panelMain.setBorder(new EmptyBorder(12,12,12,12));

        // tarjetas
        decorateAsCard(panelMain);
        decorateAsCard(panelInfo);
        decorateAsCard(panelDatos);
        decorateAsCard(panelBotones);

        // tipografías de campos
        if (txtNombre != null)    { txtNombre.setFont(fText);    styleTextField(txtNombre); }
        if (txtDireccion != null) { txtDireccion.setFont(fText); styleTextField(txtDireccion); }
        if (txtTelefono != null)  { txtTelefono.setFont(fText);  styleTextField(txtTelefono); }

        // combo
        if (cmbActivo != null) {
            cmbActivo.setFont(fText);
            cmbActivo.setForeground(TEXT_PRIMARY);
            cmbActivo.setBackground(Color.WHITE);
            cmbActivo.setBorder(new CompoundBorderRounded(BORDER_SOFT, 12, 1, new Insets(6,8,6,8)));
        }

        // botones
        if (btnAgregar != null) stylePrimaryButton(btnAgregar);
        if (btnSalir   != null) styleExitButton(btnSalir);
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

    // ====== Bordes redondeados (solo UI) ======
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