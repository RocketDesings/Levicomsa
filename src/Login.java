import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.swing.JLayer;
import javax.swing.plaf.LayerUI;
import java.awt.geom.RoundRectangle2D;

public class Login {
    private JPanel mainPanel;
    private JTextField txtUsuario;
    private JTextField txtContrasena; // si luego cambias a JPasswordField, leerPasswordDeCampo ya lo soporta
    private JButton btnIniciarSesion;
    private JButton btnSalir;
    private JPanel panelLogo;
    private JPanel panelInfo;
    private JLabel lblLogo;
    private JPanel panelTxt;
    private JPanel panelBotones;

    // ===== Paleta alineada con PantallaAdmin/Asesor =====
    private static final Color GREEN_DARK   = new Color(0x0A6B2A);
    private static final Color GREEN_BASE   = new Color(0x16A34A);
    private static final Color GREEN_SOFT   = new Color(0x22C55E);
    private static final Color BG_TOP       = new Color(0x052E16); // verde muy oscuro
    private static final Color BG_BOT       = new Color(0x064E3B); // verde profundo
    private static final Color CARD_BG      = new Color(255, 255, 255);
    private static final Color TEXT_PRIMARY = new Color(0x111827);
    private static final Color TEXT_MUTED   = new Color(0x67676E);
    private static final Color BORDER_SOFT  = new Color(0x535353);
    private static final Color BORDER_FOCUS = new Color(0x059669);

