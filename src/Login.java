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

public class Login {
    private JPanel mainPanel;
    private JTextField txtUsuario;
    private JTextField txtContrasena; // si luego cambias a JPasswordField, leerPasswordDeCampo ya lo soporta
    private JButton btnIniciarSesion;
    private JButton btnSalir;
    private JPanel panelLogo;
    private JPanel panelInfo;
    private JLabel lblLogo;

    // Colores base (puedes ajustar a tu marca)
    private static final Color BG_GRADIENT_TOP = new Color(0x0F172A);  // azul muy oscuro
    private static final Color BG_GRADIENT_BOT = new Color(0x1E293B);  // slate
    private static final Color CARD_BG          = new Color(0xFFFFFF);
    private static final Color PRIMARY          = new Color(0x2563EB);  // azul
    private static final Color PRIMARY_HOVER    = new Color(0x1D4ED8);
    private static final Color PRIMARY_PRESSED  = new Color(0x1E40AF);
    private static final Color OUTLINE          = new Color(0x334155);  // slate-700
    private static final Color OUTLINE_HOVER    = new Color(0x1F2937);
    private static final Color TEXT_MUTED       = new Color(0x6B7280);
    private static final Color FIELD_BORDER     = new Color(0xCBD5E1);
    private static final Color FIELD_BORDER_FOC = new Color(0x2563EB);

