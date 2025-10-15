import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PantallaAdmin implements Refrescable {

    // ====== componentes (de tu .form) ======
    private JFrame pantalla;
    private JPanel panelMain;
    private JTable table1;
    private JButton btnAgregarCliente;
    private JButton btnModificarCliente;
    private JButton btnCobrar;
    private JButton btnEliminar;
    private JButton btnSalir;
    private JLabel lblHora;
    private JLabel lblImagen;
    private JLabel lblTitulo;
    private JLabel lblSlogan;

    private JPanel panelInfo;
    private JPanel panelExtra;
    private JPanel panelBotones;
    private JLabel lblIcono;
    private JLabel lblNombre;
    private JLabel lblSucursal;
    private JPanel panelBusqueda;
    private JTextField tfBuscar;
    private JLabel lblPlaceholder;
    private JPanel panelAdmin;
    private JButton btnAdministracion;
    private JButton btnBitacoras;
    private JButton buscarButton;

    // ====== comportamiento ======
    private AutoActualizarTabla autoActualizador;
    private TableRowSorter<DefaultTableModel> sorter;

    // sesión
    private final int usuarioId;   // Usuarios.id
    private String nombreTrabajador;
    private String nombreSucursal;
    private int sucursalId = -1;   // <— NUEVO: id de la sucursal asociada al usuario

    // Referencia a la ventana de Herramientas (para no abrir duplicadas)
    private JDialog dlgHerramientas;  // <— NUEVO

    // paginación
    private static final int PAGE_SIZE = 300;
    private int currentOffset = 0;

    // ====== Paleta (igual que en Asesor) ======
    private static final Color GREEN_DARK   = new Color(0x0A6B2A);
    private static final Color GREEN_BASE   = new Color(0x16A34A);
    private static final Color GREEN_SOFT   = new Color(0x22C55E);
    private static final Color BG_CANVAS    = new Color(0xF3F4F6);
    private static final Color CARD_BG      = Color.WHITE;
    private static final Color TEXT_PRIMARY = new Color(0x111827);
    private static final Color TEXT_MUTED   = new Color(0x6B7280);
    private static final Color BORDER_SOFT  = new Color(0xE5E7EB);
    private static final Color BORDER_FOCUS = new Color(0x059669);
    private static final Color TABLE_ALT    = new Color(0xF9FAFB);
    private static final Color TABLE_SEL_BG = new Color(0xE6F7EE);
    private static final Color TABLE_SEL_TX = TEXT_PRIMARY;

    // ---------- constructores ----------
    public PantallaAdmin() { this(-1); }
    public PantallaAdmin(int usuarioId) {
        this.usuarioId = usuarioId;
        lblSlogan.setText("<html>Comprometidos con tu tranquilidad,<br>ofreciéndote soluciones a la medida de tus necesidades.</html>");
        // Frame
        pantalla = new JFrame("Panel de Administración");
        pantalla.setUndecorated(false);
        pantalla.setContentPane(panelMain);
        pantalla.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Pantalla completa (maximizada, no exclusiva)
        pantalla.setExtendedState(JFrame.MAXIMIZED_BOTH);
        pantalla.setResizable(true);
        pantalla.addWindowStateListener(e -> {
            int ns = e.getNewState();
            if ((ns & JFrame.ICONIFIED) == 0 && (ns & JFrame.MAXIMIZED_BOTH) == 0) {
                pantalla.setExtendedState(JFrame.MAXIMIZED_BOTH);
            }
        });
        pantalla.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e)  { stopAuto(); }
            @Override public void windowClosed (WindowEvent e)  { stopAuto(); }
        });

        // Tema / estilos
        applyTheme();
        setLogo();

        pantalla.pack();
        pantalla.setLocationRelativeTo(null);
        pantalla.setVisible(true);

        // Acciones
        if (btnSalir != null) btnSalir.addActionListener(e -> mostrarAlertaCerrarSesion());
        if (btnAgregarCliente != null) btnAgregarCliente.addActionListener(e -> abrirFormularioAgregarCliente());
        if (btnModificarCliente != null) btnModificarCliente.addActionListener(e -> abrirSeleccionModificar());
        if (btnEliminar != null) btnEliminar.addActionListener(e -> eliminarCliente());

        // Deshabilita Cobrar hasta que tengamos sucursalId válido
        if (btnCobrar != null) btnCobrar.setEnabled(false);

        // Abrir Herramientas admin sin duplicados
        if (btnAdministracion != null)
            btnAdministracion.addActionListener(e -> mostrarHerramientasAdmin());

        // Reloj + tabla + búsqueda
        iniciarReloj();
        configurarTabla();
        cablearBusquedaInline();

        // Cargar datos de usuario (esto setea sucursalId) y clientes
        cargarDatosAdmin();
        cargarClientesDesdeBD();

        // Ahora que ya cargamos, cablea Cobrar con validación
        if (btnCobrar != null) {
            btnCobrar.addActionListener(e -> {
                if (sucursalId <= 0) {
                    JOptionPane.showMessageDialog(pantalla, "No se detectó sucursal del usuario.");
                    return;
                }
                EnviarCobro.mostrar(sucursalId, usuarioId);
            });
        }

        autoActualizador = new AutoActualizarTabla(this::cargarClientesDesdeBD, 5000);
        autoActualizador.iniciar();

        if (tfBuscar != null) SwingUtilities.invokeLater(() -> tfBuscar.requestFocusInWindow());
    }

    // ====================== THEME / ESTILO ======================
    private void applyTheme() {
        panelMain.setBackground(new Color(222, 222, 222));

        // Tipografía
        if (lblTitulo   != null) { lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 24)); lblTitulo.setForeground(TEXT_PRIMARY); }
        if (lblSlogan   != null) lblSlogan.setForeground(TEXT_MUTED);
        if (lblNombre   != null) lblNombre.setFont(new Font("Segoe UI", Font.BOLD, 18));
        if (lblSucursal != null) lblSucursal.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        // Botones
        if (btnAgregarCliente != null) stylePrimaryButton(btnAgregarCliente);
        if (btnModificarCliente != null) styleOutlineButton(btnModificarCliente);
        if (btnCobrar != null) styleOutlineButton(btnCobrar);

        // Peligro: Eliminar y Salir en rojo con hover gris, texto negro
        if (btnEliminar != null) styleDangerButton(btnEliminar);
        if (btnSalir != null) styleDangerButton(btnSalir);

        // Search
        if (tfBuscar != null) styleSearchField(tfBuscar);
        if (buscarButton != null) {
            buscarButton.setUI(new ModernButtonUI(GREEN_BASE, GREEN_SOFT, GREEN_DARK, Color.WHITE, 12, true));
            buscarButton.setBorder(new EmptyBorder(10,16,10,16));
            buscarButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        // Bordes como en tu diseño original
        if (panelInfo  != null) panelInfo.setBorder(new MatteBorder(2,2,2,2, new Color(0xD1D5DB)));
        if (panelExtra != null) panelExtra.setBorder(new MatteBorder(2,2,2,2, new Color(0xD1D5DB)));
    }

    private void decorateAsCard(JComponent c) {
        c.setOpaque(true);
        c.setBackground(CARD_BG);
        c.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1,1,1,1, BORDER_SOFT),
                new EmptyBorder(12,12,12,12)
        ));
    }

    private void setLogo() {
        ImageIcon icon = new ImageIcon("resources/images/levicomsa.png");
        lblIcono.setIcon(new ImageIcon(
                icon.getImage().getScaledInstance(128, 128, Image.SCALE_SMOOTH)
        ));
    }

    // ====================== UI básica ======================
    private void iniciarReloj() {
        if (lblHora == null) return;
        SimpleDateFormat formato = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        Timer timer = new Timer(1000, e -> lblHora.setText(formato.format(new Date())));
        timer.start();
    }

    private void configurarTabla() {
        String[] columnas = {"Nombre", "Teléfono", "CURP", "Pensionado", "RFC", "NSS", "Correo"};
        DefaultTableModel modelo = new DefaultTableModel(columnas, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table1.setModel(modelo);
        sorter = new TableRowSorter<>(modelo);
        table1.setRowSorter(sorter);
        table1.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table1.setRowHeight(28);
        table1.setShowGrid(false);
        table1.setIntercellSpacing(new Dimension(0, 0));

        // Header verde
        JTableHeader header = table1.getTableHeader();
        header.setDefaultRenderer(new HeaderRenderer(header.getDefaultRenderer(), GREEN_DARK, Color.WHITE));
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 36));

        // Zebra + selección
        table1.setDefaultRenderer(Object.class, new ZebraRenderer());

        int[] widths = {220, 140, 160, 110, 160, 140, 260}; // NSS ~140
        for (int i = 0; i < Math.min(widths.length, table1.getColumnCount()); i++) {
            table1.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private void cablearBusquedaInline() {
        if (tfBuscar == null) return;

        tfBuscar.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e){ aplicarFiltro(tfBuscar.getText().trim()); }
            public void removeUpdate(DocumentEvent e){ aplicarFiltro(tfBuscar.getText().trim()); }
            public void changedUpdate(DocumentEvent e){ aplicarFiltro(tfBuscar.getText().trim()); }
        });

        tfBuscar.addActionListener(e -> aplicarFiltro(tfBuscar.getText().trim()));
        tfBuscar.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ESCAPE"), "clear");
        tfBuscar.getActionMap().put("clear", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                tfBuscar.setText(""); aplicarFiltro("");
            }
        });

        if (buscarButton != null)
            buscarButton.addActionListener(e -> aplicarFiltro(tfBuscar.getText().trim()));
    }

    private void setPlaceholder(JTextField tf, String texto) {
        tf.setForeground(TEXT_MUTED);
        tf.setText(texto);
        tf.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                if (tf.getText().equals(texto)) { tf.setText(""); tf.setForeground(TEXT_PRIMARY); }
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                if (tf.getText().isBlank()) { tf.setForeground(TEXT_MUTED); tf.setText(texto); }
            }
        });
    }

    private void aplicarFiltro(String q) {
        if (sorter == null) return;
        if (q == null || q.isBlank()) { sorter.setRowFilter(null); return; }
        String regex = "(?i)" + java.util.regex.Pattern.quote(q);
        List<RowFilter<Object,Object>> cols = new ArrayList<>();
        for (int c = 0; c <= 6; c++) cols.add(RowFilter.regexFilter(regex, c));
        sorter.setRowFilter(RowFilter.orFilter(cols));
    }

    private void stopAuto() { try { if (autoActualizador != null) autoActualizador.detener(); } catch (Exception ignored) {} }

    // ====================== Datos del admin ======================
    private void cargarDatosAdmin() {
        if (lblNombre == null && lblSucursal == null) return;
        if (usuarioId <= 0) {
            if (lblNombre != null)   lblNombre.setText("Administrador");
            if (lblSucursal != null) lblSucursal.setText("");
            sucursalId = -1;
            if (btnCobrar != null) btnCobrar.setEnabled(false);
            return;
        }
        final String sql = """
            SELECT COALESCE(u.nombre, t.nombre) AS nombreTrabajador,
                   s.nombre AS sucursal,
                   s.id     AS sucursal_id
            FROM Usuarios u
            LEFT JOIN trabajadores t ON t.id = u.trabajador_id
            LEFT JOIN sucursales   s ON s.id = t.sucursal_id
            WHERE u.id = ?
            """;
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    nombreTrabajador = rs.getString("nombreTrabajador");
                    nombreSucursal   = rs.getString("sucursal");
                    sucursalId       = rs.getInt("sucursal_id");
                    if (rs.wasNull()) sucursalId = -1;
                } else {
                    sucursalId = -1;
                }
            }
        } catch (SQLException ignored) {
            sucursalId = -1;
        }

        if (lblNombre != null)   lblNombre.setText(nombreTrabajador != null ? nombreTrabajador : "Administrador");
        if (lblSucursal != null) lblSucursal.setText(nombreSucursal != null ? nombreSucursal : "");
        if (btnCobrar != null)   btnCobrar.setEnabled(sucursalId > 0);  // habilita sólo si hay sucursal válida
    }

    // ====================== Datos de clientes ======================
    public void cargarClientesDesdeBD() {
        final String sql = "SELECT nombre, telefono, CURP, pensionado, RFC, NSS, correo " +
                "FROM Clientes ORDER BY nombre LIMIT ? OFFSET ?";

        try (Connection conn = DB.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (usuarioId > 0) {
                try (PreparedStatement set = conn.prepareStatement("SET @app_user_id = ?")) {
                    set.setInt(1, usuarioId);
                    set.executeUpdate();
                }
            }

            ps.setInt(1, PAGE_SIZE);
            ps.setInt(2, currentOffset);

            try (ResultSet rs = ps.executeQuery()) {
                DefaultTableModel modelo = (DefaultTableModel) table1.getModel();
                table1.getTableHeader().setReorderingAllowed(false);
                modelo.setRowCount(0);

                while (rs.next()) {
                    String nombre     = rs.getString(1);
                    String telefono   = rs.getString(2);
                    String curp       = rs.getString(3);
                    String pensionado = rs.getBoolean(4) ? "Sí" : "No";
                    String rfc        = rs.getString(5);
                    String nss        = rs.getString(6);   // NSS
                    String correo     = rs.getString(7);
                    modelo.addRow(new Object[]{nombre, telefono, curp, pensionado, rfc, nss, correo});
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(pantalla, "Error al cargar clientes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void mostrar() {
        pantalla.setVisible(true);
        cargarClientesDesdeBD();
    }

    @Override
    public void refrescarDatos() {
        cargarClientesDesdeBD();
        pantalla.setVisible(true);
    }

    // ====================== Abrir formularios ======================
    private void abrirFormularioAgregarCliente() {
        try {
            Class<?> cls = Class.forName("FormularioAgregarCliente");
            try {
                var ctor = cls.getDeclaredConstructor(Refrescable.class, int.class);
                ctor.setAccessible(true);
                ctor.newInstance(this, usuarioId);
            } catch (NoSuchMethodException noPair) {
                var ctor2 = cls.getDeclaredConstructor(Refrescable.class);
                ctor2.setAccessible(true);
                ctor2.newInstance(this);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(pantalla, "No se pudo abrir el formulario: " + ex.getMessage());
        }
    }

    private void abrirSeleccionModificar() {
        try {
            Class<?> cls = Class.forName("SeleccionarCliente2");
            try {
                var ctor = cls.getDeclaredConstructor(Refrescable.class, int.class);
                ctor.setAccessible(true);
                ctor.newInstance(this, usuarioId);
            } catch (NoSuchMethodException noPair) {
                var ctor2 = cls.getDeclaredConstructor();
                ctor2.setAccessible(true);
                ctor2.newInstance();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(pantalla, "No se pudo abrir la selección: " + ex.getMessage());
        }
    }

    // ====================== Eliminar cliente (UI) ======================
    private void eliminarCliente() {
        int fila = table1.getSelectedRow();
        if (fila == -1) {
            JOptionPane.showMessageDialog(pantalla, "Selecciona un cliente para eliminar.");
            return;
        }
        fila = table1.convertRowIndexToModel(fila);

        String nombre = table1.getModel().getValueAt(fila, 0).toString();
        String curp   = table1.getModel().getValueAt(fila, 2).toString(); // si tu PK es id, úsalo mejor

        int ok = JOptionPane.showConfirmDialog(
                pantalla,
                "¿Seguro que deseas eliminar a:\n" + nombre + " (CURP " + curp + ")?",
                "Confirmar eliminación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;

        final String sql = "DELETE FROM Clientes WHERE CURP = ?";
        try (Connection conn = DB.get()) {
            // registra usuario para triggers (bitácora)
            if (usuarioId > 0) {
                try (PreparedStatement set = conn.prepareStatement("SET @app_user_id = ?")) {
                    set.setInt(1, usuarioId);
                    set.executeUpdate();
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, curp);
                int n = ps.executeUpdate();
                if (n > 0) {
                    JOptionPane.showMessageDialog(pantalla, "Cliente eliminado correctamente.");
                    cargarClientesDesdeBD();
                } else {
                    JOptionPane.showMessageDialog(pantalla, "No se encontró el cliente.");
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(pantalla, "Error al eliminar cliente: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void mostrarAlertaCerrarSesion() {
        new AlertaCerrarSesion(pantalla);
    }

    // ====================== Herramientas Admin (sin duplicados) ======================
    private void mostrarHerramientasAdmin() {
        if (dlgHerramientas != null && dlgHerramientas.isDisplayable()) {
            dlgHerramientas.toFront();
            dlgHerramientas.requestFocus();
            return;
        }
        // Crea nueva instancia (requiere que HerramientasAdmin extienda JDialog y tenga ctor Window)
        dlgHerramientas = new HerramientasAdmin(pantalla, usuarioId, sucursalId);
        dlgHerramientas.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed (WindowEvent e) { dlgHerramientas = null; }
            @Override public void windowClosing(WindowEvent e) { dlgHerramientas = null; }
        });
        dlgHerramientas.setVisible(true);
    }

    // ========= estilos reutilizables =========
    private void stylePrimaryButton(JButton b) {
        b.setUI(new ModernButtonUI(GREEN_BASE, GREEN_SOFT, GREEN_DARK, Color.WHITE, 12, true));
        b.setBorder(new EmptyBorder(10,18,10,18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    private void styleOutlineButton(JButton b) {
        b.setUI(new ModernButtonUI(new Color(0,0,0,0), new Color(0,0,0,25), new Color(0,0,0,45), TEXT_PRIMARY, 12, false));
        b.setBorder(new EmptyBorder(10,18,10,18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    private void styleDangerButton(JButton b) {
        Color ROJO_BASE = new Color(0xDC2626);
        Color GRIS_HOV  = new Color(0xD1D5DB);
        Color GRIS_PRE  = new Color(0x9CA3AF);
        b.setUI(new ModernButtonUI(ROJO_BASE, GRIS_HOV, GRIS_PRE, Color.BLACK, 12, true));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    private void styleSearchField(JTextField tf) {
        tf.setBorder(new RoundBorder(BORDER_SOFT, 10, 1, new Insets(8, 12, 8, 12)));
        tf.setBackground(Color.WHITE);
        tf.setForeground(TEXT_PRIMARY);
        tf.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                tf.setBorder(new RoundBorder(BORDER_FOCUS, 12, 2, new Insets(8, 12, 8, 12)));
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                tf.setBorder(new RoundBorder(BORDER_SOFT, 10, 1, new Insets(8, 12, 8, 12)));
            }
        });
    }

    private static class HeaderRenderer extends DefaultTableCellRenderer {
        private final TableCellRenderer base;
        private final Color bg, fg;
        HeaderRenderer(TableCellRenderer base, Color bg, Color fg) { this.base=base; this.bg=bg; this.fg=fg; }
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            Component comp = base.getTableCellRendererComponent(t, v, sel, foc, r, c);
            comp.setBackground(bg);
            comp.setForeground(fg);
            comp.setFont(comp.getFont().deriveFont(Font.BOLD));
            if (comp instanceof JComponent jc) jc.setBorder(new MatteBorder(0,0,1,0, GREEN_BASE.darker()));
            return comp;
        }
    }
    private class ZebraRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            if (sel) {
                comp.setBackground(TABLE_SEL_BG);
                comp.setForeground(TABLE_SEL_TX);
            } else {
                comp.setBackground((r % 2 == 0) ? Color.WHITE : TABLE_ALT);
                comp.setForeground(TEXT_PRIMARY);
            }
            setBorder(new EmptyBorder(6,8,6,8));
            return comp;
        }
    }

    private static class RoundBorder extends MatteBorder {
        private final int arc;
        private final Insets pad;
        private final Color color;
        public RoundBorder(Color color, int arc, int thickness, Insets padding) {
            super(thickness, thickness, thickness, thickness, color);
            this.arc = arc;
            this.pad = padding;
            this.color = color;
        }
        @Override public Insets getBorderInsets(Component c) {
            return new Insets(pad.top + top, pad.left + left, pad.bottom + bottom, pad.right + right);
        }
        @Override public boolean isBorderOpaque() { return false; }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = width  - 1;
            int h = height - 1;
            g2.setColor(color);
            g2.drawRoundRect(x, y, w, h, arc, arc);
            g2.dispose();
        }
    }

    private static class ModernButtonUI extends javax.swing.plaf.basic.BasicButtonUI {
        private final Color base, hover, pressed, text;
        private final int radius;
        private final boolean filled;
        public ModernButtonUI(Color base, Color hover, Color pressed, Color text, int radius, boolean filled) {
            this.base=base; this.hover=hover; this.pressed=pressed; this.text=text; this.radius=radius; this.filled=filled;
        }
        @Override public void installUI (JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setOpaque(false);
            b.setBorderPainted(false);
            b.setForeground(text);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addMouseListener(new MouseAdapter() {});
        }
        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            ButtonModel m = b.getModel();
            Color fill = base;
            if (m.isPressed()) fill = pressed;
            else if (m.isRollover()) fill = hover;

            Shape rr = new RoundRectangle2D.Float(0, 0, b.getWidth(), b.getHeight(), radius * 2f, radius * 2f);
            if (filled) {
                g2.setColor(fill);
                g2.fill(rr);
                g2.setColor(new Color(0,0,0,25));
                g2.draw(rr);
            } else {
                g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 35));
                g2.fill(rr);
                g2.setColor(new Color(b.getForeground().getRed(), b.getForeground().getGreen(), b.getForeground().getBlue(), 160));
                g2.draw(rr);
            }

            // texto
            FontMetrics fm = g2.getFontMetrics();
            int tx = (b.getWidth() - fm.stringWidth(b.getText())) / 2;
            int ty = (b.getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.setColor(text);
            g2.drawString(b.getText(), tx, ty);

            g2.dispose();
        }
    }

    // Logo circular
    private static class BufferedImagePanel {
        private final Dimension size;
        private final Image image;
        private final int radius;
        public BufferedImagePanel(Dimension size, Image image, int radius) {
            this.size = size; this.image = image; this.radius = radius;
        }
        public Icon asIcon() {
            int w = size.width, h = size.height;
            Image img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = (Graphics2D) img.getGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape clip = new RoundRectangle2D.Float(4,4,w-8,h-8, radius, radius);
            g2.setColor(Color.WHITE);
            g2.fillOval(2,2,w-4,h-4);
            g2.setClip(clip);
            g2.drawImage(image, (w-128)/2, (h-128)/2, 128,128, null);
            g2.setClip(null);
            g2.setColor(new Color(0,0,0,40));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(2,2,w-4,h-4);
            g2.dispose();
            return new ImageIcon(img);
        }
    }
}
