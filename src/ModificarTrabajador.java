import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.sql.*;
import java.util.Arrays;
import java.util.List;

public class ModificarTrabajador {
    private JPanel panelInfo;
    private JPanel panelBotones;
    private JButton btnModificar;
    private JButton btnSalir;
    private JPanel panelMain;
    private JComboBox<ComboItem> cmbSucursal;
    private JComboBox<String> cmbPuesto;
    private JTextField txtCorreo;
    private JTextField txtNombre;
    private JTextField txtTelefono;
    private JComboBox<String> cmbActivo;

    // ---- contexto ----
    private final int trabajadorId;     // ID a editar (trabajadores.id)
    private final int usuarioId;        // para @app_user_id
    private final int sucursalIdActual; // para @app_sucursal_id
    private Runnable onSaved;           // callback para refrescar tablas al guardar

    // ===== PALETA / TIPOS (solo presentación) =====
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

    public ModificarTrabajador(int trabajadorId, int usuarioId, int sucursalIdActual) {
        this.trabajadorId = trabajadorId;
        this.usuarioId = usuarioId;
        this.sucursalIdActual = sucursalIdActual;

        // ======= ESTILO (no modifica lógica) =======
        applyTheme();

        initCombos();
        cablearEventos();
        cargarTrabajador(); // rellena campos
    }

    public void setOnSaved(Runnable onSaved) { this.onSaved = onSaved; }

