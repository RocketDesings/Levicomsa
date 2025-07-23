import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PantallaAsesor implements Refrescable {
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
    private JButton buscarButton;
    private JPanel panelBotones;
    private JPanel panelBusqueda;

    public PantallaAsesor() {
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
        cargarClientesDesdeBD();

        btnAgregarCliente.addActionListener(e -> {
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
        String[] columnas = {"Nombre", "Teléfono", "CURP", "Pensionado", "RFC", "Correo"};
        DefaultTableModel modelo = new DefaultTableModel(columnas, 0);
        tblAsesor.setModel(modelo);
    }

    public void mostrar() {
        pantalla.setVisible(true);
        cargarClientesDesdeBD();
    }

    public void cargarClientesDesdeBD() {
        String sql = "SELECT nombre, telefono, CURP, pensionado, RFC, correo FROM Clientes";

        try (Connection conn = JDBC.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            DefaultTableModel modelo = (DefaultTableModel) tblAsesor.getModel();
            modelo.setRowCount(0); // Limpiar tabla

            while (rs.next()) {
                String nombre = rs.getString("nombre");
                String telefono = rs.getString("telefono");
                String curp = rs.getString("CURP");
                boolean pensionadoBool = rs.getBoolean("pensionado");
                String pensionado = pensionadoBool ? "Sí" : "No";
                String rfc = rs.getString("RFC");
                String correo = rs.getString("correo");

                Object[] fila = {nombre, telefono, curp, pensionado, rfc, correo};
                modelo.addRow(fila);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(pantalla, "Error al cargar clientes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void refrescarDatos() {
        cargarClientesDesdeBD();
        pantalla.setVisible(true);
    }
}
