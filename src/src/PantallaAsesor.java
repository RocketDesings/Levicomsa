import javax.swing.*;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PantallaAsesor implements Refrescable {

    // --- componentes UI (de tu formulario) ---
    private JFrame pantalla;
    private JPanel panelMain;
    private JTable tblAsesor;
    private JButton btnAgregarCliente;
    private JButton btnModificarCliente;
    private JButton btnCobrar;
    private JButton btnSalir;
    private JLabel lblNombre;
    private JLabel lblImagen;
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
    private JTextField tfBuscar; // <- ya lo tienes en tu panel
    private JLabel lblPlaceholder;

    // --- comportamiento ---
    private AutoActualizarTabla autoActualizador;
    private TableRowSorter<DefaultTableModel> sorter;

    // --- sesión / datos de usuario ---
    private final int usuarioId;        // Usuarios.id del logueado (para bitácora si se necesitara)
    private String nombreTrabajador;
    private String nombreSucursal;

    // --- paginación simple (si quisieras moverla) ---
    private static final int PAGE_SIZE = 300;
    private int currentOffset = 0;

    // Constructores
    public PantallaAsesor() { this(-1); }
    public PantallaAsesor(int usuarioId) {
        this.usuarioId = usuarioId;

        // ======== FRAME ========
        pantalla = new JFrame("Pantalla Asesor");
        pantalla.setUndecorated(true);
        pantalla.setContentPane(panelMain);
        pantalla.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Fullscreen NO exclusivo (no minimiza al abrir popups propios)
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

        pantalla.pack();
        pantalla.setLocationRelativeTo(null);
        pantalla.setVisible(true);

        // Logo desde resources (con fallback en dev)
        ImageIcon icono = null;
        try { URL url = getClass().getResource("/images/logo.png"); if (url != null) icono = new ImageIcon(url); } catch (Exception ignored) {}
        if (icono == null) {
            try { icono = new ImageIcon("resources/images/logo.png"); } catch (Exception ignored) {}
            if (icono.getImageLoadStatus() != MediaTracker.COMPLETE) icono = new ImageIcon("src/images/logo.png");
        }
        Image imagenEscalada = icono.getImage().getScaledInstance(150, 150, Image.SCALE_SMOOTH);
        lblIcono.setIcon(new ImageIcon(imagenEscalada));

        // Bordes decorativos
        panelInfo.setBorder(new MatteBorder(0, 2, 2, 0, Color.BLACK));
        panelExtra.setBorder(new MatteBorder(0, 0, 2, 0, Color.BLACK));

        // Botones
        btnSalir.addActionListener(e -> mostrarAlertaCerrarSesion());
        btnAgregarCliente.addActionListener(e -> abrirFormularioAgregarCliente());

        // Reloj, tabla, búsqueda y datos de asesor
        iniciarReloj();
        configurarTabla();
        cablearBusquedaInline();   // <- aquí conectamos tfBuscar + buscarButton
        cargarDatosAsesor();

        // Carga inicial
        cargarClientesDesdeBD();

        // Auto refresco cada 5s
        autoActualizador = new AutoActualizarTabla(this::cargarClientesDesdeBD, 5000);
        autoActualizador.iniciar();
    }

    // ====================== UI ======================
    private void iniciarReloj() {
        SimpleDateFormat formato = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        Timer timer = new Timer(1000, e -> lblHora.setText(formato.format(new Date())));
        timer.start();
    }

    private void mostrarAlertaCerrarSesion() { new AlertaCerrarSesion(pantalla); }

    private void configurarTabla() {
        String[] columnas = {"Nombre", "Teléfono", "CURP", "Pensionado", "RFC", "Correo"};
        DefaultTableModel modelo = new DefaultTableModel(columnas, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        tblAsesor.setModel(modelo);

        sorter = new TableRowSorter<>(modelo);
        tblAsesor.setRowSorter(sorter);
        tblAsesor.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        int[] widths = {200, 120, 150, 90, 150, 220};
        for (int i = 0; i < Math.min(widths.length, tblAsesor.getColumnCount()); i++) {
            tblAsesor.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    /** Conecta tfBuscar y buscarButton al filtro del TableRowSorter. */
    private void cablearBusquedaInline() {

        // filtrar mientras escribe
        tfBuscar.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { aplicarFiltro(tfBuscar.getText().trim()); }
            public void removeUpdate(DocumentEvent e)  { aplicarFiltro(tfBuscar.getText().trim()); }
            public void changedUpdate(DocumentEvent e) { aplicarFiltro(tfBuscar.getText().trim()); }
        });

        // Enter = buscar; ESC = limpiar
        tfBuscar.addActionListener(e -> aplicarFiltro(tfBuscar.getText().trim()));
        tfBuscar.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ESCAPE"), "clear");
        tfBuscar.getActionMap().put("clear", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                tfBuscar.setText(""); aplicarFiltro("");
            }
        });

        // botón Buscar
        buscarButton.addActionListener(e -> aplicarFiltro(tfBuscar.getText().trim()));
    }

    private void setPlaceholder(JTextField tf, String texto){
        tf.setForeground(Color.GRAY);
        tf.setText(texto);
        tf.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e){
                if (tf.getText().equals(texto)){ tf.setText(""); tf.setForeground(Color.BLACK); }
            }
            @Override public void focusLost(java.awt.event.FocusEvent e){
                if (tf.getText().isBlank()){ tf.setForeground(Color.GRAY); tf.setText(texto); }
            }
        });
    }

    private void aplicarFiltro(String q) {
        if (sorter == null) return;
        if (q == null || q.isBlank()) { sorter.setRowFilter(null); return; }

        String regex = "(?i)" + java.util.regex.Pattern.quote(q);
        java.util.List<RowFilter<Object,Object>> cols = new java.util.ArrayList<>();
        // columnas: 0=Nombre,1=Teléfono,2=CURP,3=Pensionado,4=RFC,5=Correo
        for (int c = 0; c <= 5; c++) cols.add(RowFilter.regexFilter(regex, c));
        sorter.setRowFilter(RowFilter.orFilter(cols));
    }

    private void stopAuto() { try { if (autoActualizador != null) autoActualizador.detener(); } catch (Exception ignored) {} }

    // ============== Datos del asesor (nombre/sucursal) ==============
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
            nombreTrabajador = "Asesor";
            nombreSucursal   = "";
            e.printStackTrace();
        }
        lblNombre.setText(nombreTrabajador != null ? nombreTrabajador : "Asesor");
        lblSucursal.setText(nombreSucursal != null ? nombreSucursal : "");
    }

    // =================== Datos de clientes ===================
    public void cargarClientesDesdeBD() {
        final String sql = "SELECT nombre, telefono, CURP, pensionado, RFC, correo FROM Clientes ORDER BY nombre LIMIT ? OFFSET ?";
        int limit = PAGE_SIZE, offset = currentOffset;

        try (Connection conn = DB.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // (opcional) prepara @app_user_id si luego vas a escribir con esta misma conexión
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
                modelo.setRowCount(0);

                while (rs.next()) {
                    String nombre     = rs.getString("nombre");
                    String telefono   = rs.getString("telefono");
                    String curp       = rs.getString("CURP");
                    String pensionado = rs.getBoolean("pensionado") ? "Sí" : "No";
                    String rfc        = rs.getString("RFC");
                    String correo     = rs.getString("correo");
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

    // =================== Abrir formularios ===================
    private void abrirFormularioAgregarCliente() {
        // Si tu formulario ya tiene ctor (Refrescable, int) úsalo; si no, cae al antiguo.
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
}
