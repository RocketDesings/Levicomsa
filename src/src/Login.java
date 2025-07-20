import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.sql.*;

public class Login {
    private JPanel mainPanel;
    private JTextField txtUsuario;
    private JTextField txtContrasena;
    private JButton btnIniciarSesion;
    private JButton btnSalir;

    public Login() {
        // Inicializar el JFrame
        JFrame loginFrame = new JFrame("Login");
        loginFrame.setUndecorated(true);
        loginFrame.setContentPane(mainPanel);
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.pack();
        loginFrame.setLocationRelativeTo(null);
        loginFrame.setVisible(true);
        mainPanel.requestFocusInWindow();

        // Configurar el placeholder para txtUsuario
        setPlaceholder(txtUsuario, "Usuario");

        // Configurar el placeholder para txtContrasena
        setPlaceholder(txtContrasena, "Contraseña");

        // Acciones de los botones
        btnIniciarSesion.addActionListener(e -> {
            System.out.println("Bienvenido @a Redirigiendo...");
        });
        btnSalir.addActionListener(e -> {
            System.exit(0);
        });

// Evento del botón
        btnIniciarSesion.addActionListener(e -> {
            String user = txtUsuario.getText().trim();
            String pass = txtContrasena.getText().trim();

            if (user.equals("Usuario") || pass.equals("Contraseña") || user.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Por favor completa todos los campos.");
                return;
            }

            int rolId = obtenerRolIdUsuario(user, pass);
            if (rolId > 0) {
                JOptionPane.showMessageDialog(null, "Bienvenido " + user + " ✅");
                JFrame frameActual = (JFrame) SwingUtilities.getWindowAncestor(mainPanel);
                frameActual.dispose();

                switch (rolId) {
                    case 3:
                        new PantallaAsesor();
                        break;
                    case 1:
                        new pantallaCajero();
                        break;
                    case 2:
                        new PantallaAdmin();
                        break;
                    default:
                        JOptionPane.showMessageDialog(null, "Rol desconocido.");
                }
            } else {
                JOptionPane.showMessageDialog(null, "Usuario o contraseña incorrectos ❌");
            }
        });
    }
    // Metodo para establecer el placeholder en un JTextField
    private void setPlaceholder(JTextField textField, String placeholder) {
        textField.setForeground(Color.GRAY); // Color del texto del placeholder
        textField.setText(placeholder); // Establecer el texto del placeholder

        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (textField.getText().equals(placeholder)) {
                    textField.setText("");
                    textField.setForeground(Color.BLACK); // Cambiar el color del texto al foco
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (textField.getText().isEmpty()) {
                    textField.setForeground(Color.GRAY); // Color del texto del placeholder
                    textField.setText(placeholder); // Restablecer el placeholder
                }
            }
        });
    }//fin del metodo setPlaceholder

    private int obtenerRolIdUsuario(String usuario, String contraseña) {
        String sql = "SELECT rol_id FROM Usuarios WHERE usuario = ? AND contraseña = ?";
        try (Connection conn = JDBC.obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, usuario);
            stmt.setString(2, contraseña);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("rol_id");
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Error al conectar con la base de datos.");
        }
        return -1; // -1 indica error o usuario no encontrado
    }
}// fin de la clase Login
