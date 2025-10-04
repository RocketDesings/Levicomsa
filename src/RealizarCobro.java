import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;
import java.util.List;

/**
 * RealizarCobro (.form)
 * - panelMain: contenedor principal
 * - tblTablaCobros: lista de cobros pendientes (como en InterfazCajero)
 * - tblObjetosTotal: items (detalle de cobros agregados + extras)
 * - cmbServiciosExtra: servicios de categoria_id=3
 * - cmbMetodoPago: métodos de pago desde metodos_pago (activo=1)
 * - txtCantidad, txtCobro, txtCambio, lblTotal
 * - btnAnadirObjetoExtra, btnCobrar, btnCancelarButton
 */
public class RealizarCobro {

    // === Componentes del .form ===
    private JPanel panelMain;
    private JPanel panelBotones;
    private JPanel panelExtra;
    private JPanel panelTabla;
    private JScrollPane scrTabla;
    private JTable tblTablaCobros;        // lista de pendientes
    private JButton btnCobrar;            // finaliza
    private JButton btnCancelarButton;    // cierra
    private JPanel panelLabel;
    private JPanel panelAñadir;
    private JPanel panelInfoPago;
    private JPanel panelAgregarServicios;
    private JPanel panelRapidos;
    private JTextField txtCantidad;
    private JComboBox<ServicioItem> cmbServiciosExtra; // categoría 3
    private JPanel panelInfoPago2;
    private JPanel panelObjetos;
    private JComboBox<MetodoPagoItem> cmbMetodoPago;   // metodos_pago
    private JTextField txtCobro;         // recibido
    private JLabel lblCambio;
    private JLabel lblRecibido;
    private JLabel lblMetodoPago;
    private JTextField txtCambio;        // cambio (auto)
    private JScrollPane scrObjetos;
    private JTable tblObjetosTotal;      // detalle + extras
    private JLabel lblTituloTotal;
    private JLabel lblTotal;             // suma
    private JPanel panelTotal;
    private JButton btnAnadirObjetoExtra;
    private JLabel lblTituloTabla;

    // === Estado ===
    private final int sucursalId;
    private final int usuarioId;

    // Ahora manejamos VARIOS cobros en el ticket:
    private final LinkedHashSet<Integer> cobrosAgregados = new LinkedHashSet<>();
    private final Map<Integer, BigDecimal> totalPorCobro = new HashMap<>(); // se recalcula al vuelo

    private DefaultTableModel modelCobros;
    private DefaultTableModel modelItems; // incluye columna oculta servicio_id y cobro_id
    private TableRowSorter<DefaultTableModel> sorterCobros;

    private final List<Extra> extrasPendientes = new ArrayList<>();

    // evita 2 ventanas abiertas
    private static WeakReference<JDialog> OPEN = new WeakReference<>(null);
    private JDialog dialog;

