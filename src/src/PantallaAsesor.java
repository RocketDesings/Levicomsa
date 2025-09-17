import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PantallaAsesor implements Refrescable {

    // ====== UI generada en el .form ======
    private JFrame pantalla;
    private JPanel panelMain;
    private JTable tblAsesor;
    private JButton btnAgregarCliente;
    private JButton btnModificarCliente;
    private JButton btnCobrar;
    private JButton btnSalir;
    private JLabel lblNombre;
    private JLabel lblImagen;
    private JLabel lblPlaceholder;
    private JLabel lblHora;
    private JLabel lblIcono;
    private JLabel lblTitulo;
    private JPanel panelInfo;
    private JLabel lblSlogan;
    private JLabel lblSucursal;
    private JComboBox comboBox1;
    private JButton buscarButton;
    private JPanel panelBotones;
    private JPanel panelBusqueda;
    private JPanel panelExtra;
    private JTextField tfBuscar;

    // ====== comportamiento ======
    private AutoActualizarTabla autoActualizador;
    private TableRowSorter<DefaultTableModel> sorter;

    // ====== sesión ======
    private final int usuarioId;
    private String nombreTrabajador;
    private String nombreSucursal;

    // Paginación simple
    private static final int PAGE_SIZE = 300;
    private int currentOffset = 0;

    // ====== Paleta (del logo) ======
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

    public PantallaAsesor() { this(-1); }
    public PantallaAsesor(int usuarioId) {
        this.usuarioId = usuarioId;

        // ===== Frame =====
        pantalla = new JFrame("Pantalla Asesor");
        pantalla.setUndecorated(true);
        pantalla.setContentPane(panelMain);
        pantalla.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Fullscreen (maximizada, no exclusiva → no minimiza al abrir diálogos propios)
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

        // Estilo general
        applyTheme();

        pantalla.pack();
        pantalla.setLocationRelativeTo(null);
        pantalla.setVisible(true);

        // Acciones
        btnSalir.addActionListener(e -> new AlertaCerrarSesion(pantalla));
        btnAgregarCliente.addActionListener(e -> abrirFormularioAgregarCliente());

        iniciarReloj();
        configurarTabla();
        cablearBusquedaInline();
        cargarDatosAsesor();
        cargarClientesDesdeBD();

        // Auto refresh
        autoActualizador = new AutoActualizarTabla(this::cargarClientesDesdeBD, 5000);
        autoActualizador.iniciar();

        SwingUtilities.invokeLater(() -> tfBuscar.requestFocusInWindow());

        // ---------- LOGO ----------
        ImageIcon icono = null;
        try {
            URL link = getClass().getResource("/images/levicomsa.png"); // <— en resources/images/
            if (link != null) icono = new ImageIcon(link);
        } catch (Exception ignored) {}
        if (icono == null) icono = new ImageIcon("resources/images/levicomsa.png"); // fallback en dev
        Image imagenEscalada = icono.getImage().getScaledInstance(200, 200, Image.SCALE_SMOOTH);
        if (lblIcono != null) lblIcono.setIcon(new ImageIcon(imagenEscalada));

        // ---------- LOGO ----------
        ImageIcon icono2 = null;
        try {
            URL url2 = getClass().getResource("/images/usuario.png"); // <— en resources/images/
            if (url2 != null) icono2 = new ImageIcon(url2);
        } catch (Exception ignored) {}
        if (icono2 == null) icono2 = new ImageIcon("resources/images/usuario.png"); // fallback en dev
        Image imagenEscalada2 = icono2.getImage().getScaledInstance(100, 100, Image.SCALE_SMOOTH);
        if (lblImagen != null) lblImagen.setIcon(new ImageIcon(imagenEscalada2));


    }

    // ========= THEME / ESTILO =========
    private void applyTheme() {
        // Fondo principal y “cards”
        panelMain.setBackground(BG_CANVAS);

        // Tipografías
        Font h1 = new Font("Segoe UI", Font.BOLD, 24);
        Font h2 = new Font("Segoe UI", Font.PLAIN, 14);
        lblSlogan.setText("<html>Comprometidos con tu tranquilidad,<br>ofreciéndote soluciones a la medida de tus necesidades.</html>");
        if (lblTitulo   != null) lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 70));
        if (lblTitulo   != null) lblTitulo.setForeground(TEXT_PRIMARY);
        if (lblNombre   != null) lblNombre.setFont(new Font("Segoe UI", Font.BOLD, 18));
        if (lblSucursal != null) lblSucursal.setFont(new Font("Segoe UI", Font.BOLD, 18));
        if (lblSlogan   != null) lblSlogan.setFont(new Font("Segoe UI", Font.BOLD, 30));
        if (lblSlogan   != null) lblSlogan.setForeground(TEXT_MUTED);
        if (lblNombre   != null) lblNombre.setFont(new Font("Segoe UI", Font.BOLD, 15));

        // Botones
        stylePrimaryButton(btnAgregarCliente);      // verde sólido
        styleOutlineButton(btnModificarCliente);    // outline discreto
        styleOutlineButton(btnCobrar);
        styleExitButton(btnSalir);                  // rojo base, hover gris, texto negro

        // Search field
        styleSearchField(tfBuscar);
        if (buscarButton != null) {
            buscarButton.setUI(new ModernButtonUI(GREEN_BASE, GREEN_SOFT, GREEN_DARK, Color.WHITE, 12, true));
            buscarButton.setBorder(new EmptyBorder(10,16,10,16));
            buscarButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        // Bordes laterales como en tu diseño original
        if (panelInfo  != null) panelInfo.setBorder(new MatteBorder(0,2,2,0, new Color(0xD1D5DB)));
        if (panelExtra != null) panelExtra.setBorder(new MatteBorder(0,0,2,0, new Color(0xD1D5DB)));
    }

    private void decorateAsCard(JComponent c) {
        c.setOpaque(true);
        c.setBackground(CARD_BG);
        c.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1,1,1,1, BORDER_SOFT),
                new EmptyBorder(12,12,12,12)
        ));
    }

    // ========= RELOJ =========
    private void iniciarReloj() {
        SimpleDateFormat formato = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        Timer timer = new Timer(1000, e -> lblHora.setText(formato.format(new Date())));
        timer.start();
    }

    // ========= BÚSQUEDA =========
    private void cablearBusquedaInline() {
        tfBuscar.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { aplicarFiltro(tfBuscar.getText().trim()); }
            public void removeUpdate(DocumentEvent e)  { aplicarFiltro(tfBuscar.getText().trim()); }
            public void changedUpdate(DocumentEvent e) { aplicarFiltro(tfBuscar.getText().trim()); }
        });
        tfBuscar.addActionListener(e -> aplicarFiltro(tfBuscar.getText().trim()));
        tfBuscar.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ESCAPE"), "clear");
        tfBuscar.getActionMap().put("clear", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                tfBuscar.setText(""); aplicarFiltro("");
            }
        });
        if (buscarButton != null)
            tfBuscar.addActionListener(e -> aplicarFiltro(tfBuscar.getText().trim()));
    }

    private void setPlaceholder(JTextField tf, String texto){
        tf.setForeground(TEXT_MUTED);
        tf.setText(texto);
        tf.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e){
                if (tf.getText().equals(texto)){ tf.setText(""); tf.setForeground(TEXT_PRIMARY); }
            }
            @Override public void focusLost(java.awt.event.FocusEvent e){
                if (tf.getText().isBlank()){ tf.setForeground(TEXT_MUTED); tf.setText(texto); }
            }
        });
    }

    private void aplicarFiltro(String q) {
        if (sorter == null) return;
        if (q == null || q.isBlank()) { sorter.setRowFilter(null); return; }
        String regex = "(?i)" + java.util.regex.Pattern.quote(q);
        List<RowFilter<Object,Object>> cols = new ArrayList<>();
        for (int c = 0; c <= 5; c++) cols.add(RowFilter.regexFilter(regex, c));
        sorter.setRowFilter(RowFilter.orFilter(cols));
    }

    // ========= TABLA =========
    private void configurarTabla() {
        String[] columnas = {"Nombre", "Teléfono", "CURP", "Pensionado", "RFC", "Correo"};
        DefaultTableModel modelo = new DefaultTableModel(columnas, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        tblAsesor.setModel(modelo);
        sorter = new TableRowSorter<>(modelo);
        tblAsesor.setRowSorter(sorter);
        tblAsesor.setRowHeight(28);
        tblAsesor.setShowGrid(false);
        tblAsesor.setIntercellSpacing(new Dimension(0, 0));

        // Header
        JTableHeader header = tblAsesor.getTableHeader();
        header.setDefaultRenderer(new HeaderRenderer(header.getDefaultRenderer(), GREEN_DARK, Color.WHITE));
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 36));

        // Zebra + selección accesible
        tblAsesor.setDefaultRenderer(Object.class, new ZebraRenderer());

        int[] widths = {220, 140, 160, 110, 160, 260};
        for (int i = 0; i < Math.min(widths.length, tblAsesor.getColumnCount()); i++) {
            tblAsesor.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        tblAsesor.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    }

    // ========= DATOS DEL ASESOR =========
    private void cargarDatosAsesor() {
        if (usuarioId <= 0) { lblNombre.setText("Asesor"); lblSucursal.setText(""); return; }

        final String sql = """
            SELECT COALESCE(u.nombre, t.nombre) AS nombreTrabajador,
                   s.nombre AS sucursal
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
                }
            }
        } catch (SQLException e) {
            nombreTrabajador = "Asesor"; nombreSucursal = "";
        }
        lblNombre.setText(nombreTrabajador != null ? nombreTrabajador : "Asesor");
        lblSucursal.setText(nombreSucursal != null ? nombreSucursal : "");
    }

    // ========= CARGA DE CLIENTES =========
    public void cargarClientesDesdeBD() {
        final String sql = "SELECT nombre, telefono, CURP, pensionado, RFC, correo FROM Clientes ORDER BY nombre LIMIT ? OFFSET ?";
        int limit = PAGE_SIZE, offset = currentOffset;

        try (Connection conn = DB.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // (opcional) deja lista la conexión por si luego escribes y quieres bitácora
            if (usuarioId > 0) {
                try (PreparedStatement set = conn.prepareStatement("SET @app_user_id = ?")) {
                    set.setInt(1, usuarioId);
                    set.executeUpdate();
                }
            }

            ps.setInt(1, limit);
            ps.setInt(2, offset);

            try (ResultSet rs = ps.executeQuery()) {
                DefaultTableModel modelo = (DefaultTableModel) tblAsesor.getModel();
                tblAsesor.getTableHeader().setReorderingAllowed(false);
                modelo.setRowCount(0);

                while (rs.next()) {
                    String nombre     = rs.getString(1);
                    String telefono   = rs.getString(2);
                    String curp       = rs.getString(3);
                    String pensionado = rs.getBoolean(4) ? "Sí" : "No";
                    String rfc        = rs.getString(5);
                    String correo     = rs.getString(6);
                    modelo.addRow(new Object[]{nombre, telefono, curp, pensionado, rfc, correo});
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

    private void stopAuto() {
        try { if (autoActualizador != null) autoActualizador.detener(); } catch (Exception ignored) {}
    }

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
    private void styleExitButton(JButton b) {
        Color ROJO_BASE = new Color(0xDC2626);
        Color GRIS_HOV  = new Color(0xD1D5DB);
        Color GRIS_PRE  = new Color(0x9CA3AF);
        b.setUI(new ModernButtonUI(ROJO_BASE, GRIS_HOV, GRIS_PRE, Color.BLACK, 12, true));
        b.setBorder(new EmptyBorder(10,18,10,18));
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

    // ========= renderers / bordes / botones modernos =========
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

    private static class ModernButtonUI extends BasicButtonUI {
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

    // Muestra una imagen recortada en círculo con borde sutil
    private static class BufferedImagePanel {
        private final Dimension size;
        private final Image image;
        private final int radius;
        public BufferedImagePanel(Dimension size, Image image, int radius) {
            this.size = size; this.image = image; this.radius = radius;
        }
        public Icon asIcon() {
            int w = size.width, h = size.height;
            Image img = new BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = (Graphics2D) img.getGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape clip = new RoundRectangle2D.Float(4,4,w-8,h-8, radius, radius);
            g2.setColor(Color.WHITE);
            g2.fillOval(2,2,w-4,h-4);                   // fondo blanco
            g2.setClip(clip);
            g2.drawImage(image, (w-128)/2, (h-128)/2, 128,128, null);
            g2.setClip(null);
            g2.setColor(new Color(0,0,0,40));          // borde/sombra
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(2,2,w-4,h-4);
            g2.dispose();
            return new ImageIcon(img);
        }
    }
}
