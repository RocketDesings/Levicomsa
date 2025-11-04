import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.math.BigDecimal;
import java.sql.*;
import java.util.Objects;

public class AgregarServicio {
    private JPanel panelDatos;
    private JTextField txtNombre;
    private JTextField txtDescripcion;
    private JComboBox<String> cmbActivo;
    private JComboBox<CategoriaItem> cmbCategoria;
    private JTextField txtPrecio;
    private JPanel panelBotones;
    private JButton btnAgregar;
    private JButton btnSalir;
    private JPanel panelInfo;
    private JPanel panelMain;

    // contexto (para bitácoras/triggers)
    private final int usuarioId;
    private final int sucursalId;

    // callback para refrescar la tabla del CRUD
    private final Runnable onSaved;

    // ===== PALETA / TIPOGRAFÍA (solo UI) =====
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

    // ---------- constructores ----------
    public AgregarServicio() {
        this(-1, -1, null);
    }
    public AgregarServicio(int usuarioId, int sucursalId, Runnable onSaved) {
        this.usuarioId = usuarioId;
        this.sucursalId = sucursalId;
        this.onSaved    = onSaved;

        configurarUI();
        cargarCategorias();
        cargarActivo();

        // acciones
        if (btnAgregar != null) btnAgregar.addActionListener(e -> guardar());
        if (btnSalir   != null) btnSalir.addActionListener(e -> {
            Window w = SwingUtilities.getWindowAncestor(panelMain);
            if (w instanceof JDialog d) d.dispose();
        });
    }

