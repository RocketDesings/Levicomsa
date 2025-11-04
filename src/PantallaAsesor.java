import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
    private JPanel panelTabla;
    private JLabel lblPuesto;
    private JButton btnCambiarContra;
    private JButton btnConsultarCliente;
    private JPanel paneLogos;
    private JPanel panelInfos;

    // ====== comportamiento ======
    private AutoActualizarTabla autoActualizador;
    private TableRowSorter<DefaultTableModel> sorter;

    // ====== sesión ======
    private final int usuarioId;
    private int sucursalId = -1;
    private String nombreTrabajador;
    private String nombreSucursal;
    private String puesto; // 'admin', 'cajero' o 'asesor'

    // Paginación simple
    private static final int PAGE_SIZE = 300;
    private int currentOffset = 0;

    // ====== Paleta (idéntica a PantallaAdmin) ======
    private static final Color GREEN_DARK   = new Color(0x0A6B2A);
    private static final Color GREEN_BASE   = new Color(0x16A34A);
    private static final Color GREEN_SOFT   = new Color(0x22C55E);
    private static final Color BG_CANVAS    = new Color(0xF3F4F6);
    private static final Color CARD_BG      = new Color(255, 255, 255);
    private static final Color TEXT_PRIMARY = new Color(0x111827);
    private static final Color TEXT_MUTED   = new Color(0x67676E);
    private static final Color BORDER_SOFT  = new Color(0x535353);
    private static final Color BORDER_FOCUS = new Color(0x059669);
    private static final Color TABLE_ALT    = new Color(0xF9FAFB);
    private static final Color TABLE_SEL_BG = new Color(0xE6F7EE);
    private static final Color TABLE_SEL_TX = TEXT_PRIMARY;
    Font fText  = new Font("Segoe UI", Font.PLAIN, 16);
    Font fText2  = new Font("Segoe UI", Font.PLAIN, 12);
    // hover visual para la tabla (igual que Admin)
    private int hoverRow = -1;
    private static final ZoneId ZONA_MAZATLAN = ZoneId.of("America/Mazatlan");
    private static final Locale  LOCALE_MX    = new Locale("es", "MX");
    public PantallaAsesor() { this(-1); }
    public PantallaAsesor(int usuarioId) {
        this.usuarioId = usuarioId;

        // Fuente global (igual que Admin)
        try { setUIFont(new Font("Segoe UI", Font.BOLD, 13)); } catch (Exception ignore) {}

        // ===== Frame =====
        pantalla = new JFrame("Pantalla Asesor");
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

        // Iconos (si tienes helper UiImages)
        try {
            UiImages.setIcon(lblIcono, "/images/levicomsa.png", 150);
            UiImages.setIcon(lblImagen, "/images/usuario.png", 100);
        } catch (Throwable ignore) {}

        // Estilo general (espejo de Admin)
        applyTheme();
        pantalla.pack();
        pantalla.setLocationRelativeTo(null);
        pantalla.setVisible(true);
        btnModificarCliente.setFont(fText);
        btnCobrar.setFont(fText);
        btnSalir.setFont(fText);
        btnAgregarCliente.setFont(fText);
        btnCambiarContra.setFont(fText2);
        btnConsultarCliente.setFont(fText);
        // ===== Acciones (sin tocar lógica) =====
        if (btnConsultarCliente != null) btnConsultarCliente.addActionListener(e -> abrirConsultarCliente());
        if (btnSalir != null) btnSalir.addActionListener(e -> new AlertaCerrarSesion(pantalla));
        if (btnAgregarCliente != null) btnAgregarCliente.addActionListener(e -> abrirFormularioAgregarCliente());
        if (btnModificarCliente != null) btnModificarCliente.addActionListener(e -> abrirSeleccionModificar());
        if (btnCobrar != null) {
            btnCobrar.addActionListener(e -> {
                if (sucursalId <= 0) {
                    JOptionPane.showMessageDialog(pantalla, "No se detectó sucursal del usuario.");
                    return;
                }
                EnviarCobro.mostrar(sucursalId, usuarioId);
            });
        }
        if (btnCambiarContra != null) {
            btnCambiarContra.addActionListener(e ->
                    new CambiarContrasenaDialog(this.usuarioId, false).setVisible(true)
            );
        }

        iniciarReloj();
        configurarTabla();
        cablearBusquedaInline();
        cargarDatosAsesor();
        cargarClientesDesdeBD();

        // Auto refresh
        autoActualizador = new AutoActualizarTabla(this::cargarClientesDesdeBD, 5000);
        autoActualizador.iniciar();

        if (tfBuscar != null) SwingUtilities.invokeLater(() -> tfBuscar.requestFocusInWindow());
    }

    // ========= THEME / ESTILO (calcado de Admin) =========
    private void applyTheme() {
        // Fondo principal (Admin deja blanco)
        if (panelMain != null) panelMain.setBackground(Color.WHITE);

        // Cards con sombra y borde redondeado
        decorateAsCard(panelInfo);
        decorateAsCard(panelExtra);
        decorateAsCard(panelBotones);
        decorateAsCard(panelTabla);
        decorateAsCard(panelBusqueda);
        decorateAsCard(paneLogos);
        decorateAsCard(panelInfos);
        decorateAsCard(panelMain    );

        // Scrollbars modernos (igual que Admin)
        JScrollPane scr = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, tblAsesor);
        if (scr != null) {
            scr.getVerticalScrollBar().setUI(new SmoothScrollBarUI());
            scr.getHorizontalScrollBar().setUI(new SmoothScrollBarUI());
            scr.setBorder(new MatteBorder(1,1,1,1,BORDER_SOFT));
            scr.getViewport().setBackground(CARD_BG);
        }

        // Tipografías y jerarquía visual (igual que Admin)
        if (lblSlogan != null) {
            lblSlogan.setText("<html>Comprometidos con tu tranquilidad, ofreciéndote soluciones a la medida de <br>tus necesidades.</html>");
            lblSlogan.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 22));
            lblSlogan.setForeground(TEXT_MUTED);
        }
        if (lblTitulo   != null) { lblTitulo.setFont(new Font("Segoe UI Black", Font.BOLD, 54)); lblTitulo.setForeground(TEXT_PRIMARY); }
        if (lblNombre   != null) { lblNombre.setFont(new Font("Segoe UI Semibold", Font.BOLD, 24)); lblNombre.setForeground(TEXT_PRIMARY); }
        if (lblSucursal != null) { lblSucursal.setFont(new Font("Segoe UI Semibold", Font.BOLD, 30)); lblSucursal.setForeground(TEXT_PRIMARY); }
        if (lblPuesto   != null) { lblPuesto.setFont(new Font("Segoe UI", Font.BOLD, 20)); lblPuesto.setForeground(TEXT_MUTED); }

        // Botones (mismos estilos que Admin)
        if (btnAgregarCliente   != null) stylePrimaryButton(btnAgregarCliente);
        if (btnModificarCliente != null) styleGhostButton(btnModificarCliente);
        if (btnConsultarCliente != null) styleGhostButton(btnConsultarCliente);
        if (btnCambiarContra    != null) styleGhostButton(btnCambiarContra);
        if (btnCobrar           != null) styleGhostButton(btnCobrar);
        if (btnSalir            != null) styleExitButton(btnSalir);

        // Campo de búsqueda (igual que Admin)
        if (tfBuscar != null) styleSearchField(tfBuscar);
        if (buscarButton != null) {
            buscarButton.setUI(new ModernButtonUI(GREEN_BASE, GREEN_SOFT, GREEN_DARK, Color.WHITE, 12, true));
            buscarButton.setBorder(new EmptyBorder(10,16,10,16));
            buscarButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
    }

    private void decorateAsCard(JComponent c) {
        if (c == null) return;
        c.setOpaque(true);
        c.setBackground(CARD_BG);
        c.setBorder(new CompoundRoundShadowBorder(14, BORDER_SOFT, new Color(0,0,0,28)));
    }

    private void setUIFont(Font f) {
        var keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object val = UIManager.get(key);
            if (val instanceof Font) UIManager.put(key, f);
        }
    }

    // ========= RELOJ =========
    private void iniciarReloj() {
        if (lblHora == null) return;

        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withLocale(LOCALE_MX);

        javax.swing.Timer timer = new javax.swing.Timer(1000, e -> {
            String ahora = ZonedDateTime.now(ZONA_MAZATLAN).format(fmt);
            lblHora.setText(ahora); // hora local Mazatlán
        });
        timer.setInitialDelay(0);
        timer.start();
    }

    // ========= BÚSQUEDA =========
    private void cablearBusquedaInline() {
        if (tfBuscar == null) return;

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
            buscarButton.addActionListener(e -> aplicarFiltro(tfBuscar.getText().trim()));
    }

    private void aplicarFiltro(String q) {
        if (sorter == null) return;
        if (q == null || q.isBlank()) { sorter.setRowFilter(null); return; }
        String regex = "(?i)" + java.util.regex.Pattern.quote(q);
        List<RowFilter<Object,Object>> cols = new ArrayList<>();
        for (int c = 0; c <= 6; c++) cols.add(RowFilter.regexFilter(regex, c));
        sorter.setRowFilter(RowFilter.orFilter(cols));
    }

    // ========= TABLA (igual que Admin: header verde, zebra, hover) =========
    private void configurarTabla() {
        String[] columnas = {"Nombre", "Teléfono", "CURP", "Pensionado", "RFC", "NSS", "Correo"};
        DefaultTableModel modelo = new DefaultTableModel(columnas, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        tblAsesor.setModel(modelo);
        sorter = new TableRowSorter<>(modelo);
        tblAsesor.setRowSorter(sorter);
        tblAsesor.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        tblAsesor.setRowHeight(30);
        tblAsesor.setShowGrid(false);
        tblAsesor.setIntercellSpacing(new Dimension(0, 0));

        // Header verde
        JTableHeader header = tblAsesor.getTableHeader();
        header.setDefaultRenderer(new HeaderRenderer(header.getDefaultRenderer(), GREEN_DARK, Color.WHITE));
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 38));

        // Zebra + selección + hover
        tblAsesor.setDefaultRenderer(Object.class, new ZebraRenderer());
        tblAsesor.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int r = tblAsesor.rowAtPoint(e.getPoint());
                if (r != hoverRow) { hoverRow = r; tblAsesor.repaint(); }
            }
        });
        tblAsesor.addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) { hoverRow = -1; tblAsesor.repaint(); }
        });

        int[] widths = {220, 140, 160, 110, 160, 140, 260};
        for (int i = 0; i < Math.min(widths.length, tblAsesor.getColumnCount()); i++) {
            tblAsesor.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private static String capitalizarRol(String s) {
        if (s == null || s.isBlank()) return "";
        String[] parts = s.split("/");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (!p.isEmpty()) {
                out.append(Character.toUpperCase(p.charAt(0)));
                if (p.length() > 1) out.append(p.substring(1).toLowerCase());
            }
            if (i < parts.length - 1) out.append("/");
        }
        return out.toString();
    }



    // ========= DATOS DEL ASESOR =========
    private void cargarDatosAsesor() {
        if (usuarioId <= 0) {
            if (lblNombre   != null) lblNombre.setText("Asesor");
            if (lblSucursal != null) lblSucursal.setText("");
            if (lblPuesto   != null) lblPuesto.setText("");
            sucursalId = -1;
            return;
        }

        final String sql = """
        SELECT 
            COALESCE(u.nombre, t.nombre, u.usuario) AS nombreTrabajador,
            s.nombre AS sucursal,
            s.id     AS sucursal_id,
            COALESCE(LOWER(r.nombre),
                     CASE u.rol_id WHEN 1 THEN 'admin'
                                   WHEN 2 THEN 'cajero'
                                   WHEN 3 THEN 'asesor'
                     END,
                     LOWER(t.puesto)) AS rol_texto
        FROM Usuarios u
        LEFT JOIN trabajadores t ON t.id = u.trabajador_id
        LEFT JOIN sucursales   s ON s.id = t.sucursal_id
        LEFT JOIN roles        r ON r.id = u.rol_id
        WHERE u.id = ?
        """;

        String nombreTrabajador = "Asesor";
        String nombreSucursal   = "";
        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    nombreTrabajador = rs.getString("nombreTrabajador");
                    nombreSucursal   = rs.getString("sucursal");
                    sucursalId       = rs.getInt("sucursal_id");
                    puesto           = rs.getString("rol_texto");
                } else {
                    sucursalId = -1; puesto = "";
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

        if (lblNombre   != null) lblNombre.setText(nombreTrabajador != null ? nombreTrabajador : "Asesor");
        if (lblSucursal != null) lblSucursal.setText(nombreSucursal   != null ? nombreSucursal   : "");
        if (lblPuesto   != null) lblPuesto.setText(capitalizarRol(puesto));

    }

    // ========= CARGA DE CLIENTES =========
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
                DefaultTableModel modelo = (DefaultTableModel) tblAsesor.getModel();
                tblAsesor.getTableHeader().setReorderingAllowed(false);
                modelo.setRowCount(0);

                while (rs.next()) {
                    String nombre     = rs.getString(1);
                    String telefono   = rs.getString(2);
                    String curp       = rs.getString(3);
                    String pensionado = rs.getBoolean(4) ? "Sí" : "No";
                    String rfc        = rs.getString(5);
                    String nss        = rs.getString(6);
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

    //FUNCIONALIDAD BOTON MODIFICAR
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

    //FUNCIONALIDAD BOTON CONSULTAR CLIENTE
    // Abre la ventana de historial para el cliente seleccionado en la tabla
    private void abrirConsultarCliente() {
        int viewRow = tblAsesor.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(pantalla, "Selecciona un cliente de la tabla.");
            return;
        }
        int row = tblAsesor.convertRowIndexToModel(viewRow);
        DefaultTableModel m = (DefaultTableModel) tblAsesor.getModel();

        String nombre   = safeStr(m.getValueAt(row, 0)); // Nombre
        String tel      = safeStr(m.getValueAt(row, 1)); // Teléfono
        String curp     = safeStr(m.getValueAt(row, 2)); // CURP
        String rfc      = safeStr(m.getValueAt(row, 4)); // RFC
        String nss      = safeStr(m.getValueAt(row, 5)); // NSS

        int clienteId = resolverClienteId(nombre, curp, rfc, nss, tel);
        if (clienteId <= 0) {
            JOptionPane.showMessageDialog(pantalla,
                    "No se pudo identificar el cliente seleccionado.\n" +
                            "Asegúrate de que CURP/RFC/NSS estén capturados.");
            return;
        }
        ConsultarCliente.mostrar(pantalla, clienteId, nombre);
    }

    private String safeStr(Object o){ return o==null? "" : o.toString().trim(); }

    // Busca el id del cliente priorizando identificadores únicos
    private int resolverClienteId(String nombre, String curp, String rfc, String nss, String tel) {
        final String qCurp = "SELECT id FROM Clientes WHERE CURP=? LIMIT 1";
        final String qRfc  = "SELECT id FROM Clientes WHERE RFC=?  LIMIT 1";
        final String qNss  = "SELECT id FROM Clientes WHERE NSS=?  LIMIT 1";
        final String qNomTel = "SELECT id FROM Clientes WHERE nombre=? AND telefono=? ORDER BY id DESC LIMIT 1";

        try (Connection con = DB.get()) {
            if (!curp.isBlank()) {
                try (PreparedStatement ps = con.prepareStatement(qCurp)) {
                    ps.setString(1, curp);
                    try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
                }
            }
            if (!rfc.isBlank()) {
                try (PreparedStatement ps = con.prepareStatement(qRfc)) {
                    ps.setString(1, rfc);
                    try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
                }
            }
            if (!nss.isBlank()) {
                try (PreparedStatement ps = con.prepareStatement(qNss)) {
                    ps.setString(1, nss);
                    try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
                }
            }
            if (!nombre.isBlank() && !tel.isBlank()) {
                try (PreparedStatement ps = con.prepareStatement(qNomTel)) {
                    ps.setString(1, nombre);
                    ps.setString(2, tel);
                    try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(pantalla, "Error al buscar cliente: " + ex.getMessage());
        }
        return -1;
    }

    // ========= estilos reutilizables (idénticos a Admin) =========
    private void stylePrimaryButton(JButton b) {
        b.setUI(new ModernButtonUI(GREEN_DARK, GREEN_SOFT, GREEN_DARK, Color.WHITE, 15, true));
        b.setBorder(new EmptyBorder(10,18,10,28));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    private void styleGhostButton(JButton b) {
        b.setUI(new ModernButtonUI(new Color(0,0,0,20), new Color(0,0,0,35), new Color(0,0,0,60), TEXT_PRIMARY, 15, false));
        b.setBorder(new EmptyBorder(10,28,10,18));
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
            } else if (r == hoverRow) {
                comp.setBackground(new Color(0xEEF9F2)); // hover sutil
                comp.setForeground(TEXT_PRIMARY);
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
            this.arc = arc; this.pad = padding; this.color = color;
        }
        @Override public Insets getBorderInsets(Component c) {
            return new Insets(pad.top + top, pad.left + left, pad.bottom + bottom, pad.right + right);
        }
        @Override public boolean isBorderOpaque() { return false; }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = width  - 1, h = height - 1;
            g2.setColor(color);
            g2.drawRoundRect(x, y, w, h, arc, arc);
            g2.dispose();
        }
    }

    // Borde con sombra + esquinas redondeadas para “cards” (igual que Admin)
    static class CompoundRoundShadowBorder extends EmptyBorder {
        private final int arc;
        private final Color border;
        private final Color shadow;
        public CompoundRoundShadowBorder(int arc, Color border, Color shadow) {
            super(12,12,12,12);
            this.arc = arc; this.border = border; this.shadow = shadow;
        }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // sombra suave
            g2.setColor(shadow);
            for (int i=0;i<8;i++) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.08f));
                g2.fillRoundRect(x+2+i, y+4+i, w-4, h-6, arc, arc);
            }
            // borde sutil
            g2.setComposite(AlphaComposite.SrcOver);
            g2.setColor(border);
            g2.drawRoundRect(x+1, y+1, w-3, h-3, arc, arc);
            g2.dispose();
        }
    }

    static class ModernButtonUI extends BasicButtonUI {
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
                g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 25));
                g2.fill(rr);
                g2.setColor(new Color(0,0,0,60));
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

    // Scrollbars finos y redondeados (idénticos a Admin)
    private static class SmoothScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            this.thumbColor = new Color(0xCBD5E1);
            this.trackColor = new Color(0xF1F5F9);
        }
        @Override protected Dimension getMinimumThumbSize() { return new Dimension(8, 40); }
        @Override protected JButton createDecreaseButton(int orientation) { return botonVacio(); }
        @Override protected JButton createIncreaseButton(int orientation) { return botonVacio(); }
        private JButton botonVacio() {
            JButton b = new JButton(); b.setPreferredSize(new Dimension(0,0)); b.setOpaque(false); b.setBorder(null); return b;
        }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            g2.fillRoundRect(r.x+2, r.y+2, r.width-4, r.height-4, 8, 8);
            g2.dispose();
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2=(Graphics2D)g.create();
            g2.setColor(trackColor);
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);
            g2.dispose();
        }
    }

    // Muestra una imagen recortada en círculo con borde sutil (conservado)
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
