import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
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

    // ======= constructores =======
    public ModificarServicio(int usuarioId, int sucursalId, Runnable onSaved) {
        this.usuarioId = usuarioId;
        this.sucursalId = sucursalId;
        this.onSaved = onSaved;

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
    /** Abre el diálogo centrado, con tamaño justo (pack) y sin “lienzo” extra. */
    public static JDialog open(Window owner, int usuarioId, int sucursalId, Runnable onSaved) {
        ModificarServicio ui = new ModificarServicio(usuarioId, sucursalId, onSaved);

        JDialog d = new JDialog(owner, "Modificar servicio", Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(ui.buildCompactContent());
        d.pack();                    // tamaño justo según preferred sizes
        d.setResizable(false);       // que no se agrande accidentalmente
        d.setLocationRelativeTo(owner != null && owner.isShowing() ? owner : null); // centro

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

        // === FORMATO DE BOTONES (lo que pediste) ===
        if (btnModificar != null) stylePrimaryButton(btnModificar); // verde
        if (btnSalir != null)     styleDangerButton(btnSalir);      // rojo
    }

    // ===================== estilos de botón =====================
    private void stylePrimaryButton(JButton b) {
        // verde: #22C55E / hover #16A34A / press #15803D
        b.setUI(new ModernButtonUI(new Color(0x22C55E), new Color(0x16A34A), new Color(0x15803D),
                Color.WHITE, 12, true));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    private void styleDangerButton(JButton b) {
        // rojo: #EF4444 / hover #DC2626 / press #B91C1C
        b.setUI(new ModernButtonUI(new Color(0xEF4444), new Color(0xDC2626), new Color(0xB91C1C),
                Color.WHITE, 12, true));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // ===================== combos/carga =====================
    private void cargarCombos() {
        cargarCategorias();
        recargarServiciosDeCategoria(); // carga inicial según 1ra categoría
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
        if (m.getSize() > 0) cmbCategoria.setSelectedIndex(0);
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
        if (m.getSize() > 0) {
            cmbServicio.setSelectedIndex(0);
            cargarDatosServicioSeleccionado();
        } else {
            limpiarCampos();
        }
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
                    seleccionarCategoriaPorId(catId);
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
                cmbCategoria.setSelectedIndex(i);
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

    // ===== UI de botón moderno =====
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
}