    public Login() {
        // ---------- FRAME ----------
        JFrame loginFrame = new JFrame("Login");
        loginFrame.setUndecorated(true);
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Root con gradiente y tarjeta centrada
        GradientPanel root = new GradientPanel(BG_GRADIENT_TOP, BG_GRADIENT_BOT);
        root.setLayout(new GridBagLayout());

        // Tarjeta con sombra y esquinas redondeadas que envuelve tu mainPanel
        CardPanel card = new CardPanel();
        card.setLayout(new BorderLayout());
        if (mainPanel != null) {
            mainPanel.setOpaque(false); // dejamos que la tarjeta pinte el fondo
            card.add(mainPanel, BorderLayout.CENTER);
        }
        Insets pad = new Insets(24, 28, 24, 28);
        card.setBorder(new EmptyBorder(pad));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1; gbc.weighty = 1;
        gbc.fill = GridBagConstraints.NONE;
        root.add(card, gbc);

        loginFrame.setContentPane(root);
        loginFrame.pack();
        loginFrame.setLocationRelativeTo(null);
        loginFrame.setVisible(true);

        UiImages.setIcon(lblLogo, "/images/levicomsa.png", 250);

        // ---------- PANELES ----------
        if (panelLogo != null) {
            panelLogo.setOpaque(false);
            // línea divisoria más sutil
            panelLogo.setBorder(new MatteBorder(0, 0, 0, 1, new Color(0,0,0,35)));
        }
        if (panelInfo != null) {
            panelInfo.setOpaque(false);
        }

        // ---------- TIPOGRAFÍA ----------
        Font fTitle = new Font("Segoe UI", Font.BOLD, 22);
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
            btnIniciarSesion.addActionListener(e -> intentarLogin());
        }
        if (btnSalir != null) {
            styleExitButton(btnSalir);
            btnSalir.addActionListener(e -> System.exit(0));
        }
        if (txtContrasena != null) {
            txtContrasena.addActionListener(e -> intentarLogin());
        }
    }

    // Dentro de Login.java
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
                        registrarLastLogin(id);          // <<<<<< ACTUALIZA AQUÍ
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
                        registrarLastLogin(id);          // <<<<<< Y AQUÍ TAMBIÉN
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
            // No interrumpas el flujo de login si falla este update
            System.err.println("[Login] No se pudo actualizar last_login: " + e.getMessage());
        }
    }


    // ======== DISEÑO: helpers ========

    private void styleTextField(JTextField tf) {
        tf.setOpaque(true);
        tf.setBackground(Color.WHITE);
        tf.setForeground(Color.BLACK);
        tf.setCaretColor(Color.BLACK);
        tf.setBorder(new CompoundBorderRounded(FIELD_BORDER, 10, 1, new Insets(10, 12, 10, 12)));

        // borde reactivo al foco (sin tocar tu placeholder)
        tf.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                tf.setBorder(new CompoundBorderRounded(new Color(32,169,61), 10, 2, new Insets(10, 12, 10, 12)));
            }
            @Override public void focusLost(FocusEvent e) {
                tf.setBorder(new CompoundBorderRounded(FIELD_BORDER, 10, 1, new Insets(10, 12, 10, 12)));
            }
        });
    }

    private void stylePrimaryButton(JButton b) {
        Color ROJO_BASE    = new Color(0x20a93d); // rojo
        Color GRIS_HOVER   = new Color(0xD1D5DB); // gris al pasar el mouse
        Color GRIS_PRESSED = new Color(0x9CA3AF); // gris más oscuro al presionar

        // filled = true para que sea botón sólido
        b.setUI(new ModernButtonUI(ROJO_BASE, GRIS_HOVER, GRIS_PRESSED, Color.BLACK, 12, true));
        b.setForeground(Color.WHITE); // texto negro
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void styleOutlineButton(JButton b) {
        b.setUI(new ModernButtonUI(new Color(0,0,0,0), new Color(0,0,0,25), new Color(0,0,0,45), Color.WHITE, 12, false));
        b.setForeground(Color.WHITE);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    private void styleExitButton(JButton b) {
        Color ROJO_BASE    = new Color(0xEF4040); // rojo
        Color GRIS_HOVER   = new Color(0xD1D5DB); // gris al pasar el mouse
        Color GRIS_PRESSED = new Color(0x9CA3AF); // gris más oscuro al presionar

        // filled = true para que sea botón sólido
        b.setUI(new ModernButtonUI(ROJO_BASE, GRIS_HOVER, GRIS_PRESSED, Color.BLACK, 12, true));
        b.setForeground(Color.WHITE); // texto negro
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private boolean debeForzarCambio(int userId) {
        final String q = "SELECT must_change_password FROM Usuarios WHERE id=?";
        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(q)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        } catch (SQLException e) {
            return false;
        }
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

            // fondo
            if (filled || fill.getAlpha() > 0) {
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), arc, arc);
            }

            // borde sutil en outline
            if (!filled) {
                g2.setColor(new Color(255, 255, 255, 100));
                g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, arc, arc);
            }

            g2.dispose();
            // pinta el texto/icono encima
            super.paint(g, c);
        }
    }

    // Tarjeta con sombra y esquinas redondeadas
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

    // Panel de fondo con gradiente vertical
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

    // ======== LÓGICA ORIGINAL (sin cambios funcionales) ========

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

            // --- FORZAR CAMBIO DE CONTRASEÑA SI EL ADMIN LO MARCÓ ---
            if (debeForzarCambio(s.idUsuario)) {
                new CambiarContrasenaDialog(s.idUsuario, true).setVisible(true);
                // Si el usuario cerró el diálogo sin cambiar, sigue marcado → no lo dejes pasar
                if (debeForzarCambio(s.idUsuario)) return;
            }

            JOptionPane.showMessageDialog(null, "Bienvenido " + usuario + " ✅");
            JFrame frameActual = (JFrame) SwingUtilities.getWindowAncestor(mainPanel);
            if (frameActual != null) frameActual.dispose();

            switch (s.rolId) {
                case 1 -> new PantallaAdmin(s.idUsuario);
                case 2 -> new pantallaCajero(s.idUsuario);
                case 3 -> new PantallaAsesor(s.idUsuario);
                default -> JOptionPane.showMessageDialog(null, "Rol no soportado: " + s.rolId);
            }
        } else {
            JOptionPane.showMessageDialog(null, "Usuario o contraseña incorrectos");
        }


    }

    /** Intenta validar primero con password_hash (BCrypt); si no existe o no coincide, cae a `contraseña` en texto plano. */
    private int obtenerRolIdUsuario(String usuario, String plainPassword) {
        // 1) password_hash
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
            if (!esColumnaDesconocida(ex)) {
                manejarSQLException(ex);
                return -1;
            }
        }

        // 2) legado: columna `contraseña`
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

    /** Comprueba la contraseña: si parece BCrypt y la clase está disponible, la valida; si no, compara en plano. */
    boolean comprobarPassword(String plain, String hashOrPlain) {
        if (hashOrPlain == null || hashOrPlain.isEmpty()) return false;

        // 1) PBKDF2 (nuestro formato)
        if (hashOrPlain.startsWith("pbkdf2$")) {
            return Passwords.verify(plain.toCharArray(), hashOrPlain);
        }

        // 2) BCrypt (si tienes usuarios antiguos con BCrypt)
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

        // 3) Legado en claro
        return plain.equals(hashOrPlain);
    }


    /** Abre la pantalla correspondiente al rol (soporta PantallaCajero o pantallaCajero). */
    private void abrirPantallaPorRol(int rol) {
        switch (rol) {
            case 1 -> { abrirFramePorNombre("PantallaAdmin"); }
            case 2 -> { if (!abrirFramePorNombre("PantallaCajero")) abrirFramePorNombre("pantallaCajero"); }
            case 3 -> { abrirFramePorNombre("PantallaAsesor"); }
            case 4 -> { abrirFramePorNombre("PantallaAdmin"); }
            default -> JOptionPane.showMessageDialog(null, "Rol no soportado: " + rol);
        }
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

    // ======== Placeholders (sin cambios) ========
    private void setPlaceholder(JTextField textField, String placeholder) {
        if (textField == null) return;
        textField.setForeground(TEXT_MUTED);
        textField.setText(placeholder);
        textField.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (textField.getText().equals(placeholder)) {
                    textField.setText(""); textField.setForeground(Color.BLACK);
                }
            }
            @Override public void focusLost(FocusEvent e) {
                if (textField.getText().isEmpty()) {
                    textField.setForeground(TEXT_MUTED); textField.setText(placeholder);
                }
            }
        });
    }

    // ======== Utilidades de SQL (sin cambios) ========
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
}
