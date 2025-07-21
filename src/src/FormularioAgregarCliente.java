import javax.swing.*;

public class FormularioAgregarCliente extends JFrame {
    public JPanel mainPanel;
    private JTextField txtNombre;
    private JTextField txtTelefono;
    private JTextField txtCurp;
    private JCheckBox chechPensionado;
    private JTextField txtRFC;
    private JTextField txtCorreo;
    public JButton btnAgregar;
    public JButton btnCancelar;
    private PantallaAsesor pantallaPrincipal;

    public FormularioAgregarCliente(PantallaAsesor pantallaPrincipal) {
        this.pantallaPrincipal = pantallaPrincipal; // ASIGNACIÓN CORRECTA

        setTitle("Agregar Cliente");
        setContentPane(mainPanel);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        btnAgregar.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Cliente agregado correctamente.");

        });

        btnCancelar.addActionListener(e -> {
            dispose();
            pantallaPrincipal.mostrar();
        });
    }
}
