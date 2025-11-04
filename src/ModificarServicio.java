import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.math.BigDecimal;
import java.sql.*;
import java.util.Objects;

public class ModificarServicio {
    // ----- componentes del .form -----
    private JPanel panelDatos;
    private JTextField txtDescripcion;
    private JComboBox<String> cmbActivo;
    private JTextField txtPrecio;
    private JComboBox<CategoriaItem> cmbCategoria;
    private JPanel panelBotones;
    private JButton btnModificar;
    private JButton btnSalir;
    private JPanel panelInfo;
    private JPanel panelMain;
    private JComboBox<ServicioItem> cmbServicio;
    private JTextField txtNombreServicio;

    // ----- contexto -----
    private final int usuarioId;
    private final int sucursalId;
    private final Runnable onSaved; // callback para refrescar lista en CRUDServicios

    // ----- preselección (nuevo) -----
    private final Integer initServicioId;    // servicio a seleccionar al abrir (puede ser null)
    private final Integer initCategoriaId;   // categoría a seleccionar al abrir (puede ser null)
    private boolean initialSelectionDone = false; // evita reintentos en cambios posteriores

    // ====== PALETA / TIPOGRAFÍA (solo presentación) ======
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

    // ======= constructores =======
    public ModificarServicio(int usuarioId, int sucursalId, Runnable onSaved) {
        this(usuarioId, sucursalId, onSaved, null, null);
    }
    public ModificarServicio(int usuarioId, int sucursalId, Runnable onSaved,
                             Integer initServicioId, Integer initCategoriaId) {
        this.usuarioId = usuarioId;
        this.sucursalId = sucursalId;
        this.onSaved = onSaved;
        this.initServicioId = initServicioId;
        this.initCategoriaId = initCategoriaId;

        configurarUI();
        cargarCombos();

        // eventos
        if (cmbCategoria != null) {
            cmbCategoria.addActionListener(e -> recargarServiciosDeCategoria());
        }
        if (cmbServicio != null) {
            cmbServicio.addActionListener(e -> cargarDatosServicioSeleccionado());
        }
        if (btnModificar != null) btnModificar.addActionListener(e -> guardarCambios());
        if (btnSalir != null) btnSalir.addActionListener(e -> {
            Window w = SwingUtilities.getWindowAncestor(panelMain);
            if (w instanceof JDialog d) d.dispose();
        });
    }

    // ======= apertura centrada/compacta =======
    /** Mantengo el open original para compatibilidad. */
    public static JDialog open(Window owner, int usuarioId, int sucursalId, Runnable onSaved) {
        return open(owner, usuarioId, sucursalId, onSaved, null, null);
    }

