import javax.swing.*;

public class AlertaCerrarSesion extends JFrame {
    private JPanel panelMain;
    private JFrame pantallaAsesor;
    private JButton btnConfirmar;
    private JButton regresarButton;
    private JLabel lblTexto;
    private JLabel lblIcono;
    private JPanel panelBotones;

    public AlertaCerrarSesion(JFrame pantallaAsesor) {
        this.pantallaAsesor = pantallaAsesor;
        JFrame alerta = new JFrame("Alerta Cerrar Sesión");
        alerta.setUndecorated(true);
        alerta.setContentPane(panelMain);
        alerta.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        alerta.pack();
        alerta.setLocationRelativeTo(null); // Centra la ventana
        alerta.setVisible(true);

        btnConfirmar.addActionListener(e -> {
            pantallaAsesor.dispose();
            alerta.dispose();
            Login login = new Login(); // Abre la ventana de login
        });

        regresarButton.addActionListener(e -> {
            alerta.dispose();
        });
    }
}
