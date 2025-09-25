import javax.swing.*;

public class pantallaCajero {
    private JPanel panelMain;
    private JLabel lblImgCajero;
    private JLabel lblLogo;
    private JTextField txtMontoInicial;
    private JButton tbnIngresar;
    private JButton tbnCerrarSesion;

    public pantallaCajero() {
        JFrame cajeroFrame = new JFrame("Pantalla Cajero");
        cajeroFrame.setUndecorated(true);
        cajeroFrame.setContentPane(panelMain);
        cajeroFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        cajeroFrame.pack();
        cajeroFrame.setLocationRelativeTo(null);
        cajeroFrame.setVisible(true);
        tbnIngresar.addActionListener(e -> {
            String monto = txtMontoInicial.getText().trim();
            if (monto.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Por favor ingresa un monto inicial.");
            } else {
                JOptionPane.showMessageDialog(null, "Monto ingresado: " + monto);
                JFrame frameActual = (JFrame) SwingUtilities.getWindowAncestor(panelMain);
                frameActual.dispose();
                new InterfazCajero();
            }

        });

        tbnCerrarSesion.addActionListener(e -> {
            JFrame frameActual = (JFrame) SwingUtilities.getWindowAncestor(panelMain);
            frameActual.dispose();
            new Login();
        });
    }
}


