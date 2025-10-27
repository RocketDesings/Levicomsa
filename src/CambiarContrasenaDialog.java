// CambiarContrasenaDialog.java
import javax.swing.*;
import java.awt.*;
import java.sql.*;

public class CambiarContrasenaDialog extends JDialog {
    private final int usuarioId;
    private final boolean forzado; // true: no pide "actual"
    private JPasswordField txtActual, txtNueva, txtRepite;

    public CambiarContrasenaDialog(int usuarioId, boolean forzado) {
        super((Frame) null, forzado ? "Debes cambiar tu contraseña" : "Cambiar contraseña", true);
        this.usuarioId = usuarioId;
        this.forzado = forzado;
        buildUI();
    }

    private void buildUI() {
        txtActual = new JPasswordField();
        txtNueva  = new JPasswordField();
        txtRepite = new JPasswordField();

        JPanel p = new JPanel(new GridLayout(0,1,8,8));
        if (!forzado) p.add(fila("Contraseña actual:", txtActual));
        p.add(fila("Nueva contraseña:", txtNueva));
        p.add(fila("Repetir contraseña:", txtRepite));

        JButton ok = new JButton("Guardar");
        JButton cancel = new JButton(forzado ? "Salir" : "Cancelar");
        ok.addActionListener(e -> onSave());
        cancel.addActionListener(e -> {
            if (forzado) System.exit(0); // no entra si no cambia
            dispose();
        });

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(cancel); south.add(ok);

        getContentPane().add(p, BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);
        setSize(380, 240);
        setLocationRelativeTo(null);
    }

    private JPanel fila(String label, JComponent field) {
        JPanel r = new JPanel(new BorderLayout(8,8));
        r.add(new JLabel(label), BorderLayout.WEST);
        r.add(field, BorderLayout.CENTER);
        return r;
    }

    private void onSave() {
        char[] nueva = txtNueva.getPassword();
        char[] rep   = txtRepite.getPassword();

        if (nueva.length < 6) { msg("La nueva contraseña debe tener al menos 6 caracteres."); return; }
        if (!java.util.Arrays.equals(nueva, rep)) { msg("Las contraseñas no coinciden."); return; }

        if (!forzado) {
            char[] actual = txtActual.getPassword();
            if (!validarActual(actual)) { msg("La contraseña actual no coincide."); return; }
        }

        String hash = Passwords.hash(nueva);
        final String up = "UPDATE Usuarios SET password_hash=?, `contraseña`='', must_change_password=0, last_password_change=NOW() WHERE id=?";
        try (Connection con = cn(); PreparedStatement ps = con.prepareStatement(up)) {
            ps.setString(1, hash);
            ps.setInt(2, usuarioId);
            ps.executeUpdate();
            msg("¡Contraseña actualizada!");
            dispose();
        } catch (SQLException ex) {
            msg("Error BD: " + ex.getMessage());
        }
    }

    private boolean validarActual(char[] actual) {
        final String q = "SELECT password_hash, `contraseña` FROM Usuarios WHERE id=?";
        try (Connection con = cn(); PreparedStatement ps = con.prepareStatement(q)) {
            ps.setInt(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                String hash = rs.getString("password_hash");
                String plano = rs.getString("contraseña");
                String actualStr = new String(actual);

                // PBKDF2 (nuestro formato)
                if (hash != null && hash.startsWith("pbkdf2$") && Passwords.verify(actualStr.toCharArray(), hash)) return true;

                // BCrypt (si tuvieras usuarios viejos con BCrypt)
                if (hash != null && (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"))) {
                    try {
                        Class<?> c = Class.forName("org.mindrot.jbcrypt.BCrypt");
                        java.lang.reflect.Method checkpw = c.getMethod("checkpw", String.class, String.class);
                        Object ok = checkpw.invoke(null, actualStr, hash);
                        if (ok instanceof Boolean && (Boolean) ok) return true;
                    } catch (ClassNotFoundException ignore) { /* si no está la lib, se omite */ }
                    catch (Exception e) { e.printStackTrace(); }
                }

                // Legado en claro
                return plano != null && actualStr.equals(plano);
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private Connection cn() throws SQLException {
        try {
            // intenta DB.getConnection()
            java.lang.reflect.Method m = DB.class.getMethod("getConnection");
            return (Connection) m.invoke(null);
        } catch (NoSuchMethodException nsme) {
            try {
                // fallback a DB.get()
                java.lang.reflect.Method m2 = DB.class.getMethod("get");
                return (Connection) m2.invoke(null);
            } catch (Exception inner) {
                throw new SQLException("No encontré DB.getConnection() ni DB.get()", inner);
            }
        } catch (Exception e) {
            throw new SQLException("Error obteniendo conexión", e);
        }
    }

    private void msg(String s){ JOptionPane.showMessageDialog(this, s); }
}