    /** Abre como diálogo listo para usar desde CRUDServicios. */
    public static JDialog createDialog(Window owner, int usuarioId, int sucursalId, Runnable onSaved) {
        AgregarServicio ui = new AgregarServicio(usuarioId, sucursalId, onSaved);
        JDialog d = new JDialog(owner, "Agregar servicio", Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(ui.wrapCompact(ui.panelMain)); // wrapper visual compacto (solo estilo)
        d.setMinimumSize(new Dimension(720, 420));
        d.pack();
        d.setLocationRelativeTo(owner);
        return d;
    }

    // ===================== UI =====================
    private void configurarUI() {
        // Fondos y tarjetas
        if (panelMain != null) panelMain.setOpaque(false);
        if (panelInfo != null) panelInfo.setOpaque(false);
        if (panelDatos != null) panelDatos.setOpaque(false);
        if (panelBotones != null) panelBotones.setOpaque(false);

        decorateAsCard(panelMain);
        decorateAsCard(panelInfo);
        decorateAsCard(panelDatos);
        decorateAsCard(panelBotones);

        if (panelMain != null) panelMain.setBorder(new EmptyBorder(12,12,12,12));

        // Campos
        if (txtNombre != null) {
            txtNombre.setFont(fText);
            styleTextField(txtNombre);
        }
        if (txtDescripcion != null) {
            txtDescripcion.setFont(fText);
            styleTextField(txtDescripcion);
        }
        if (txtPrecio != null) {
            txtPrecio.setFont(fText);
            txtPrecio.setToolTipText("Ejemplo: 0, 10.50, 99");
            styleTextField(txtPrecio);
        }

        // Combos
        if (cmbCategoria != null) {
            cmbCategoria.setFont(fText);
            cmbCategoria.setForeground(TEXT_PRIMARY);
            cmbCategoria.setBackground(Color.WHITE);
            cmbCategoria.setBorder(new CompoundBorderRounded(BORDER_SOFT, 12, 1, new Insets(6,8,6,8)));
        }
        if (cmbActivo != null) {
            cmbActivo.setFont(fText);
            cmbActivo.setForeground(TEXT_PRIMARY);
            cmbActivo.setBackground(Color.WHITE);
            cmbActivo.setBorder(new CompoundBorderRounded(BORDER_SOFT, 12, 1, new Insets(6,8,6,8)));
        }

        // Botones
        if (btnAgregar != null) stylePrimaryButton(btnAgregar);
        if (btnSalir   != null) styleExitButton(btnSalir);
    }

    /** Wrapper con padding y card blanca (solo presentación). */
    private JComponent wrapCompact(JComponent content) {
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(true);
        card.setBackground(CARD_BG);
        card.setBorder(new PantallaAdmin.CompoundRoundShadowBorder(14, BORDER_SOFT, new Color(0,0,0,28)));

        card.add(content, BorderLayout.CENTER);
        root.add(card, BorderLayout.CENTER);
        return root;
    }

    // ======= Estilos de controles (no funcionales) =======
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

    private void stylePrimaryButton(JButton b) {
        b.setUI(new PantallaAdmin.ModernButtonUI(GREEN_DARK, GREEN_SOFT, GREEN_DARK, Color.WHITE, 15, true));
        b.setBorder(new EmptyBorder(10,18,10,28));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setForeground(Color.WHITE);
    }

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

    // ===================== Guardar =====================
    private void guardar() {
        String nombre = safe(txtNombre);
        String descr  = safe(txtDescripcion);
        String precioTx = safe(txtPrecio);
        CategoriaItem cat = (CategoriaItem) (cmbCategoria != null ? cmbCategoria.getSelectedItem() : null);
        int activo = (Objects.equals(cmbActivo != null ? cmbActivo.getSelectedItem() : "Sí", "Sí")) ? 1 : 0;

        if (nombre.isBlank()) {
            JOptionPane.showMessageDialog(panelMain, "El nombre es obligatorio.");
            if (txtNombre != null) txtNombre.requestFocus();
            return;
        }
        if (cat == null) {
            JOptionPane.showMessageDialog(panelMain, "Selecciona una categoría.");
            if (cmbCategoria != null) cmbCategoria.requestFocus();
            return;
        }

        BigDecimal precio = parsePrecio(precioTx);
        if (precio == null) {
            JOptionPane.showMessageDialog(panelMain, "Precio no válido. Usa números, ej. 0, 10.50");
            if (txtPrecio != null) txtPrecio.requestFocus();
            return;
        }
        if (precio.signum() < 0) {
            JOptionPane.showMessageDialog(panelMain, "El precio no puede ser negativo.");
            if (txtPrecio != null) txtPrecio.requestFocus();
            return;
        }

        final String sql = """
        INSERT INTO servicios (categoria_id, nombre, descripcion, precio, activo)
        VALUES (?,?,?,?,?)
        """;
        try (Connection con = DB.get()) {
            if (usuarioId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_user_id=?")) { p.setInt(1, usuarioId); p.executeUpdate(); }
            if (sucursalId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_sucursal_id=?")) { p.setInt(1, sucursalId); p.executeUpdate(); }

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, cat.id);
                ps.setString(2, nombre);
                ps.setString(3, descr.isBlank() ? null : descr);
                ps.setBigDecimal(4, precio);
                ps.setInt(5, activo);
                ps.executeUpdate();
            }

            JOptionPane.showMessageDialog(panelMain, "Servicio agregado correctamente.");
            if (onSaved != null) onSaved.run();
            Window w = SwingUtilities.getWindowAncestor(panelMain);
            if (w instanceof JDialog d) d.dispose();

        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Duplicate")) {
                JOptionPane.showMessageDialog(panelMain, "Ya existe un servicio con ese nombre.");
            } else {
                JOptionPane.showMessageDialog(panelMain, "Error BD: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    // ===================== helpers =====================
    private static BigDecimal parsePrecio(String s) {
        if (s == null) return BigDecimal.ZERO;
        s = s.trim();
        if (s.isEmpty()) return BigDecimal.ZERO;
        s = s.replace(",", ".");
        try { return new BigDecimal(s); }
        catch (Exception e) { return null; }
    }

    private static String safe(JTextField tf) { return tf != null ? tf.getText().trim() : ""; }
    private static String nvl(String s) { return s != null ? s : ""; }

    // item para el combo de categorías
    static class CategoriaItem {
        final int id; final String nombre;
        CategoriaItem(int id, String nombre) { this.id = id; this.nombre = nombre; }
        @Override public String toString() { return nombre; }
    }

    // ====== Bordes redondeados para inputs (solo UI) ======
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
    private void cargarCategorias() {
        if (cmbCategoria == null) return;
        DefaultComboBoxModel<CategoriaItem> m = new DefaultComboBoxModel<>();
        final String sql = "SELECT id, nombre FROM categorias_servicio WHERE activo=1 ORDER BY nombre";
        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                m.addElement(new CategoriaItem(rs.getInt("id"), nvl(rs.getString("nombre"))));
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(panelMain, "Error al cargar categorías: " + e.getMessage());
        } cmbCategoria.setModel(m); if (m.getSize() > 0) cmbCategoria.setSelectedIndex(0);
    }
    private void cargarActivo() {
        if (cmbActivo == null) return;
        DefaultComboBoxModel<String> m = new DefaultComboBoxModel<>();
        m.addElement("Sí");
        m.addElement("No");
        cmbActivo.setModel(m);
        cmbActivo.setSelectedIndex(0);
    }
}
