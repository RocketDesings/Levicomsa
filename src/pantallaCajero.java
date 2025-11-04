import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
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
    private JLabel lblTitulo;
    // Asegúrate de añadir este JLabel en el diseñador con este nombre:
    private JLabel lblNombre;
    private JPanel panelTitulo;
    private JPanel panelBienvenida;
    private JPanel panelMonto;
    private JPanel panelBotones;
    private JLabel lblBienvenida;
    private JLabel lblMonto;
    private JPanel panelTextos;

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
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        decorateAsCard(panelMonto);
        decorateAsCard(panelMain);
        decorateAsCard(panelTextos);
        decorateAsCard(panelMain);
        decorateAsCard(panelBotones);
        lblBienvenida.setFont(fTitle);
        lblNombre.setFont(fTitle);
        lblMonto.setFont(fText);
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
        if (txtMontoInicial != null) styleTextField(txtMontoInicial);
        tbnIngresar.addActionListener(e -> onIngresar(frame));
        stylePrimaryButton(tbnIngresar);
        styleExitButton(tbnCerrarSesion);
        tbnCerrarSesion.addActionListener(e -> {
            frame.dispose();
            new Login();
        });
// Tarjeta con sombra y esquinas redondeadas que envuelve tu mainPanel
        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                frame.setShape(new java.awt.geom.RoundRectangle2D.Double(
                        0, 0, frame.getWidth(), frame.getHeight(), 14, 14
                ));
            }
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
    private void styleTextField(JTextField tf) {
        tf.setOpaque(true);
        tf.setBackground(Color.WHITE);
        tf.setForeground(TEXT_PRIMARY);
        tf.setCaretColor(TEXT_PRIMARY);
        tf.setBorder(new Login.CompoundBorderRounded(BORDER_SOFT, 12, 1, new Insets(10, 12, 10, 12)));
        tf.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                tf.setBorder(new Login.CompoundBorderRounded(BORDER_FOCUS, 12, 2, new Insets(10,12,10,12)));
            }
            @Override public void focusLost(FocusEvent e) {
                tf.setBorder(new Login.CompoundBorderRounded(BORDER_SOFT, 12, 1, new Insets(10,12,10,12)));
            }
        });
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

    // Overloads para abrir desde otros módulos
    public static void mostrar(int usuarioId) {
        new pantallaCajero(usuarioId);
    }
    public static void mostrar(int sucursalId, int usuarioId, boolean usarAmbos) {
        new pantallaCajero(sucursalId, usuarioId, true);
    }
}
