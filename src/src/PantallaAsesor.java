import javax.swing.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.table.DefaultTableModel;

public class PantallaAsesor {
    private JFrame pantalla;
    private JPanel panelMain;
    private JTable tblAsesor;
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
    private JTable tblASesor;

    public PantallaAsesor() {
        // btnModificarCliente.addActionListener(e -> modificarCliente());
        // btnCobrar.addActionListener(e -> cobrar());
        btnSalir.addActionListener(e -> mostrarAlertaCerrarSesion());

        pantalla = new JFrame("Pantalla Asesor");
        pantalla.setUndecorated(true);
        pantalla.setContentPane(panelMain);
        pantalla.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pantalla.pack();
        pantalla.setLocationRelativeTo(null);
        pantalla.setVisible(true);

        iniciarReloj();
        configurarTabla();

        btnAgregarCliente.addActionListener(e -> {
            pantalla.setVisible(false);
            new FormularioAgregarCliente(this);
        });
    }

    private void iniciarReloj() {
        SimpleDateFormat formato = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        Timer timer = new Timer(1000, e -> {
            String horaActual = formato.format(new Date());
            lblHora.setText(horaActual);
        });
        timer.start();
    }

    private void mostrarAlertaCerrarSesion() {
        AlertaCerrarSesion alerta = new AlertaCerrarSesion(pantalla);
    }

    public void configurarTabla() {
        String[] columnas = {"id_cliente", "Nombre", "Teléfono", "CURP", "pensionado", "RFC", "correo"};
        DefaultTableModel modelo = new DefaultTableModel(columnas, 0); // 0 filas iniciales
        tblAsesor.setModel(modelo);
    }

    public void mostrar() {
        pantalla.setVisible(true); // Método para mostrar esta ventana desde afuera
    }
}
