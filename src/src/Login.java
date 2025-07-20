import javax.swing.*;

public class Login {
    private JPanel mainPanel;
    private JTextField txtusuario;
    private JTextField txtContrasena;
    private JButton btnIniciarSesion;

    public Login() {
        JFrame loginFrame = new JFrame("Login");
        loginFrame.setContentPane(mainPanel);
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.pack();
        loginFrame.setLocationRelativeTo(null); // Centra la ventana
        loginFrame.setVisible(true);

        //btnIniciarSesion.addActionListener(e -> iniciar());
    }
}