    /** Abre el formulario como diálogo modal. */
    public static JDialog createDialog(Window owner, int trabajadorId, int usuarioId, int sucursalIdActual, Runnable onSaved) {
        ModificarTrabajador ui = new ModificarTrabajador(trabajadorId, usuarioId, sucursalIdActual);
        ui.setOnSaved(onSaved);

        JDialog d = new JDialog(owner, "Modificar trabajador", Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(ui.panelMain);
        d.setMinimumSize(new Dimension(720, 460));
        d.pack();
        d.setLocationRelativeTo(owner);
        return d;
    }

    // ====================== UI / eventos ======================
    private void initCombos() {
        // Activo
        if (cmbActivo != null && cmbActivo.getItemCount() == 0) {
            cmbActivo.setModel(new DefaultComboBoxModel<>(new String[]{"Sí", "No"}));
        }
        // Puestos (ajusta si necesitas otros)
        if (cmbPuesto != null && cmbPuesto.getItemCount() == 0) {
            List<String> puestos = Arrays.asList("Administrador", "Cajero/Asesor", "Asesor", "Asesor/Contador", "Cajero/Contador/Asesor");
            for (String p : puestos) cmbPuesto.addItem(p);
        }
        // Sucursales activas
        cargarSucursalesCombo(-1); // seleccionaremos la correcta al cargar datos
    }

    private void cablearEventos() {
        if (btnModificar != null) btnModificar.addActionListener(e -> guardarCambios());
        if (btnSalir != null) btnSalir.addActionListener(e -> cerrar());
    }

    // ====================== Carga ======================
    private void cargarTrabajador() {
        final String sql = "SELECT nombre, correo, telefono, sucursal_id, puesto, activo FROM trabajadores WHERE id = ?";
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, trabajadorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    JOptionPane.showMessageDialog(panelMain, "No se encontró el trabajador (id=" + trabajadorId + ").");
                    cerrar();
                    return;
                }
                if (txtNombre   != null) txtNombre.setText(nvl(rs.getString("nombre")));
                if (txtCorreo   != null) txtCorreo.setText(nvl(rs.getString("correo")));
                if (txtTelefono != null) txtTelefono.setText(nvl(rs.getString("telefono")));
                int sucId = rs.getInt("sucursal_id");
                cargarSucursalesCombo(sucId); // repobla y selecciona
                if (cmbPuesto != null) cmbPuesto.setSelectedItem(nvl(rs.getString("puesto")));
                if (cmbActivo != null) cmbActivo.setSelectedIndex(rs.getInt("activo") == 1 ? 0 : 1);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(panelMain, "Error al cargar trabajador: " + ex.getMessage());
            ex.printStackTrace();
            cerrar();
        }
    }

    private void cargarSucursalesCombo(int selectedId) {
        if (cmbSucursal == null) return;
        cmbSucursal.removeAllItems();
        ComboItem selectLater = null;
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement("SELECT id, nombre FROM sucursales WHERE activo=1 ORDER BY nombre");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ComboItem it = new ComboItem(rs.getInt(1), rs.getString(2));
                cmbSucursal.addItem(it);
                if (it.id == selectedId) selectLater = it;
            }
            if (selectLater != null) cmbSucursal.setSelectedItem(selectLater);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(panelMain, "Error al cargar sucursales: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ====================== Guardar ======================
    private void guardarCambios() {
        String nombre   = tx(txtNombre);
        String correo   = tx(txtCorreo);
        String telefono = tx(txtTelefono);
        ComboItem suc   = (ComboItem) (cmbSucursal != null ? cmbSucursal.getSelectedItem() : null);
        int sucursalId  = (suc != null ? suc.id : -1);
        String puesto   = (cmbPuesto != null && cmbPuesto.getSelectedItem()!=null) ? cmbPuesto.getSelectedItem().toString().trim() : "";
        int activo      = parseActivo(cmbActivo);

        if (nombre.isBlank() || correo.isBlank() || telefono.isBlank() || sucursalId <= 0 || puesto.isBlank()) {
            JOptionPane.showMessageDialog(panelMain, "Completa nombre, correo, teléfono, sucursal y puesto.");
            return;
        }
        if (!correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            JOptionPane.showMessageDialog(panelMain, "Correo inválido.");
            return;
        }

        final String sql = """
            UPDATE trabajadores
            SET nombre=?, correo=?, telefono=?, sucursal_id=?, puesto=?, activo=?, actualizado_en=NOW()\s
            WHERE id=?
            """;

        try (Connection con = DB.get()) {
            // variables de sesión para triggers de bitácora
            if (usuarioId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_user_id=?")) { p.setInt(1, usuarioId); p.executeUpdate(); }
            if (sucursalIdActual > 0) try (PreparedStatement p = con.prepareStatement("SET @app_sucursal_id=?")) { p.setInt(1, sucursalIdActual); p.executeUpdate(); }

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, nombre);
                ps.setString(2, correo);
                ps.setString(3, telefono);
                ps.setInt(4, sucursalId);
                ps.setString(5, puesto);
                ps.setInt(6, activo);
                ps.setInt(7, trabajadorId);

                int n = ps.executeUpdate();
                if (n > 0) {
                    JOptionPane.showMessageDialog(panelMain, "Trabajador modificado correctamente.");
                    if (onSaved != null) onSaved.run();
                    cerrar();
                } else {
                    JOptionPane.showMessageDialog(panelMain, "No hubo cambios.");
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(panelMain, "Error BD: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // ====================== util ======================
    private void cerrar() {
        Window w = SwingUtilities.getWindowAncestor(panelMain);
        if (w instanceof JDialog d) d.dispose();
    }
    private static String tx(JTextField t){ return (t!=null && t.getText()!=null) ? t.getText().trim() : ""; }
    private static String nvl(String s){ return s != null ? s : ""; }
    private static int parseActivo(JComboBox<String> c){
        if (c == null || c.getSelectedItem()==null) return 1;
        String s = c.getSelectedItem().toString().trim().toLowerCase();
        return (s.startsWith("s") || s.equals("1") || s.equals("true")) ? 1 : 0;
    }

    /** Item para el combo de sucursales. */
    static class ComboItem {
        final int id; final String nombre;
        ComboItem(int id, String nombre){ this.id=id; this.nombre=nombre; }
        @Override public String toString(){ return nombre + " (id " + id + ")"; }
    }

    // ==================== SOLO ESTILO (no cambia funcionalidades) ====================
    private void applyTheme() {
        // contenedor
        if (panelMain != null) panelMain.setBorder(new EmptyBorder(12,12,12,12));

        // fuentes
        setFontIfNotNull(txtNombre, fText);
        setFontIfNotNull(txtCorreo, fText);
        setFontIfNotNull(txtTelefono, fText);
        if (cmbPuesto != null)   cmbPuesto.setFont(fText);
        if (cmbSucursal != null) cmbSucursal.setFont(fText);
        if (cmbActivo != null)   cmbActivo.setFont(fText);

        // inputs
        styleTextField(txtNombre);
        styleTextField(txtCorreo);
        styleTextField(txtTelefono);

        // combos
        styleCombo(cmbPuesto);
        styleCombo(cmbSucursal);
        styleCombo(cmbActivo);

        // botones
        stylePrimaryButton(btnModificar);
        styleExitButton(btnSalir);

        // tarjetas
        decorateAsCard(panelMain);
        decorateAsCard(panelInfo);
        decorateAsCard(panelBotones);
    }

    private void setFontIfNotNull(JComponent c, Font f) { if (c != null) c.setFont(f); }

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

    private void styleCombo(JComboBox<?> cb) {
        if (cb == null) return;
        cb.setBackground(Color.WHITE);
        cb.setForeground(TEXT_PRIMARY);
        cb.setBorder(new CompoundBorderRounded(BORDER_SOFT, 12, 1, new Insets(6, 10, 6, 10)));
        cb.setOpaque(true);
        cb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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

    private void decorateAsCard(JComponent c) {
        if (c == null) return;
        c.setOpaque(true);
        c.setBackground(CARD_BG);
        c.setBorder(new PantallaAdmin.CompoundRoundShadowBorder(14, BORDER_SOFT, new Color(0,0,0,28)));
    }

    // ====== helpers de borde internos para inputs ======
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
