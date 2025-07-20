import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

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
}// fin de la clase Login
