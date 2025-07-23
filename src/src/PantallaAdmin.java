import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PantallaAdmin implements Refrescable {
    private JFrame pantalla;
    private JPanel panelMain;
    private JTable table1;
    private JButton btnAgregarCliente;
    private JButton btnModificarCliente;
    private JButton btnCobrar;
    private JButton btnSalir;
    private JLabel lblHora;
    private JLabel lblImagen;
    private JLabel lblNombre;
    private JLabel lblTitulo;
    private JLabel lblSlogan;
    private JLabel lblIcono;
    private JLabel lblSucursal;
    private JPanel panelInfo;
    private JComboBox comboBox1;
    private JPanel panelBotones;
    private JPanel panelBusqueda;
    private JButton btnEliminar;
    private JTextField textField1;

    private AutoActualizarTabla autoActualizador; // NUEVO

    public PantallaAdmin() {
        pantalla = new JFrame("Pantalla Admin");
        pantalla.setContentPane(panelMain);
        pantalla.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pantalla.pack();
        pantalla.setLocationRelativeTo(null);
        pantalla.setVisible(true);

        iniciarReloj();
        configurarTabla();
        cargarClientesDesdeBD();

        // Inicia autoactualización cada 30 segundos
        autoActualizador = new AutoActualizarTabla(this::cargarClientesDesdeBD, 5000);
        autoActualizador.iniciar();

        btnAgregarCliente.addActionListener(e -> {
            new FormularioAgregarCliente(this);
        });

        btnEliminar.addActionListener(e -> {
            new SeleccionarCliente();
        });

        btnSalir.addActionListener(e -> {
            autoActualizador.detener(); // Detiene el hilo si cierra
            System.exit(0);
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

    public void configurarTabla() {
        String[] columnas = {"Nombre", "Teléfono", "CURP", "Pensionado", "RFC", "Correo"};
        DefaultTableModel modelo = new DefaultTableModel(columnas, 0);
        table1.setModel(modelo);
    }

    public void cargarClientesDesdeBD() {
        String sql = "SELECT nombre, telefono, CURP, pensionado, RFC, correo FROM Clientes";

        try (Connection conn = JDBC.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            DefaultTableModel modelo = (DefaultTableModel) table1.getModel();
            modelo.setRowCount(0);

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

    public void mostrar() {
        pantalla.setVisible(true);
        cargarClientesDesdeBD();
    }

    @Override
    public void refrescarDatos() {
        cargarClientesDesdeBD();
        pantalla.setVisible(true);
    }
}
