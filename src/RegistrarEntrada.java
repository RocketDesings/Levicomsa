import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class RegistrarEntrada {
    private JPanel panelMain;
    private JTextField txtMonto;
    private JPanel panelInfo;
    private JButton btnConfirmar;
    private JButton btnCancelar;
    private JTextField txtMotivoEntrada;
    private JPanel panelBotones;
    private JLabel lblTitulo;
    private JLabel lblMonto;
    private JLabel lblMotivo;

    private static volatile boolean ABIERTO = false;   // ← candado global
    public static boolean isAbierto() { return ABIERTO; }

    // contexto
    private final int sucursalId;
    private final int usuarioId;
    private JFrame frame;

    // ===== Paleta y tipografías (igual que pantallaCajero / PantallaAdmin) =====
    private static final Color BORDER_SOFT  = new Color(0x535353);
    private static final Color CARD_BG      = new Color(255, 255, 255);
    private static final Color GREEN_DARK   = new Color(0x0A6B2A);
    private static final Color GREEN_BASE   = new Color(0x16A34A);
    private static final Color GREEN_SOFT   = new Color(0x22C55E);
    private static final Color TEXT_PRIMARY = new Color(0x111827);
    private static final Color BORDER_FOCUS = new Color(0x059669);
    private final Font fText   = new Font("Segoe UI", Font.PLAIN, 16);
    private final Font fTitle  = new Font("Segoe UI", Font.BOLD, 22);

    // ==== Abrir desde otros módulos (con anti-duplicado) ====
    public static void mostrar(int sucursalId, int usuarioId) {
        if (ABIERTO) { JOptionPane.showMessageDialog(null, "Ya está abierto 'Registrar ENTRADA'."); return; }
        new RegistrarEntrada(sucursalId, usuarioId);
    }

    public RegistrarEntrada(int sucursalId, int usuarioId) {
        this.sucursalId = sucursalId;
        this.usuarioId  = usuarioId;

        ABIERTO = true; // toma el candado

        // Fuente global consistente
        try { setUIFont(new Font("Segoe UI", Font.BOLD, 13)); } catch (Exception ignored) {}

        // ===== Frame con el mismo estilo (undecorated + esquinas redondeadas) =====
        frame = new JFrame("Registrar ENTRADA");
        frame.setUndecorated(true);
        frame.setContentPane(panelMain);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        // Bordes redondeados como en tus otras pantallas
        frame.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                frame.setShape(new RoundRectangle2D.Double(0, 0, frame.getWidth(), frame.getHeight(), 14, 14));
            }
        });

        // ===== Estilo visual a lo pantallaCajero =====
        applyTheme();

        // Accesos rápidos: ESC cierra, Enter confirma
        if (panelMain != null) {
            panelMain.registerKeyboardAction(
                    e -> frame.dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
            );
        }
        if (txtMonto != null) {
            txtMonto.addActionListener(e -> btnConfirmar.doClick());
            // Solo números, punto o coma
            txtMonto.addKeyListener(new KeyAdapter() {
                @Override public void keyTyped(KeyEvent e) {
                    char c = e.getKeyChar();
                    if (!Character.isDigit(c) && c != '.' && c != ',' && c != '\b') e.consume();
                }
            });
        }

        // Eventos (misma funcionalidad original)
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed (WindowEvent e) { ABIERTO = false; }
            @Override public void windowClosing(WindowEvent e)  { ABIERTO = false; }
        });
        btnCancelar.addActionListener(e -> frame.dispose());
        btnConfirmar.addActionListener(e -> onConfirmar(frame));

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        lblTitulo.setFont(fTitle);
        // Foco inicial
        SwingUtilities.invokeLater(() -> {
            if (txtMonto != null) txtMonto.requestFocusInWindow();
        });
    }

    // =================== THEME / ESTILO ===================
    private void applyTheme() {
        // Tarjetas con sombra y borde redondeado (como PantallaAdmin / pantallaCajero)
        decorateAsCard(panelInfo);
        decorateAsCard(panelBotones);
        decorateAsCard(panelMain);

        // Inputs
        if (txtMonto != null) {
            txtMonto.setFont(fText);
            styleTextField(txtMonto);
        }
        if (txtMotivoEntrada != null) {
            txtMotivoEntrada.setFont(fText);
            styleTextField(txtMotivoEntrada);
        }

        // Botones (verde primario y cancelar rojo)
        if (btnConfirmar != null) {
            btnConfirmar.setFont(fText);
            stylePrimaryButton(btnConfirmar);
        }
        if (btnCancelar != null) {
            btnCancelar.setFont(fText);
            styleExitButton(btnCancelar);
        }
    }

    private void stylePrimaryButton(JButton b) {
        // Igual que pantallaCajero: usa ModernButtonUI de PantallaAdmin
        b.setUI(new PantallaAdmin.ModernButtonUI(GREEN_DARK, GREEN_SOFT, GREEN_DARK, Color.WHITE, 15, true));
        b.setBorder(new EmptyBorder(10,18,10,28));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void styleExitButton(JButton b) {
        // Botón rojo consistente con tu estilo
        Color ROJO_BASE    = new Color(0xDC2626);
        Color GRIS_HOVER   = new Color(0xD1D5DB);
        Color GRIS_PRESSED = new Color(0x9CA3AF);
        b.setUI(new Login.ModernButtonUI(ROJO_BASE, GRIS_HOVER, GRIS_PRESSED, Color.BLACK, 22, true));
        b.setBorder(new EmptyBorder(10,18,10,28));
        b.setForeground(Color.WHITE);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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

    private void decorateAsCard(JComponent c) {
        if (c == null) return;
        c.setOpaque(true);
        c.setBackground(CARD_BG);
        c.setBorder(new PantallaAdmin.CompoundRoundShadowBorder(14, BORDER_SOFT, new Color(0,0,0,28)));
    }

    private void setUIFont(Font f) {
        var keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object k = keys.nextElement();
            Object v = UIManager.get(k);
            if (v instanceof Font) UIManager.put(k, f);
        }
    }

    // =================== LÓGICA ORIGINAL (sin cambios) ===================
    private void onConfirmar(JFrame owner) {
        String rawMonto = txtMonto.getText() != null ? txtMonto.getText().trim() : "";
        if (rawMonto.isEmpty()) { JOptionPane.showMessageDialog(panelMain, "Ingresa un monto."); return; }

        BigDecimal monto;
        try {
            rawMonto = rawMonto.replace(",", ".");
            monto = new BigDecimal(rawMonto).setScale(2, RoundingMode.HALF_UP);
            if (monto.compareTo(BigDecimal.ZERO) <= 0) { JOptionPane.showMessageDialog(panelMain, "El monto debe ser mayor a 0."); return; }
        } catch (Exception ex) { JOptionPane.showMessageDialog(panelMain, "Monto inválido."); return; }

        String descripcion = txtMotivoEntrada.getText() != null ? txtMotivoEntrada.getText().trim() : "";
        if (descripcion.isEmpty()) descripcion = "ENTRADA MANUAL";

        btnConfirmar.setEnabled(false);

        Connection con = null;
        try {
            con = DB.get();
            con.setAutoCommit(false);

            try (PreparedStatement ps = con.prepareStatement("SET @app_user_id = ?")) {
                ps.setInt(1, usuarioId);
                ps.executeUpdate();
            }

            final String sql = "INSERT INTO caja_movimientos " +
                    "(sucursal_id, usuario_id, tipo, monto, descripcion, cobro_id) " +
                    "VALUES (?, ?, 'ENTRADA', ?, ?, NULL)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, sucursalId);
                ps.setInt(2, usuarioId);
                ps.setBigDecimal(3, monto);
                ps.setString(4, descripcion);
                ps.executeUpdate();
            }

            con.commit();
            JOptionPane.showMessageDialog(panelMain, "Entrada registrada: $" + monto.toPlainString());
            owner.dispose();

        } catch (Exception ex) {
            try { if (con != null) con.rollback(); } catch (Exception ignore) {}
            ex.printStackTrace();
            JOptionPane.showMessageDialog(panelMain, "No se pudo registrar la entrada:\n" + ex.getMessage());
            btnConfirmar.setEnabled(true);
        } finally {
            try { if (con != null) con.setAutoCommit(true); } catch (Exception ignore) {}
            try { if (con != null) con.close(); } catch (Exception ignore) {}
        }
    }
}