    // ===== Public API =====
    public static void mostrar(Window owner, int sucursalId, int usuarioId) {
        JDialog opened = OPEN.get();
        if (opened != null && opened.isShowing()) {
            opened.toFront(); opened.requestFocus();
            return;
        }

        RealizarCobro form = new RealizarCobro(sucursalId, usuarioId);
        JDialog d = new JDialog(owner, "Realizar cobro", Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(form.panelMain);
        d.setMinimumSize(new Dimension(1100, 650));
        d.setLocationRelativeTo(owner);

        d.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e)  { OPEN.clear(); }
            @Override public void windowClosing(java.awt.event.WindowEvent e) { OPEN.clear(); }
        });

        form.dialog = d;
        OPEN = new WeakReference<>(d);

        form.init();                // ← se abre sin exigir selección previa
        d.setVisible(true);
    }

    // ====== Constructores ======
    public RealizarCobro(int sucursalId, int usuarioId) {
        this.sucursalId = sucursalId;
        this.usuarioId  = usuarioId;
    }
    public RealizarCobro() { this(-1, -1); } // ctor vacío para el GUI builder si hiciera falta

    // ====== Inicialización UI / datos ======
    private void init() {
        construirModelos();
        cablearEventos();
        cargarCobrosPendientes();
        cargarServiciosCategoria3();
        cargarMetodosPago();
        actualizarTotalYCambio();
    }

    private void construirModelos() {
        // Cobros pendientes (como en InterfazCajero)
        modelCobros = new DefaultTableModel(
                new Object[]{"ID", "Fecha", "Cliente", "Total", "Notas", "Registró"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return switch (c) {
                    case 0 -> Integer.class;
                    default -> Object.class;
                };
            }
        };
        tblTablaCobros.setModel(modelCobros);
        sorterCobros = new TableRowSorter<>(modelCobros);
        tblTablaCobros.setRowSorter(sorterCobros);
        tblTablaCobros.setRowHeight(26);
        tblTablaCobros.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        if (lblTituloTabla != null) lblTituloTabla.setText("Cobros pendientes");

        // Detalle + extras (agregamos col oculta: servicio_id y cobro_id)
        modelItems = new DefaultTableModel(
                new Object[]{"Concepto", "Cant.", "P.Unit", "Subtotal", "servicio_id", "cobro_id"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return switch (c) {
                    case 1 -> Integer.class;
                    case 2,3 -> BigDecimal.class;
                    case 4,5 -> Integer.class;
                    default -> String.class;
                };
            }
        };
        tblObjetosTotal.setModel(modelItems);
        tblObjetosTotal.setRowHeight(24);

        // ocultar servicio_id y cobro_id
        ocultarColumna(tblObjetosTotal, 4);
        ocultarColumna(tblObjetosTotal, 5);

        // render dinero
        var moneyR = new javax.swing.table.DefaultTableCellRenderer() {
            @Override protected void setValue(Object v) {
                if (v instanceof BigDecimal bd) {
                    setHorizontalAlignment(RIGHT);
                    setText("$ " + bd.setScale(2, RoundingMode.HALF_UP).toPlainString());
                } else super.setValue(v);
            }
        };
        tblObjetosTotal.getColumnModel().getColumn(2).setCellRenderer(moneyR);
        tblObjetosTotal.getColumnModel().getColumn(3).setCellRenderer(moneyR);

        if (txtCantidad != null) txtCantidad.setText("1");
        if (lblTituloTotal != null) lblTituloTotal.setText("Resumen de ítems (0 cobros)");
        if (lblTotal != null) lblTotal.setText("$ 0.00");
        if (txtCambio != null) { txtCambio.setEditable(false); txtCambio.setText("0.00"); }
    }

    private static void ocultarColumna(JTable t, int idx) {
        t.getColumnModel().getColumn(idx).setMinWidth(0);
        t.getColumnModel().getColumn(idx).setMaxWidth(0);
        t.getColumnModel().getColumn(idx).setPreferredWidth(0);
    }

    private void cablearEventos() {
        // ENTER en la tabla superior agrega todas las seleccionadas
        tblTablaCobros.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("ENTER"), "add-selected");
        tblTablaCobros.getActionMap().put("add-selected", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { agregarCobrosSeleccionados(); }
        });

        // Doble click también agrega
        tblTablaCobros.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) agregarCobrosSeleccionados();
            }
        });

        // agregar extra (requiere al menos UN cobro agregado al ticket)
        if (btnAnadirObjetoExtra != null) {
            btnAnadirObjetoExtra.addActionListener(e -> agregarExtra());
        }

        // cambio en recibido
        if (txtCobro != null) {
            txtCobro.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { actualizarTotalYCambio(); }
                public void removeUpdate(DocumentEvent e) { actualizarTotalYCambio(); }
                public void changedUpdate(DocumentEvent e) { actualizarTotalYCambio(); }
            });
        }

        // cobrar
        if (btnCobrar != null) btnCobrar.addActionListener(e -> onCobrar());

        // cancelar
        if (btnCancelarButton != null) btnCancelarButton.addActionListener(e -> dialog.dispose());
    }

    // ====== Carga de datos ======
    private void cargarCobrosPendientes() {
        modelCobros.setRowCount(0);
        String base = """
            SELECT c.id, c.fecha, cl.nombre AS cliente,
                   COALESCE(c.total,0) AS total, c.notas,
                   COALESCE(u.nombre,u.usuario) AS registro
            FROM cobros c
            JOIN Clientes cl ON cl.id = c.cliente_id
            JOIN Usuarios u  ON u.id  = c.usuario_id
            WHERE c.estado='pendiente'
            """;
        String sql = (sucursalId > 0) ? base + " AND c.sucursal_id=? ORDER BY c.fecha DESC"
                : base + " ORDER BY c.fecha DESC";
        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            if (sucursalId > 0) ps.setInt(1, sucursalId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    Timestamp ts = rs.getTimestamp("fecha");
                    String fecha = (ts==null) ? "" : new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(ts);
                    String cliente = rs.getString("cliente");
                    BigDecimal total = rs.getBigDecimal("total"); if (total==null) total = BigDecimal.ZERO;
                    String notas = rs.getString("notas");
                    String reg   = rs.getString("registro");
                    modelCobros.addRow(new Object[]{id, fecha, cliente, "$ " + total.setScale(2, RoundingMode.HALF_UP), notas, reg});
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(dialog, "No se pudieron cargar cobros:\n" + ex.getMessage());
        }
    }

    // Agrega todas las filas seleccionadas de la tabla superior al ticket (pueden ser varias)
    private void agregarCobrosSeleccionados() {
        int[] viewRows = tblTablaCobros.getSelectedRows();
        if (viewRows == null || viewRows.length == 0) {
            JOptionPane.showMessageDialog(dialog, "Selecciona uno o varios cobros y presiona ENTER (o doble click).");
            return;
        }
        for (int vr : viewRows) {
            int mr = tblTablaCobros.convertRowIndexToModel(vr);
            Integer cobroId = (Integer) modelCobros.getValueAt(mr, 0);
            if (cobroId != null && !cobrosAgregados.contains(cobroId)) {
                agregarCobroAlTicket(cobroId);
            }
        }
        actualizarTituloResumen();
        actualizarTotalYCambio();
    }

    private void agregarCobroAlTicket(int cobroId) {
        // Carga su detalle (sin limpiar) y etiqueta cada fila con ese cobro_id
        final String q = """
            SELECT d.servicio_id, COALESCE(s.nombre,'(Sin servicio)') AS concepto,
                   d.cantidad, d.precio_unit
            FROM cobro_detalle d
            LEFT JOIN servicios s ON s.id = d.servicio_id
            WHERE d.cobro_id = ?
            ORDER BY d.id
            """;
        BigDecimal total = BigDecimal.ZERO;
        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(q)) {
            ps.setInt(1, cobroId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int sid          = rs.getInt("servicio_id");
                    String concepto  = rs.getString("concepto");
                    int cant         = Math.max(1, rs.getInt("cantidad"));
                    BigDecimal pu    = rs.getBigDecimal("precio_unit"); if (pu==null) pu = BigDecimal.ZERO;
                    BigDecimal sub   = pu.multiply(BigDecimal.valueOf(cant));
                    modelItems.addRow(new Object[]{concepto, cant, pu, sub, sid, cobroId});
                    total = total.add(sub);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(dialog, "No se pudo cargar el detalle del cobro #" + cobroId + ":\n" + ex.getMessage());
            return;
        }
        cobrosAgregados.add(cobroId);
        totalPorCobro.put(cobroId, total);
    }

    private void actualizarTituloResumen() {
        if (lblTituloTotal != null) {
            lblTituloTotal.setText("Resumen de ítems (" + cobrosAgregados.size() + " cobro" +
                    (cobrosAgregados.size()==1 ? ")" : "s)"));
        }
    }

    private void limpiarDetalleYSeleccion() {
        extrasPendientes.clear();
        cobrosAgregados.clear();
        totalPorCobro.clear();
        modelItems.setRowCount(0);
        actualizarTituloResumen();
        actualizarTotalYCambio();
    }

    private void cargarServiciosCategoria3() {
        DefaultComboBoxModel<ServicioItem> model = new DefaultComboBoxModel<>();
        final String sql = "SELECT id, nombre, precio FROM servicios WHERE activo=1 AND categoria_id=3 ORDER BY nombre";
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                model.addElement(new ServicioItem(
                        rs.getInt("id"),
                        rs.getString("nombre"),
                        rs.getBigDecimal("precio")
                ));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(dialog, "No se pudieron cargar servicios extra:\n" + ex.getMessage());
        }
        cmbServiciosExtra.setModel(model);
    }

    private void cargarMetodosPago() {
        DefaultComboBoxModel<MetodoPagoItem> model = new DefaultComboBoxModel<>();
        final String sql = "SELECT id, nombre FROM metodos_pago WHERE activo=1 ORDER BY id";
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) model.addElement(new MetodoPagoItem(rs.getInt(1), rs.getString(2)));
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(dialog, "No se pudieron cargar métodos de pago:\n" + ex.getMessage());
        }
        cmbMetodoPago.setModel(model);
        if (lblMetodoPago != null) lblMetodoPago.setText("Método de pago:");
        if (lblRecibido  != null)  lblRecibido.setText("Recibido:");
    }

    // ====== Acciones UI ======
    private void agregarExtra() {
        if (cobrosAgregados.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "Primero agrega al menos un cobro al ticket (selecciona y presiona ENTER).");
            return;
        }
        ServicioItem s = (ServicioItem) cmbServiciosExtra.getSelectedItem();
        if (s == null) { JOptionPane.showMessageDialog(dialog, "Selecciona un servicio extra."); return; }

        int cant;
        try {
            String raw = txtCantidad.getText().trim();
            cant = Integer.parseInt(raw);
            if (cant <= 0) throw new NumberFormatException();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(dialog, "Cantidad inválida.");
            return;
        }

        // Asignamos extras al PRIMER cobro agregado (política simple)
        int cobroDestino = cobrosAgregados.iterator().next();

        BigDecimal sub = s.precio.multiply(BigDecimal.valueOf(cant));
        modelItems.addRow(new Object[]{s.nombre + " (extra)", cant, s.precio, sub, s.id, cobroDestino});
        extrasPendientes.add(new Extra(s.id, cant, s.precio));

        // sumamos al total del cobro destino
        totalPorCobro.put(cobroDestino, totalPorCobro.getOrDefault(cobroDestino, BigDecimal.ZERO).add(sub));

        txtCantidad.setText("1");
        if (cmbServiciosExtra.getItemCount() > 0) cmbServiciosExtra.setSelectedIndex(0);

        actualizarTotalYCambio();
    }

    private void actualizarTotalYCambio() {
        BigDecimal total = BigDecimal.ZERO;
        for (int r = 0; r < modelItems.getRowCount(); r++) {
            Object v = modelItems.getValueAt(r, 3);
            if (v instanceof BigDecimal bd) total = total.add(bd);
            else if (v != null) {
                try { total = total.add(new BigDecimal(String.valueOf(v))); } catch (Exception ignore) {}
            }
        }
        if (lblTotal != null) lblTotal.setText("$ " + total.setScale(2, RoundingMode.HALF_UP).toPlainString());

        if (txtCobro != null && txtCambio != null) {
            try {
                String s = txtCobro.getText().trim().replace(",", ".");
                BigDecimal recibido = s.isEmpty() ? BigDecimal.ZERO : new BigDecimal(s);
                BigDecimal cambio = recibido.subtract(total);
                if (cambio.compareTo(BigDecimal.ZERO) < 0) cambio = BigDecimal.ZERO;
                txtCambio.setText(cambio.setScale(2, RoundingMode.HALF_UP).toPlainString());
            } catch (Exception ex) {
                txtCambio.setText("0.00");
            }
        }
    }

    // ====== Cobrar ======
    private void onCobrar() {
        if (cobrosAgregados.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "Agrega uno o varios cobros al ticket antes de cobrar.");
            return;
        }
        BigDecimal totalGeneral = BigDecimal.ZERO;
        for (int r = 0; r < modelItems.getRowCount(); r++) {
            BigDecimal sub = toMoney(modelItems.getValueAt(r, 3));
            totalGeneral = totalGeneral.add(sub);
        }
        if (totalGeneral.compareTo(BigDecimal.ZERO) <= 0) {
            JOptionPane.showMessageDialog(dialog, "El total debe ser mayor a 0.");
            return;
        }

        MetodoPagoItem mp = (MetodoPagoItem) cmbMetodoPago.getSelectedItem();
        if (mp == null) { JOptionPane.showMessageDialog(dialog, "Selecciona método de pago."); return; }

        // validación recibido si es efectivo
        boolean esEfectivo = mp.nombre.equalsIgnoreCase("Efectivo");
        if (esEfectivo) {
            try {
                BigDecimal recibido = toMoney(txtCobro.getText());
                if (recibido.compareTo(totalGeneral) < 0) {
                    JOptionPane.showMessageDialog(dialog, "El monto recibido es menor al total.");
                    return;
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Monto recibido inválido.");
                return;
            }
        }

        btnCobrar.setEnabled(false);

        try (Connection con = DB.get()) {
            con.setAutoCommit(false);

            // auditoría
            try (PreparedStatement ps = con.prepareStatement("SET @app_user_id = ?")) {
                ps.setInt(1, usuarioId);
                ps.executeUpdate();
            }

            // 1) Insertar EXTRAS (si los hay) en el PRIMER cobro agregado
            if (!extrasPendientes.isEmpty()) {
                int cobroDestino = cobrosAgregados.iterator().next();
                final String insDet = "INSERT INTO cobro_detalle (cobro_id, servicio_id, cantidad, precio_unit) VALUES (?,?,?,?)";
                try (PreparedStatement ins = con.prepareStatement(insDet)) {
                    for (Extra ex : extrasPendientes) {
                        ins.setInt(1, cobroDestino);
                        ins.setInt(2, ex.servicioId);
                        ins.setInt(3, ex.cantidad);
                        ins.setBigDecimal(4, ex.precioUnit);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
            }

            for (Integer cobroId : cobrosAgregados) {
                BigDecimal totalCobro = calcularTotalDelCobroEnTicket(cobroId);
                final String up = "UPDATE cobros SET estado='pagado', total=?, metodo_pago_id=? " +
                        "WHERE id=? AND estado='pendiente'";
                try (PreparedStatement ps = con.prepareStatement(up)) {
                    ps.setBigDecimal(1, totalCobro);
                    ps.setInt(2, mp.id);
                    ps.setInt(3, cobroId);
                    int n = ps.executeUpdate();
                    if (n == 0) {
                        con.rollback();
                        JOptionPane.showMessageDialog(dialog, "El cobro #" + cobroId + " ya no está pendiente.");
                        btnCobrar.setEnabled(true);
                        return;
                    }
                }
            }

            // 3) Movimiento de caja (ENTRADA) por el total general
            String ids = String.join(", ", cobrosAgregados.stream().map(String::valueOf).toList());
            final String insMov = "INSERT INTO caja_movimientos " +
                    "(sucursal_id, usuario_id, tipo, monto, descripcion, cobro_id) " +
                    "VALUES (?, ?, 'ENTRADA', ?, CONCAT('PAGO COBROS #', ?), NULL)";
            try (PreparedStatement ps = con.prepareStatement(insMov)) {
                ps.setInt(1, sucursalId);
                ps.setInt(2, usuarioId);
                ps.setBigDecimal(3, totalGeneral);
                ps.setString(4, ids);
                ps.executeUpdate();
            }

            con.commit();

            JOptionPane.showMessageDialog(dialog,
                    "Cobros " + ids + " registrados como PAGADOS.\nTotal: $ " +
                            totalGeneral.setScale(2, RoundingMode.HALF_UP).toPlainString());

            dialog.dispose(); // cerrar al cobrar

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(dialog, "No se pudo completar el pago:\n" + ex.getMessage());
            btnCobrar.setEnabled(true);
        }
    }

    // Suma los subtotales de filas del ticket cuyo cobro_id == cobroId
    private BigDecimal calcularTotalDelCobroEnTicket(int cobroId) {
        BigDecimal t = BigDecimal.ZERO;
        for (int r = 0; r < modelItems.getRowCount(); r++) {
            Object vCobro = modelItems.getValueAt(r, 5);
            if (vCobro instanceof Integer id && id == cobroId) {
                t = t.add(toMoney(modelItems.getValueAt(r, 3)));
            }
        }
        return t;
    }

    // ====== Tipos auxiliares ======
    private static class ServicioItem {
        final int id; final String nombre; final BigDecimal precio;
        ServicioItem(int id, String nombre, BigDecimal precio){
            this.id=id; this.nombre=nombre; this.precio = (precio==null?BigDecimal.ZERO:precio);
        }
        @Override public String toString(){
            return nombre + " ($" + precio.setScale(2, RoundingMode.HALF_UP).toPlainString() + ")";
        }
    }
    private static class MetodoPagoItem {
        final int id; final String nombre;
        MetodoPagoItem(int id, String nombre){ this.id=id; this.nombre=nombre; }
        @Override public String toString(){ return nombre; }
    }
    private static class Extra {
        final int servicioId; final int cantidad; final BigDecimal precioUnit;
        Extra(int servicioId, int cantidad, BigDecimal precioUnit){
            this.servicioId=servicioId; this.cantidad=cantidad; this.precioUnit=precioUnit;
        }
    }

    // ===== Helpers =====
    private static BigDecimal toMoney(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd.setScale(2, RoundingMode.HALF_UP);
        String s = String.valueOf(v).trim().replace(",", ".");
        if (s.isEmpty()) return BigDecimal.ZERO;
        return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
    }
}
