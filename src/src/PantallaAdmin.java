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

    // Estos pueden o no existir en tu form; si existen, se usarán:
    private JPanel panelInfo;
    private JPanel panelExtra;
    private JPanel panelBotones;
    private JLabel lblIcono;
    private JLabel lblNombre;
    private JLabel lblSucursal;
    private JPanel panelBusqueda;
    private JTextField tfBuscar;
    private JButton buscarButton;

    // ====== comportamiento ======
    private AutoActualizarTabla autoActualizador;
    private TableRowSorter<DefaultTableModel> sorter;

    // sesión
    private final int usuarioId;   // Usuarios.id
    private String nombreTrabajador;
    private String nombreSucursal;

    // paginación
    private static final int PAGE_SIZE = 300;
    private int currentOffset = 0;

    // ---------- constructores ----------
    public PantallaAdmin() { this(-1); }
    public PantallaAdmin(int usuarioId) {
        this.usuarioId = usuarioId;

        // Frame
        pantalla = new JFrame("Panel de Administración");
        pantalla.setUndecorated(true);
        pantalla.setContentPane(panelMain);
        pantalla.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // pantalla completa (no exclusiva → no “minimiza” al abrir UI propia)
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

        // logo (si tienes lblIcono)
        if (lblIcono != null) {
            ImageIcon icono = null;
            try { URL url = getClass().getResource("/images/logo.png"); if (url != null) icono = new ImageIcon(url); } catch (Exception ignored) {}
            if (icono == null) {
                try { icono = new ImageIcon("resources/images/logo.png"); } catch (Exception ignored) {}
                if (icono.getImageLoadStatus() != MediaTracker.COMPLETE) icono = new ImageIcon("src/images/logo.png");
            }
            Image img = icono.getImage().getScaledInstance(150, 150, Image.SCALE_SMOOTH);
            lblIcono.setIcon(new ImageIcon(img));
        }

        // bordes si existen
        if (panelInfo  != null) panelInfo.setBorder(new MatteBorder(0,2,2,0, Color.BLACK));
        if (panelExtra != null) panelExtra.setBorder(new MatteBorder(0,0,2,0, Color.BLACK));

        // acciones
        if (btnSalir != null) btnSalir.addActionListener(e -> mostrarAlertaCerrarSesion());
        if (btnAgregarCliente != null) btnAgregarCliente.addActionListener(e -> abrirFormularioAgregarCliente());
        if (btnModificarCliente != null) btnModificarCliente.addActionListener(e -> abrirSeleccionModificar());
        // si tienes un botón de eliminar/cobrar, conéctalo igual

        iniciarReloj();
        configurarTabla();
        cablearBusquedaInline(); // usa tfBuscar+buscarButton si están en el form
        cargarDatosAdmin();      // lblNombre/lblSucursal si existen

        cargarClientesDesdeBD();

        autoActualizador = new AutoActualizarTabla(this::cargarClientesDesdeBD, 5000);
        autoActualizador.iniciar();

        // foco inicial en buscar si existe
        if (tfBuscar != null) SwingUtilities.invokeLater(() -> tfBuscar.requestFocusInWindow());
    }

    // ====================== UI básica ======================
    private void iniciarReloj() {
        if (lblHora == null) return;
        SimpleDateFormat formato = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        Timer timer = new Timer(1000, e -> lblHora.setText(formato.format(new Date())));
        timer.start();
    }

    private void configurarTabla() {
        String[] cols = {"Nombre", "Teléfono", "CURP", "Pensionado", "RFC", "Correo"};
        DefaultTableModel modelo = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table1.setModel(modelo);

        sorter = new TableRowSorter<>(modelo);
        table1.setRowSorter(sorter);
        table1.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        int[] widths = {200, 120, 150, 90, 150, 220};
        for (int i = 0; i < Math.min(widths.length, table1.getColumnCount()); i++) {
            table1.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private void cablearBusquedaInline() {
        if (tfBuscar == null || buscarButton == null) return;

        setPlaceholder(tfBuscar, "Buscar por nombre, teléfono, CURP, RFC o correo…");

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

        buscarButton.addActionListener(e -> aplicarFiltro(tfBuscar.getText().trim()));
    }

    private void setPlaceholder(JTextField tf, String texto) {
        tf.setForeground(Color.GRAY);
        tf.setText(texto);
        tf.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                if (tf.getText().equals(texto)) { tf.setText(""); tf.setForeground(Color.BLACK); }
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                if (tf.getText().isBlank()) { tf.setForeground(Color.GRAY); tf.setText(texto); }
            }
        });
    }

    private void aplicarFiltro(String q) {
        if (sorter == null) return;
        if (q == null || q.isBlank()) { sorter.setRowFilter(null); return; }
        String regex = "(?i)" + java.util.regex.Pattern.quote(q);
        java.util.List<RowFilter<Object,Object>> cols = new java.util.ArrayList<>();
        for (int c = 0; c <= 5; c++) cols.add(RowFilter.regexFilter(regex, c));
        sorter.setRowFilter(RowFilter.orFilter(cols));
    }

    private void stopAuto() { try { if (autoActualizador != null) autoActualizador.detener(); } catch (Exception ignored) {} }

    // ====================== Datos del admin ======================
    private void cargarDatosAdmin() {
        if (lblNombre == null && lblSucursal == null) return;
        if (usuarioId <= 0) {
            if (lblNombre != null)   lblNombre.setText("Administrador");
            if (lblSucursal != null) lblSucursal.setText("");
            return;
        }
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
        } catch (SQLException ignored) {}

        if (lblNombre != null)   lblNombre.setText(nombreTrabajador != null ? nombreTrabajador : "Administrador");
        if (lblSucursal != null) lblSucursal.setText(nombreSucursal != null ? nombreSucursal : "");
    }

    // ====================== Datos de clientes ======================
    public void cargarClientesDesdeBD() {
        final String sql = "SELECT nombre, telefono, CURP, pensionado, RFC, correo FROM Clientes ORDER BY nombre LIMIT ? OFFSET ?";
        int limit = PAGE_SIZE, offset = currentOffset;

        try (Connection conn = DB.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // (opcional) deja lista la conexión por si aquí mismo fueras a escribir y quieras bitácora
            if (usuarioId > 0) {
                try (PreparedStatement set = conn.prepareStatement("SET @app_user_id = ?")) {
                    set.setInt(1, usuarioId);
                    set.executeUpdate();
                }
            }

            ps.setInt(1, limit);
            ps.setInt(2, offset);

            try (ResultSet rs = ps.executeQuery()) {
                DefaultTableModel modelo = (DefaultTableModel) table1.getModel();
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

    private void mostrarAlertaCerrarSesion() {
        new AlertaCerrarSesion(pantalla);
    }
}