    public Login() {

        // ---------- FRAME ----------
        JFrame loginFrame = new JFrame("Login");
        loginFrame.setUndecorated(true);
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Fuente global (consistente con el resto)
        try { setUIFont(new Font("Segoe UI", Font.PLAIN, 13)); } catch (Exception ignored) {}

        // Root con gradiente y tarjeta centrada
        GradientPanel root = new GradientPanel(BG_TOP, BG_BOT);
        root.setLayout(new GridBagLayout());

        // Tarjeta con sombra y esquinas redondeadas que envuelve tu mainPanel
        loginFrame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                loginFrame.setShape(new java.awt.geom.RoundRectangle2D.Double(
                        0, 0, loginFrame.getWidth(), loginFrame.getHeight(), 24, 24
                ));
            }
        });
        JLayer<JComponent> roundedLayer = new JLayer<>(mainPanel, new RoundedClipUI(10)); // 20 = radio de esquina
        loginFrame.setContentPane(roundedLayer);
        loginFrame.pack();
        loginFrame.setLocationRelativeTo(null);
        loginFrame.setVisible(true);
        // ---------- LOGO ----------
        UiImages.setIcon(lblLogo, "/images/levicomsa.png", 140);

        // ---------- TARJETAS INTERNAS ----------
        // Aplica estilo “card” a secciones del .form (no cambia layouts ni funcionalidad)
        decorateAsCard(panelLogo);
        decorateAsCard(panelInfo);
        decorateAsCard(panelTxt);
        decorateAsCard(panelBotones);
        decorateAsCard(mainPanel);

        // División sutil entre columnas si tu .form lo usa lado a lado
        if (panelLogo != null) {
            panelLogo.setOpaque(true);
            panelLogo.setBackground(CARD_BG);
            panelLogo.setBorder(BorderFactory.createCompoundBorder(
                    new MatteBorder(0, 0, 0, 0, new Color(0,0,0,0)),
                    new EmptyBorder(16,16,16,16)
            ));
        }

        // ---------- TIPOGRAFÍA ----------
        Font fTitle = new Font("Segoe UI Semibold", Font.PLAIN, 22);
        Font fText  = new Font("Segoe UI", Font.PLAIN, 16);
        if (lblLogo != null) lblLogo.setFont(fTitle);
        if (txtUsuario != null) txtUsuario.setFont(fText);
        if (txtContrasena != null) txtContrasena.setFont(fText);
        if (btnIniciarSesion != null) btnIniciarSesion.setFont(fText);
        if (btnSalir != null) btnSalir.setFont(fText);

        // ---------- CAMPOS ----------
        if (txtUsuario != null) styleTextField(txtUsuario);
        if (txtContrasena != null) styleTextField(txtContrasena);
        setPlaceholder(txtUsuario, "Usuario");
        setPlaceholder(txtContrasena, "Contraseña");

        // ---------- BOTONES ----------
        if (btnIniciarSesion != null) {
            stylePrimaryButton(btnIniciarSesion);
            btnIniciarSesion.setBorder(new EmptyBorder(12,18,12,18));
            btnIniciarSesion.addActionListener(e -> intentarLogin());
        }
        if (btnSalir != null) {
            styleExitButton(btnSalir);
            btnSalir.setBorder(new EmptyBorder(12,18,12,18));
            btnSalir.addActionListener(e -> System.exit(0));
        }
        if (txtContrasena != null) {
            txtContrasena.addActionListener(e -> intentarLogin());
        }
    }

    // ---------- Sesión / autenticación (sin cambios funcionales) ----------
    private static class Sesion {
        final int idUsuario;
        final int rolId;
        Sesion(int idUsuario, int rolId) { this.idUsuario = idUsuario; this.rolId = rolId; }
    }

    private Sesion autenticarUsuario(String usuario, String plainPassword) {
        // 1) Intento moderno: password_hash
        String q1 = "SELECT id, password_hash, rol_id FROM Usuarios WHERE usuario = ?";
        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(q1)) {
            ps.setString(1, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String hash = rs.getString("password_hash");
                    int rol = rs.getInt("rol_id");
                    int id  = rs.getInt("id");
                    if (comprobarPassword(plainPassword, hash)) {
                        registrarLastLogin(id);
                        return new Sesion(id, rol);
                    }
                }
            }
        } catch (SQLException ex) {
            if (!esColumnaDesconocida(ex)) { manejarSQLException(ex); return null; }
        }

        // 2) Legado: `contraseña` plano
        String q2 = "SELECT id, `contraseña` AS password_plano, rol_id FROM Usuarios WHERE usuario = ?";
        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(q2)) {
            ps.setString(1, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String plano = rs.getString("password_plano");
                    int rol = rs.getInt("rol_id");
                    int id  = rs.getInt("id");
                    if (plano != null && plainPassword.equals(plano)) {
                        registrarLastLogin(id);
                        return new Sesion(id, rol);
                    }
                }
            }
        } catch (SQLException ex) {
            manejarSQLException(ex);
        }
        return null;
    }

    /** Guarda NOW() en Usuarios.last_login para el usuario dado. */
    private void registrarLastLogin(int userId) {
        final String sql = "UPDATE Usuarios SET last_login = NOW() WHERE id = ?";
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[Login] No se pudo actualizar last_login: " + e.getMessage());
        }
    }

    // ======== DISEÑO: helpers (alineados con el resto) ========
    private void styleTextField(JTextField tf) {
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

    private void stylePrimaryButton(JButton b) {
        b.setUI(new ModernButtonUI(GREEN_BASE, GREEN_SOFT, GREEN_DARK, Color.WHITE, 12, true));
        b.setForeground(Color.WHITE);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void styleExitButton(JButton b) {
        Color ROJO_BASE    = new Color(0xDC2626);
        Color GRIS_HOVER   = new Color(0xD1D5DB);
        Color GRIS_PRESSED = new Color(0x9CA3AF);
        b.setUI(new ModernButtonUI(ROJO_BASE, GRIS_HOVER, GRIS_PRESSED, Color.BLACK, 12, true));
        b.setForeground(Color.WHITE);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // Aplica tarjeta con sombra suave y borde sutil
    private void decorateAsCard(JComponent c) {
        if (c == null) return;
        c.setOpaque(true);
        c.setBackground(CARD_BG);
        c.setBorder(new CompoundRoundShadowBorder(14, BORDER_SOFT, new Color(1,1,1,1)));
    }

    // Botón redondeado moderno (pinta fondo y mantiene el render del texto)
    static class ModernButtonUI extends BasicButtonUI {
        private final Color bg, hover, press, fg;
        private final int arc;
        private final boolean filled;

        ModernButtonUI(Color bg, Color hover, Color press, Color fg, int arc, boolean filled) {
            this.bg = bg; this.hover = hover; this.press = press; this.fg = fg; this.arc = arc; this.filled = filled;
        }
        @Override public void installUI(JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setOpaque(false);
            b.setBorder(new EmptyBorder(12, 18, 12, 18));
            b.setRolloverEnabled(true);
            b.setFocusPainted(false);
            b.setForeground(fg);
        }
        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            ButtonModel m = b.getModel();
            Color fill = m.isPressed() ? press : (m.isRollover() ? hover : bg);

            if (filled || fill.getAlpha() > 0) {
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), arc, arc);
            } else {
                g2.setColor(new Color(255, 255, 255, 100));
                g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, arc, arc);
            }

            g2.dispose();
            super.paint(g, c);
        }
    }

    // Tarjeta con sombra y esquinas redondeadas (contenedor exterior)
    static class CardPanel extends JPanel {
        private final int arc = 24;
        CardPanel() { setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            // sombra suave
            for (int i = 0; i < 10; i++) {
                float alpha = 0.05f * (10 - i);
                g2.setColor(new Color(0, 0, 0, (int) (alpha * 255)));
                g2.fillRoundRect(8 - i, 8 - i, w - (8 - i) * 2, h - (8 - i) * 2, arc + i, arc + i);
            }
            // fondo tarjeta
            g2.setColor(CARD_BG);
            g2.fillRoundRect(0, 0, w, h, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // Panel de fondo con gradiente vertical (verde)
    static class GradientPanel extends JPanel {
        private final Color top, bot;
        GradientPanel(Color top, Color bot) { this.top = top; this.bot = bot; setOpaque(true); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            GradientPaint gp = new GradientPaint(0, 0, top, 0, getHeight(), bot);
            g2.setPaint(gp);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // Borde redondeado + padding integrado para campos
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

    // Borde redondeado con sombra suave para las “cards” internas
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
                float alpha = 0.08f;
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2.fillRoundRect(x+2+i, y+4+i, w-4, h-6, arc, arc);
            }
            // borde sutil
            g2.setComposite(AlphaComposite.SrcOver);
            g2.setColor(border);
            g2.drawRoundRect(x+1, y+1, w-3, h-3, arc, arc);
            g2.dispose();
        }
    }

    /** Clip redondeado para cualquier JComponent sin cambiar su tipo ni su lógica. */
    static class RoundedClipUI extends LayerUI<JComponent> {
        private final int arc;
        RoundedClipUI(int arc) { this.arc = arc; }

        @Override
        public void paint(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Shape clip = new RoundRectangle2D.Float(0, 0, c.getWidth(), c.getHeight(), arc * 2f, arc * 2f);
            Shape old = g2.getClip();
            g2.setClip(clip);

            super.paint(g2, c);   // pinta normalmente el contenido (tu mainPanel + hijos)

            g2.setClip(old);
            g2.dispose();
        }
    }

    // ======== LÓGICA ORIGINAL (sin cambios) ========
    private void intentarLogin() {
        String usuario = (txtUsuario != null ? txtUsuario.getText().trim() : "");
        String password = leerPasswordDeCampo();

        if (usuario.isEmpty() || password.isEmpty()
                || "Usuario".equalsIgnoreCase(usuario)
                || "Contraseña".equalsIgnoreCase(password)) {
            JOptionPane.showMessageDialog(null, "Por favor completa todos los campos.");
            return;
        }

        Sesion s = autenticarUsuario(usuario, password);
        if (s != null) {

            JOptionPane.showMessageDialog(null, "Bienvenido " + usuario + " ✅");
            JFrame frameActual = (JFrame) SwingUtilities.getWindowAncestor(mainPanel);
            if (frameActual != null) frameActual.dispose();

            switch (s.rolId) {
                case 1 -> new PantallaAdmin(s.idUsuario);
                case 2 -> new pantallaCajero(s.idUsuario);
                case 3 -> new PantallaAsesor(s.idUsuario);
                case 4 -> new PantallaAsesor(s.idUsuario);
                case 5 -> new pantallaCajero(s.idUsuario);
                default -> JOptionPane.showMessageDialog(null, "Rol no soportado: " + s.rolId);
            }

        } else {
            JOptionPane.showMessageDialog(null, "Usuario o contraseña incorrectos");
        }
    }

    /** Intenta validar primero con password_hash (PBKDF2/BCrypt); si no existe o no coincide, cae a `contraseña` en texto plano. */
    private int obtenerRolIdUsuario(String usuario, String plainPassword) {
        String q1 = "SELECT password_hash, rol_id FROM Usuarios WHERE usuario = ?";
        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(q1)) {
            ps.setString(1, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String hash = rs.getString("password_hash");
                    int rol = rs.getInt("rol_id");
                    if (comprobarPassword(plainPassword, hash)) return rol;
                }
            }
        } catch (SQLException ex) {
            if (!esColumnaDesconocida(ex)) { manejarSQLException(ex); return -1; }
        }

        String q2 = "SELECT `contraseña` AS password_plano, rol_id FROM Usuarios WHERE usuario = ?";
        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(q2)) {
            ps.setString(1, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String plano = rs.getString("password_plano");
                    int rol = rs.getInt("rol_id");
                    if (plano != null && plainPassword.equals(plano)) return rol;
                }
            }
        } catch (SQLException ex) {
            manejarSQLException(ex);
        }
        return -1;
    }

    /** Lee el password sin importar si el campo es JPasswordField o JTextField. */
    private String leerPasswordDeCampo() {
        if (txtContrasena == null) return "";
        try {
            java.lang.reflect.Method m = txtContrasena.getClass().getMethod("getPassword");
            char[] chars = (char[]) m.invoke(txtContrasena);
            String p = new String(chars != null ? chars : new char[0]).trim();
            if (chars != null) java.util.Arrays.fill(chars, '\0');
            return p;
        } catch (NoSuchMethodException nsme) {
            try {
                java.lang.reflect.Method m2 = txtContrasena.getClass().getMethod("getText");
                Object r = m2.invoke(txtContrasena);
                return r != null ? r.toString().trim() : "";
            } catch (Exception e) {
                return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    /** Comprueba la contraseña: PBKDF2 (prefijo pbkdf2$), BCrypt si está disponible, o plano. */
    boolean comprobarPassword(String plain, String hashOrPlain) {
        if (hashOrPlain == null || hashOrPlain.isEmpty()) return false;

        // PBKDF2 (nuestro formato)
        if (hashOrPlain.startsWith("pbkdf2$")) {
            return Passwords.verify(plain.toCharArray(), hashOrPlain);
        }

        // BCrypt
        boolean pareceBCrypt = hashOrPlain.startsWith("$2a$") || hashOrPlain.startsWith("$2b$") || hashOrPlain.startsWith("$2y$");
        if (pareceBCrypt) {
            try {
                Class<?> c = Class.forName("org.mindrot.jbcrypt.BCrypt");
                java.lang.reflect.Method checkpw = c.getMethod("checkpw", String.class, String.class);
                Object ok = checkpw.invoke(null, plain, hashOrPlain);
                return (ok instanceof Boolean) && (Boolean) ok;
            } catch (ClassNotFoundException cnfe) {
                JOptionPane.showMessageDialog(null,
                        "Este usuario tiene hash BCrypt pero falta la librería BCrypt (org.mindrot:jbcrypt:0.4).");
                return false;
            } catch (Exception e) {
                e.printStackTrace(); return false;
            }
        }

        // Legado en claro
        return plain.equals(hashOrPlain);
    }

    private boolean abrirFramePorNombre(String className) {
        try {
            Class<?> cls = Class.forName(className);
            if (JFrame.class.isAssignableFrom(cls)) {
                Constructor<?> ctor = cls.getDeclaredConstructor();
                ctor.setAccessible(true);
                JFrame f = (JFrame) ctor.newInstance();
                if (!f.isVisible()) f.setVisible(true);
                return true;
            } else {
                Object o = cls.getDeclaredConstructor().newInstance();
                try { cls.getMethod("setVisible", boolean.class).invoke(o, true); return true; }
                catch (NoSuchMethodException ignored) { return true; }
            }
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "No se pudo abrir " + className + ": " + e.getMessage());
            return true;
        }
    }

    // ======== Placeholders ========
    private void setPlaceholder(JTextField textField, String placeholder) {
        if (textField == null) return;
        textField.setForeground(TEXT_MUTED);
        textField.setText(placeholder);
        textField.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (textField.getText().equals(placeholder)) {
                    textField.setText(""); textField.setForeground(TEXT_PRIMARY);
                }
            }
            @Override public void focusLost(FocusEvent e) {
                if (textField.getText().isEmpty()) {
                    textField.setForeground(TEXT_MUTED); textField.setText(placeholder);
                }
            }
        });
    }

    // ======== Utilidades SQL ========
    private boolean esColumnaDesconocida(SQLException ex) {
        return "42S22".equals(ex.getSQLState()) || ex.getErrorCode() == 1054;
    }
    private void manejarSQLException(SQLException ex) {
        String msg = ex.getMessage();
        if (msg != null && msg.contains("Communications link failure")) {
            JOptionPane.showMessageDialog(null, "No se pudo conectar a la base de datos.\n" +
                    "Verifica host/puerto/usuario/clave en db.properties y que MySQL esté activo.");
        } else if (msg != null && msg.contains("Access denied")) {
            JOptionPane.showMessageDialog(null, "Acceso denegado a MySQL.\n" +
                    "Usuario/contraseña o base de datos incorrectos en db.properties.");
        } else if (!esColumnaDesconocida(ex)) {
            JOptionPane.showMessageDialog(null, "Error SQL: " + msg);
        }
        ex.printStackTrace();
    }

    // ===== Fuente global helper =====
    private void setUIFont(Font f) {
        var keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object k = keys.nextElement();
            Object v = UIManager.get(k);
            if (v instanceof Font) UIManager.put(k, f);
        }
    }
}
