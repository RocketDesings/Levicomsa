import javax.swing.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PantallaAsesor {
    private JPanel panelMain;
    private JTable table1;
    private JButton btnAgregarCliente;
    private JButton btnModificarCliente;
    private JButton btnCobrar;
    private JButton btnSalir;
    private JLabel lblNombre;
    private JLabel lblImagen;
    private JLabel lblHora;
    private JLabel lblIcono;
    private JLabel lblTitulo;
    private JPanel panelInfo;
    private JLabel lblSlogan;
    private JLabel lblSucursal;
    private JComboBox comboBox1;
    private JButton button1;
    private JPanel panelBotones;
    private JPanel panelBusqueda;

    public PantallaAsesor() {
        //btnAgregarCliente.addActionListener(e -> agregarCliente());
        //btnModificarCliente.addActionListener(e -> modificarCliente());
        //btnCobrar.addActionListener(e -> cobrar());
        //btnSalir.addActionListener(e -> salir());

        horaActual();
    }

    private void horaActual() {
        Thread reloj = new Thread(() -> {
            SimpleDateFormat formato = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            while (true) {
                String horaActual = formato.format(new Date());
                SwingUtilities.invokeLater(() -> lblHora.setText(horaActual));
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        reloj.setDaemon(true);
        reloj.start();
    }
}
