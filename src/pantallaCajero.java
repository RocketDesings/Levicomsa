import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class pantallaCajero {
    private JPanel panelMain;
    private JTextField txtMontoInicial;
    private JButton tbnIngresar;
    private JButton tbnCerrarSesion;
    private JLabel lblLogo;
    private JLabel lblTitulo;
    // Asegúrate de añadir este JLabel en el diseñador con este nombre:
    private JLabel lblNombre;

    // Contexto
    private int sucursalId;         // puede resolverse desde BD
    private final int usuarioId;

    // ==== Constructor 1: recibe SOLO usuarioId y resuelve sucursalId desde BD ====
    public pantallaCajero(int usuarioId) {
        this.usuarioId = usuarioId;
        this.sucursalId = obtenerSucursalIdDeUsuario(usuarioId); // -1 si no se encuentra
        inicializarUI();
        mostrarNombreUsuario(); // llena lblNombre
    }

    // ==== Constructor 2: recibe sucursalId y usuarioId (por compatibilidad) ====
    public pantallaCajero(int sucursalId, int usuarioId, boolean usarAmbos) {
        this.usuarioId  = usuarioId;
        this.sucursalId = sucursalId;
        inicializarUI();
        mostrarNombreUsuario();
    }

    // ==== Inicialización de UI (compartida por ambos constructores) ====
    private void inicializarUI() {
        JFrame frame = new JFrame("Pantalla Cajero");
        frame.setUndecorated(true);
        frame.setContentPane(panelMain);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);

        // UX: Enter envía; restringe a números/coma/punto
        if (txtMontoInicial != null) {
            txtMontoInicial.addActionListener(e -> tbnIngresar.doClick());
            txtMontoInicial.addKeyListener(new KeyAdapter() {
                @Override public void keyTyped(KeyEvent e) {
                    char c = e.getKeyChar();
                    if (!Character.isDigit(c) && c != '.' && c != ',' && c != '\b') e.consume();
                }
            });
        }

        tbnIngresar.addActionListener(e -> onIngresar(frame));
        tbnCerrarSesion.addActionListener(e -> {
            frame.dispose();
            new Login();
        });

        frame.setVisible(true);
    }

    // ==== Click en Ingresar ====
    private void onIngresar(JFrame owner) {
        String raw = (txtMontoInicial != null) ? txtMontoInicial.getText().trim() : "";
        if (raw.isEmpty()) {
            JOptionPane.showMessageDialog(panelMain, "Por favor ingresa un monto inicial.");
            return;
        }
        if (sucursalId <= 0) {
            JOptionPane.showMessageDialog(panelMain, "No se pudo determinar la sucursal del usuario.");
            return;
        }

        BigDecimal monto;
        try {
            raw = raw.replace(",", ".");
            monto = new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP);
            if (monto.compareTo(BigDecimal.ZERO) <= 0) {
                JOptionPane.showMessageDialog(panelMain, "El monto debe ser mayor a 0.");
                return;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(panelMain, "Monto inválido.");
            return;
        }

        tbnIngresar.setEnabled(false);

        Connection con = null;
        try {
            con = DB.get();
            con.setAutoCommit(false);

            // Auditoría si la usas en triggers
            try (PreparedStatement ps = con.prepareStatement("SET @app_user_id = ?")) {
                ps.setInt(1, usuarioId);
                ps.executeUpdate();
            }

            // Inserta ENTRADA (tu trigger AFTER INSERT reflejará en bitacora_cobros)
            final String sql = "INSERT INTO caja_movimientos " +
                    "(sucursal_id, usuario_id, tipo, monto, descripcion, cobro_id) " +
                    "VALUES (?, ?, 'ENTRADA', ?, 'INICIO TURNO CAJA', NULL)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, sucursalId);
                ps.setInt(2, usuarioId);
                ps.setBigDecimal(3, monto);
                ps.executeUpdate();
            }

            con.commit();
            JOptionPane.showMessageDialog(panelMain, "Monto inicial registrado: $" + monto.toPlainString());

            owner.dispose();
            // Importante: abrir InterfazCajero con el usuarioId para que cargue nombre y sucursal
            new InterfazCajero(usuarioId);

        } catch (Exception ex) {
            try { if (con != null) con.rollback(); } catch (Exception ignore) {}
            ex.printStackTrace();
            JOptionPane.showMessageDialog(panelMain, "No se pudo registrar la apertura de caja:\n" + ex.getMessage());
            tbnIngresar.setEnabled(true);
        } finally {
            try { if (con != null) con.setAutoCommit(true); } catch (Exception ignore) {}
            try { if (con != null) con.close(); } catch (Exception ignore) {}
        }
    }

    // ==== Helpers de datos ====
    private int obtenerSucursalIdDeUsuario(int usuarioId) {
        final String sql = """
            SELECT s.id
            FROM Usuarios u
            LEFT JOIN trabajadores t ON t.id = u.trabajador_id
            LEFT JOIN sucursales   s ON s.id = t.sucursal_id
            WHERE u.id = ?
            """;
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private void mostrarNombreUsuario() {
        String nombre = "Cajero";
        final String sql = """
            SELECT COALESCE(u.nombre, t.nombre, u.usuario) AS nombre
            FROM Usuarios u
            LEFT JOIN trabajadores t ON t.id = u.trabajador_id
            WHERE u.id = ?
            """;
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String n = rs.getString("nombre");
                    if (n != null && !n.isBlank()) nombre = n;
                }
            }
        } catch (Exception ignored) {}
        if (lblNombre != null) lblNombre.setText(nombre);
    }

    // Overloads para abrir desde otros módulos
    public static void mostrar(int usuarioId) {
        new pantallaCajero(usuarioId);
    }
    public static void mostrar(int sucursalId, int usuarioId, boolean usarAmbos) {
        new pantallaCajero(sucursalId, usuarioId, true);
    }
}