    /** Nuevo: open con preselección de servicio y categoría. */
    public static JDialog open(Window owner, int usuarioId, int sucursalId, Runnable onSaved,
                               Integer servicioId, Integer categoriaId) {
        ModificarServicio ui = new ModificarServicio(usuarioId, sucursalId, onSaved, servicioId, categoriaId);

        JDialog d = new JDialog(owner, "Modificar servicio", Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(ui.buildCompactContent());
        d.pack();
        d.setResizable(false);
        d.setLocationRelativeTo(owner != null && owner.isShowing() ? owner : null);

        if (ui.btnModificar != null) d.getRootPane().setDefaultButton(ui.btnModificar);

        d.setVisible(true);
        return d;
    }

    /** Envuelve panelMain con un padding pequeño y asegura preferred size. */
    private JComponent buildCompactContent() {
        if (panelMain.getParent() != null) {
            Container p = panelMain.getParent();
            p.remove(panelMain);
        }
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(new EmptyBorder(12, 12, 12, 12));
        wrap.add(panelMain, BorderLayout.CENTER);

        Dimension pref = panelMain.getPreferredSize();
        if (pref == null || pref.width == 0 || pref.height == 0) {
            panelMain.setPreferredSize(new Dimension(680, 420));
        }
        panelMain.setMaximumSize(new Dimension(760, 500));
        return wrap;
    }

    // ===================== UI =====================
    private void configurarUI() {
        if (panelMain != null) panelMain.setBorder(new EmptyBorder(8,8,8,8));

        // Combo “Activo”
        if (cmbActivo != null) {
            DefaultComboBoxModel<String> m = new DefaultComboBoxModel<>();
            m.addElement("Sí");
            m.addElement("No");
            cmbActivo.setModel(m);
            cmbActivo.setSelectedIndex(0);
        }

        // ===== Estilo (solo UI) =====
        applyTheme();

        // Botones con el estilo pedido
        if (btnModificar != null) stylePrimaryButton(btnModificar); // verde
        if (btnSalir != null)     styleExitButton(btnSalir);        // rojo
    }

    // ===================== combos/carga =====================
    private void cargarCombos() {
        cargarCategorias();

        // Si nos pasaron categoría inicial, selecciónala aquí
        if (initCategoriaId != null) {
            seleccionarCategoriaPorId(initCategoriaId);
        }

        // Carga servicios de la categoría seleccionada
        recargarServiciosDeCategoria();
    }

    private void cargarCategorias() {
        DefaultComboBoxModel<CategoriaItem> m = new DefaultComboBoxModel<>();
        final String sql = "SELECT id, nombre FROM categorias_servicio WHERE activo=1 ORDER BY nombre";
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                m.addElement(new CategoriaItem(rs.getInt("id"), nvl(rs.getString("nombre"))));
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(panelMain, "Error al cargar categorías: " + e.getMessage());
        }
        cmbCategoria.setModel(m);
        if (m.getSize() > 0 && initCategoriaId == null) {
            cmbCategoria.setSelectedIndex(0);
        }
    }

