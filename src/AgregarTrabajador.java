import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.sql.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public class AgregarTrabajador {
    private JPanel panelDatos;
    private JTextField txtNombre;
    private JTextField txtDireccion; // <- si tu formulario tiene “Correo” pero el bound field se llama txtDireccion, lo usamos como fallback
    private JTextField txtTelefono;
    private JComboBox cmbActivo;
    private JPanel panelInfo;
    private JPanel panelBotones;
    private JButton btnAgregar;
    private JButton btnSalir;
    private JPanel panelMain;
    private JComboBox cmbSucursal;
    private JComboBox cmbPuesto;
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
    // **AGREGA** este campo en el .form si puedes (lo ideal es que el bound field real se llame txtCorreo)
    private JTextField txtCorreo; // si no existe en tu .form, no pasa nada: usamos txtDireccion como correo

    private final int usuarioId;
    private final int sucursalIdActual;
    private Runnable onSaved;

    public AgregarTrabajador(int usuarioId, int sucursalIdActual) {
        this.usuarioId = usuarioId;
        this.sucursalIdActual = sucursalIdActual;

        initCombos();

        if (btnAgregar != null) btnAgregar.addActionListener(e -> guardar());
        if (btnSalir   != null) btnSalir.addActionListener(e -> cerrarDialogo());
        decorateAsCard(panelMain);
        decorateAsCard(panelBotones);
        decorateAsCard(panelInfo);
        decorateAsCard(panelDatos);
        styleTextField(txtNombre);
        styleTextField(txtDireccion);
        styleTextField(txtTelefono);
        styleExitButton(btnSalir);
        stylePrimaryButton(btnAgregar);
        if (cmbActivo != null) {
            cmbActivo.setFont(fText);
            cmbActivo.setForeground(TEXT_PRIMARY);
            cmbActivo.setBackground(Color.WHITE);
            cmbActivo.setBorder(new AgregarSucursal.CompoundBorderRounded(BORDER_SOFT, 12, 1, new Insets(6,8,6,8)));
        }
        if (cmbPuesto != null) {
            cmbPuesto.setFont(fText);
            cmbPuesto.setForeground(TEXT_PRIMARY);
            cmbPuesto.setBackground(Color.WHITE);
            cmbPuesto.setBorder(new AgregarSucursal.CompoundBorderRounded(BORDER_SOFT, 12, 1, new Insets(6,8,6,8)));
        }
        if (cmbSucursal != null) {
            cmbSucursal.setFont(fText);
            cmbSucursal.setForeground(TEXT_PRIMARY);
            cmbSucursal.setBackground(Color.WHITE);
            cmbSucursal.setBorder(new AgregarSucursal.CompoundBorderRounded(BORDER_SOFT, 12, 1, new Insets(6,8,6,8)));
        }

    }

    public void setOnSaved(Runnable onSaved) { this.onSaved = onSaved; }

    public static JDialog createDialog(Window owner, int usuarioId, int sucursalIdActual, Runnable onSaved) {
        AgregarTrabajador ui = new AgregarTrabajador(usuarioId, sucursalIdActual);
        ui.setOnSaved(onSaved);
        JDialog d = new JDialog(owner, "Agregar trabajador", Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(ui.panelMain);
        d.setMinimumSize(new Dimension(720, 460));
        d.pack();
        d.setLocationRelativeTo(owner);
        return d;
    }

    private void initCombos() {
        if (cmbActivo != null && cmbActivo.getItemCount() == 0) {
            cmbActivo.setModel(new DefaultComboBoxModel<>(new String[]{"Sí", "No"}));
            cmbActivo.setSelectedIndex(1); // por si quieres default “No”; cámbialo a 0 si quieres “Sí”
        }
        if (cmbPuesto != null && cmbPuesto.getItemCount() == 0) {
            for (String p : Arrays.asList("Administrador", "Cajero/Asesor", "Asesor", "Asesor/Contador", "Cajero/Contador/Asesor"))
                cmbPuesto.addItem(p);
            cmbPuesto.setSelectedIndex(0);
        }
        cargarSucursalesCombo();
    }

    private void cargarSucursalesCombo() {
        if (cmbSucursal == null) return;
        cmbSucursal.removeAllItems();
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id, nombre FROM sucursales WHERE activo=1 ORDER BY nombre");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                cmbSucursal.addItem(new ComboItem(rs.getInt(1), rs.getString(2)));
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(panelMain, "Error al cargar sucursales: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void guardar() {
        String nombre   = tx(txtNombre);
        // correo: toma txtCorreo; si no existe en tu .form, toma txtDireccion como “correo” (fallback)
        String correo   = !tx(txtCorreo).isBlank() ? tx(txtCorreo) : tx(txtDireccion);
        String telefono = tx(txtTelefono);
        ComboItem suc   = (ComboItem) (cmbSucursal != null ? cmbSucursal.getSelectedItem() : null);
        int sucursalId  = (suc != null ? suc.id : -1);
        String puesto   = (cmbPuesto != null && cmbPuesto.getSelectedItem()!=null) ? cmbPuesto.getSelectedItem().toString().trim() : "";
        int activo      = parseActivo(cmbActivo);

        if (nombre.isBlank() || correo.isBlank() || telefono.isBlank() || sucursalId <= 0 || puesto.isBlank()) {
            JOptionPane.showMessageDialog(panelMain, "Completa nombre, correo, teléfono, sucursal y puesto.");
            return;
        }

        // (opcional) validación simple de correo
        if (!correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            JOptionPane.showMessageDialog(panelMain, "Correo inválido.");
            return;
        }

        final String sql = """
            INSERT INTO trabajadores
            (nombre, correo, telefono, sucursal_id, puesto, fecha_contratacion, activo)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        LocalDate hoy = LocalDate.now();

        try (Connection con = DB.get()) {
            if (usuarioId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_user_id=?")) { p.setInt(1, usuarioId); p.executeUpdate(); }
            if (sucursalIdActual > 0) try (PreparedStatement p = con.prepareStatement("SET @app_sucursal_id=?")) { p.setInt(1, sucursalIdActual); p.executeUpdate(); }

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, nombre);
                ps.setString(2, correo);                 // <--- ya no va NULL
                ps.setString(3, telefono);
                ps.setInt(4, sucursalId);
                ps.setString(5, puesto);
                ps.setDate(6, Date.valueOf(hoy));
                ps.setInt(7, activo);

                int n = ps.executeUpdate();
                if (n > 0) {
                    JOptionPane.showMessageDialog(panelMain, "Trabajador agregado correctamente.");
                    if (onSaved != null) onSaved.run();
                    cerrarDialogo();
                } else {
                    JOptionPane.showMessageDialog(panelMain, "No se pudo agregar el trabajador.");
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(panelMain, "Error BD: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void cerrarDialogo() {
        Window w = SwingUtilities.getWindowAncestor(panelMain);
        if (w instanceof JDialog d) d.dispose();
    }

    private static String tx(JTextField t){ return (t!=null && t.getText()!=null) ? t.getText().trim() : ""; }
    private static int parseActivo(JComboBox combo){
        if (combo == null || combo.getSelectedItem()==null) return 1;
        String s = combo.getSelectedItem().toString().trim().toLowerCase();
        return (s.startsWith("s") || s.equals("1") || s.equals("true")) ? 1 : 0;
    }

    static class ComboItem {
        final int id; final String nombre;
        ComboItem(int id, String nombre){ this.id=id; this.nombre=nombre; }
        @Override public String toString(){ return nombre + " (id " + id + ")"; }
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
        if (tf == null) return;   // <-- blindaje crítico
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
            super(new AgregarSucursal.RoundedLineBorder(line, arc, thickness), new EmptyBorder(innerPad));
        }
    }
}
