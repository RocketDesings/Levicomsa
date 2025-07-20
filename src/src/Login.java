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

        //BOTON INICIAR SESION
        btnIniciarSesion.addActionListener(e -> {
            String user = txtUsuario.getText().trim();
            String pass = txtContrasena.getText().trim();

            // Evita validar si se dejaron los placeholders
            if (user.equals("Usuario") || pass.equals("Contraseña") || user.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Por favor completa todos los campos.");
                return;
            }

            if (validarCredenciales(user, pass)) {
                JOptionPane.showMessageDialog(null, "Bienvenido " + user + " ✅");
                // Aquí puedes redirigir al menú principal o siguiente ventana
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

    // Metodo para validar las credenciales del usuario
    private boolean validarCredenciales(String usuario, String contraseña) {
        String sql = "SELECT * FROM Usuarios WHERE usuario = ? AND contraseña = ?";

        try (Connection conn = JDBC.obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, usuario);
            stmt.setString(2, contraseña);

            ResultSet rs = stmt.executeQuery();
            return rs.next(); // Si encuentra un registro, el login es válido

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Error al conectar con la base de datos.");
            return false;
        }
    }
}// fin de la clase Login