    private void recargarServiciosDeCategoria() {
        CategoriaItem cat = (CategoriaItem) cmbCategoria.getSelectedItem();
        DefaultComboBoxModel<ServicioItem> m = new DefaultComboBoxModel<>();
        if (cat != null) {
            final String sql = "SELECT id, nombre FROM servicios WHERE categoria_id=? ORDER BY nombre";
            try (Connection con = DB.get();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, cat.id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        m.addElement(new ServicioItem(rs.getInt("id"), nvl(rs.getString("nombre"))));
                    }
                }
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(panelMain, "Error al cargar servicios: " + e.getMessage());
            }
        }
        cmbServicio.setModel(m);

        // Selección inicial del servicio si nos lo pasaron (solo 1 vez)
        if (!initialSelectionDone && initServicioId != null) {
            int idx = indexOfServicioId(m, initServicioId);
            if (idx >= 0) cmbServicio.setSelectedIndex(idx);
            initialSelectionDone = true;
        } else if (m.getSize() > 0 && cmbServicio.getSelectedIndex() < 0) {
            cmbServicio.setSelectedIndex(0);
        }

        cargarDatosServicioSeleccionado();
    }

    private int indexOfServicioId(DefaultComboBoxModel<ServicioItem> m, int id) {
        for (int i = 0; i < m.getSize(); i++) {
            if (m.getElementAt(i).id == id) return i;
        }
        return -1;
    }

    private void cargarDatosServicioSeleccionado() {
        ServicioItem it = (ServicioItem) cmbServicio.getSelectedItem();
        if (it == null) { limpiarCampos(); return; }

        final String sql = "SELECT categoria_id, nombre, descripcion, precio, activo FROM servicios WHERE id=?";
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, it.id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int catId = rs.getInt("categoria_id");
                    seleccionarCategoriaPorId(catId); // asegura que la categoría corresponde

                    if (txtNombreServicio != null) txtNombreServicio.setText(nvl(rs.getString("nombre")));
                    if (txtDescripcion != null)   txtDescripcion.setText(nvl(rs.getString("descripcion")));
                    if (txtPrecio != null)        txtPrecio.setText(rs.getBigDecimal("precio") != null ? rs.getBigDecimal("precio").toPlainString() : "0");
                    if (cmbActivo != null)        cmbActivo.setSelectedItem(rs.getInt("activo") == 1 ? "Sí" : "No");
                } else {
                    limpiarCampos();
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(panelMain, "Error al cargar servicio: " + e.getMessage());
        }
    }

    private void seleccionarCategoriaPorId(int catId) {
        ComboBoxModel<CategoriaItem> m = cmbCategoria.getModel();
        for (int i = 0; i < m.getSize(); i++) {
            if (m.getElementAt(i).id == catId) {
                if (cmbCategoria.getSelectedIndex() != i) {
                    cmbCategoria.setSelectedIndex(i);
                }
                return;
            }
        }
    }

    private void limpiarCampos() {
        if (txtNombreServicio != null) txtNombreServicio.setText("");
        if (txtDescripcion != null)    txtDescripcion.setText("");
        if (txtPrecio != null)         txtPrecio.setText("");
        if (cmbActivo != null)         cmbActivo.setSelectedIndex(0);
    }

    // ===================== Guardar =====================
    private void guardarCambios() {
        ServicioItem serv = (ServicioItem) cmbServicio.getSelectedItem();
        CategoriaItem cat  = (CategoriaItem) cmbCategoria.getSelectedItem();
        if (serv == null) { JOptionPane.showMessageDialog(panelMain, "Selecciona un servicio."); return; }
        if (cat  == null) { JOptionPane.showMessageDialog(panelMain, "Selecciona una categoría."); return; }

        String nombre = safe(txtNombreServicio);
        String descr  = safe(txtDescripcion);
        String precioTx = safe(txtPrecio);
        if (nombre.isBlank()) {
            JOptionPane.showMessageDialog(panelMain, "El nombre es obligatorio.");
            if (txtNombreServicio != null) txtNombreServicio.requestFocus(); return;
        }

        BigDecimal precio = parsePrecio(precioTx);
        if (precio == null) { JOptionPane.showMessageDialog(panelMain, "Precio no válido."); if (txtPrecio!=null) txtPrecio.requestFocus(); return; }
        if (precio.signum() < 0) { JOptionPane.showMessageDialog(panelMain, "El precio no puede ser negativo."); if (txtPrecio!=null) txtPrecio.requestFocus(); return; }

        int activo = (Objects.equals(cmbActivo.getSelectedItem(), "Sí")) ? 1 : 0;

        final String sql = """
            UPDATE servicios
               SET categoria_id=?, nombre=?, descripcion=?, precio=?, activo=?
             WHERE id=?
        """;
        try (Connection con = DB.get()) {
            // variables de sesión para triggers/bitácoras
            if (usuarioId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_user_id=?")) { p.setInt(1, usuarioId); p.executeUpdate(); }
            if (sucursalId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_sucursal_id=?")) { p.setInt(1, sucursalId); p.executeUpdate(); }

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, cat.id);
                ps.setString(2, nombre);
                ps.setString(3, descr.isBlank() ? null : descr);
                ps.setBigDecimal(4, precio);
                ps.setInt(5, activo);
                ps.setInt(6, serv.id);

                int n = ps.executeUpdate();
                if (n > 0) {
                    JOptionPane.showMessageDialog(panelMain, "Servicio actualizado.");
                    if (onSaved != null) onSaved.run();
                    Window w = SwingUtilities.getWindowAncestor(panelMain);
                    if (w instanceof JDialog d) d.dispose();
                } else {
                    JOptionPane.showMessageDialog(panelMain, "No se aplicaron cambios.");
                }
            }
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("duplicate")) {
                JOptionPane.showMessageDialog(panelMain, "Ya existe un servicio con ese nombre.");
            } else {
                JOptionPane.showMessageDialog(panelMain, "Error BD: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    // ===================== helpers =====================
    private static String nvl(String s) { return s != null ? s : ""; }
    private static String safe(JTextField tf) { return tf != null ? tf.getText().trim() : ""; }
    private static BigDecimal parsePrecio(String s) {
        if (s == null) return BigDecimal.ZERO;
        s = s.trim();
        if (s.isEmpty()) return BigDecimal.ZERO;
        s = s.replace(",", ".");
        try { return new BigDecimal(s); } catch (Exception e) { return null; }
    }

    // ==== items para combos ====
    static class CategoriaItem {
        final int id; final String nombre;
        CategoriaItem(int id, String nombre) { this.id = id; this.nombre = nombre; }
        @Override public String toString() { return nombre; }
    }
    static class ServicioItem {
        final int id; final String nombre;
        ServicioItem(int id, String nombre) { this.id = id; this.nombre = nombre; }
        @Override public String toString() { return nombre; }
    }

    // ===== UI de botón moderno (queda por compatibilidad si lo usabas en otro lado) =====
    static class ModernButtonUI extends BasicButtonUI {
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
            b.setBorder(new EmptyBorder(10, 18, 10, 18));
            b.setRolloverEnabled(true);
            b.setFocusPainted(false);
            b.setForeground(fg);
        }

        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel m = b.getModel();

            Color fill = m.isPressed() ? press : (m.isRollover() ? hover : bg);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (filled) {
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), arc, arc);
            } else {
                g2.setColor(fill);
                g2.drawRoundRect(0, 0, c.getWidth()-1, c.getHeight()-1, arc, arc);
            }

            g2.dispose();
            super.paint(g, c); // pinta texto/icono
        }
    }

    // ============================
    // ===== SOLO ESTILO UI =======
    // ============================
    private void applyTheme() {
        // Tarjetas
        decorateAsCard(panelMain);
        decorateAsCard(panelDatos);
        decorateAsCard(panelInfo);
        decorateAsCard(panelBotones);

        // Tipografías
        if (txtNombreServicio != null) txtNombreServicio.setFont(fText);
        if (txtDescripcion != null)    txtDescripcion.setFont(fText);
        if (txtPrecio != null)         txtPrecio.setFont(fText);
        if (cmbCategoria != null)      cmbCategoria.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        if (cmbServicio != null)       cmbServicio.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        if (cmbActivo != null)         cmbActivo.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        // Campos con borde y focus
        if (txtNombreServicio != null) styleTextField(txtNombreServicio);
        if (txtDescripcion != null)    styleTextField(txtDescripcion);
        if (txtPrecio != null)         styleTextField(txtPrecio);
    }

    private void stylePrimaryButton(JButton b) {
        if (b == null) return;
        // Igual que pantallaCajero: usa ModernButtonUI de PantallaAdmin
        b.setUI(new PantallaAdmin.ModernButtonUI(GREEN_DARK, GREEN_SOFT, GREEN_DARK, Color.WHITE, 15, true));
        b.setBorder(new EmptyBorder(10,18,10,28));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setForeground(Color.WHITE);
    }

    // Botón rojo consistente con tu estilo
    private void styleExitButton(JButton b) {
        if (b == null) return;
        Color ROJO_BASE    = new Color(0xDC2626);
        Color GRIS_HOVER   = new Color(0xD1D5DB);
        Color GRIS_PRESSED = new Color(0x9CA3AF);
        b.setUI(new Login.ModernButtonUI(ROJO_BASE, GRIS_HOVER, GRIS_PRESSED, Color.BLACK, 22, true));
        b.setBorder(new EmptyBorder(10,18,10,28));
        b.setForeground(Color.WHITE);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
    }

    // Mantengo este alias porque en tu versión anterior llamabas a styleDangerButton
    private void styleDangerButton(JButton b) {
        styleExitButton(b);
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
        tf.setFont(fText);
    }

    // ===== BORDES REDONDEADOS (helper visual) =====
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
