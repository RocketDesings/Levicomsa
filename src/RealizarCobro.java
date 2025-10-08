import javax.swing.*;
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
    private JButton btnEliminarObjeto;

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
        javax.swing.table.DefaultTableCellRenderer moneyR =
                new javax.swing.table.DefaultTableCellRenderer() {
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

        // agregar extra (puede no haber cobros; si no hay, se marcan como cobro_id=0 y luego se migran/guardan)
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

        cmbMetodoPago.addActionListener(e -> {
            MetodoPagoItem mp = (MetodoPagoItem) cmbMetodoPago.getSelectedItem();
            if (mp != null && mp.nombre.equalsIgnoreCase("Transferencia")) {
                txtCobro.setText("");
                txtCobro.setEditable(false);
            } else {
                txtCobro.setEditable(true);
            }
        });
        if (btnEliminarObjeto != null) {
            btnEliminarObjeto.addActionListener(e -> eliminarItemSeleccionado());
        }
    }

    // ====== Carga de datos ======
    private void cargarCobrosPendientes() {
        modelCobros.setRowCount(0);
        String base = """
            SELECT c.id, c.fecha,
                   COALESCE(cl.nombre, 'VENTA RÁPIDA') AS cliente,
                   COALESCE(c.total,0) AS total, c.notas,
                   COALESCE(u.nombre,u.usuario) AS registro
            FROM cobros c
            LEFT JOIN Clientes cl ON cl.id = c.cliente_id
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
        ServicioItem s = (ServicioItem) cmbServiciosExtra.getSelectedItem();
        if (s == null) {
            JOptionPane.showMessageDialog(dialog, "Selecciona un servicio extra.");
            return;
        }

        int cant;
        try {
            String raw = txtCantidad.getText().trim();
            cant = Integer.parseInt(raw);
            if (cant <= 0) throw new NumberFormatException();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(dialog, "Cantidad inválida.");
            return;
        }

        // Si NO hay cobros agregados, creamos un "cobro virtual" (id = 0) para extras sueltos
        int cobroDestino = cobrosAgregados.isEmpty() ? 0 : cobrosAgregados.iterator().next();

        BigDecimal sub = s.precio.multiply(BigDecimal.valueOf(cant));
        modelItems.addRow(new Object[]{s.nombre + " (extra)", cant, s.precio, sub, s.id, cobroDestino});
        extrasPendientes.add(new Extra(s.id, cant, s.precio));

        totalPorCobro.put(cobroDestino,
                totalPorCobro.getOrDefault(cobroDestino, BigDecimal.ZERO).add(sub));

        txtCantidad.setText("1");
        if (cmbServiciosExtra.getItemCount() > 0)
            cmbServiciosExtra.setSelectedIndex(0);

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

    //Elimina la fila seleccionada en el detalle (tblObjetosTotal)
    private void eliminarItemSeleccionado() {
        int row = tblObjetosTotal.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(dialog, "Selecciona una fila para eliminar.");
            return;
        }

        int modelRow = tblObjetosTotal.convertRowIndexToModel(row);
        Integer cobroId = (Integer) modelItems.getValueAt(modelRow, 5);
        BigDecimal sub = toMoney(modelItems.getValueAt(modelRow, 3));

        // Si era un "extra pendiente", lo quitamos de la lista
        if (cobroId != null && cobroId == 0) {
            Integer servicioId = (Integer) modelItems.getValueAt(modelRow, 4);
            int cantidad = ((Number) modelItems.getValueAt(modelRow, 1)).intValue();
            BigDecimal precio = toMoney(modelItems.getValueAt(modelRow, 2));

            for (Iterator<Extra> it = extrasPendientes.iterator(); it.hasNext();) {
                Extra ex = it.next();
                if (ex.servicioId == servicioId &&
                        ex.cantidad == cantidad &&
                        ex.precioUnit.compareTo(precio) == 0) {
                    it.remove();
                    break;
                }
            }
        }

        // Ajustamos el total del cobro correspondiente
        if (cobroId != null) {
            BigDecimal totalActual = totalPorCobro.getOrDefault(cobroId, BigDecimal.ZERO);
            totalPorCobro.put(cobroId, totalActual.subtract(sub).max(BigDecimal.ZERO));
        }

        // Eliminamos la fila del modelo
        modelItems.removeRow(modelRow);

        // Si ya no quedan filas con ese cobro_id, lo sacamos del set
        boolean siguePresente = false;
        for (int r = 0; r < modelItems.getRowCount(); r++) {
            Object val = modelItems.getValueAt(r, 5);
            if (val instanceof Integer id && id.equals(cobroId)) {
                siguePresente = true;
                break;
            }
        }
        if (!siguePresente && cobroId != null && cobroId != 0) {
            cobrosAgregados.remove(cobroId);
            totalPorCobro.remove(cobroId);
        }

        // Actualizamos títulos y totales
        actualizarTituloResumen();
        actualizarTotalYCambio();
    }

    // ====== Cobrar ======
    private void onCobrar() {

        // Basado en lo que REALMENTE hay en el ticket
        if (modelItems.getRowCount() == 0) {
            JOptionPane.showMessageDialog(dialog,
                    "Agrega al menos un cobro o un servicio extra antes de cobrar.");
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

            boolean hayCobros = !cobrosAgregados.isEmpty();
            if (hayCobros) migrarExtrasAlPrimerCobroSiCorresponde();

            // 1) Si hay cobros + extras: inserta los extras en el PRIMER cobro
            if (hayCobros && !extrasPendientes.isEmpty()) {
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

            // 2) Si hay cobros: marcarlos como pagados con su total individual
            if (hayCobros) {
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
            }

            // 3) Movimiento(s) de caja (ENTRADA)
            if (hayCobros) {
                final String insMov = "INSERT INTO caja_movimientos " +
                        "(sucursal_id, usuario_id, tipo, monto, descripcion, cobro_id) " +
                        "VALUES (?, ?, 'ENTRADA', ?, ?, ?)";
                try (PreparedStatement ps = con.prepareStatement(insMov)) {
                    for (Integer cobroId : cobrosAgregados) {
                        BigDecimal totalCobro = calcularTotalDelCobroEnTicket(cobroId);
                        ps.setInt(1, sucursalId);
                        ps.setInt(2, usuarioId);
                        ps.setBigDecimal(3, totalCobro);
                        ps.setString(4, "PAGO COBRO #" + cobroId);
                        ps.setInt(5, cobroId);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                con.commit();
                String ids = String.join(", ", cobrosAgregados.stream().map(String::valueOf).toList());
                JOptionPane.showMessageDialog(dialog,
                        "Cobros " + ids + " registrados como PAGADOS.");
            } else {
                // SOLO EXTRAS -> crea un cobro con cliente_id NULL
                long cobroExtrasId = crearCobroSoloExtras(con, ((MetodoPagoItem) cmbMetodoPago.getSelectedItem()));
                con.commit();
                JOptionPane.showMessageDialog(dialog,
                        "Pago de servicios extra registrado. Folio cobro #" + cobroExtrasId + ".");
            }

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

    // Pasa las filas de extras con cobro_id = 0 al primer cobro del ticket
    private void migrarExtrasAlPrimerCobroSiCorresponde() {
        if (cobrosAgregados.isEmpty()) return;
        int primerCobro = cobrosAgregados.iterator().next();
        boolean migrado = false;

        for (int r = 0; r < modelItems.getRowCount(); r++) {
            Object vCobro = modelItems.getValueAt(r, 5);
            if (vCobro instanceof Integer id && id == 0) {
                modelItems.setValueAt(primerCobro, r, 5);
                migrado = true;
            }
        }
        if (migrado) {
            // Recalcula totales por cobro en memoria si los usas en la UI
            totalPorCobro.put(primerCobro, calcularTotalDelCobroEnTicket(primerCobro));
            totalPorCobro.remove(0); // limpia el acumulado del “cobro virtual”
            actualizarTotalYCambio();
        }
    }

    // Crea un cobro “rápido” pagado solo con extras, guarda detalle y su movimiento de caja enlazado.
    // Devuelve el ID del cobro creado. cliente_id = NULL (venta rápida)
    private long crearCobroSoloExtras(Connection con, MetodoPagoItem mp) throws Exception {
        // 1) Encabezado
        final String insCobro = "INSERT INTO cobros (sucursal_id, cliente_id, usuario_id, notas) VALUES (?,?,?,?)";
        long cobroId;
        try (PreparedStatement ps = con.prepareStatement(insCobro, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, sucursalId);
            ps.setNull(2, Types.INTEGER); // cliente_id NULL para venta rápida
            ps.setInt(3, usuarioId);
            ps.setString(4, "COBRO SOLO EXTRAS");
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                if (!k.next()) throw new SQLException("No se generó ID de cobro para extras.");
                cobroId = k.getLong(1);
            }
        }

        // 2) Detalle de extras
        final String insDet = "INSERT INTO cobro_detalle (cobro_id, servicio_id, cantidad, precio_unit) VALUES (?,?,?,?)";
        BigDecimal totalExtras = BigDecimal.ZERO;
        try (PreparedStatement ps = con.prepareStatement(insDet)) {
            for (Extra ex : extrasPendientes) {
                ps.setLong(1, cobroId);
                ps.setInt(2, ex.servicioId);
                ps.setInt(3, ex.cantidad);
                ps.setBigDecimal(4, ex.precioUnit);
                ps.addBatch();
                totalExtras = totalExtras.add(ex.precioUnit.multiply(BigDecimal.valueOf(ex.cantidad)));
            }
            ps.executeBatch();
        }

        // 3) Marcar pagado con total y método de pago
        final String up = "UPDATE cobros SET estado='pagado', total=?, metodo_pago_id=? " +
                "WHERE id=? AND estado='pendiente'";
        try (PreparedStatement ps = con.prepareStatement(up)) {
            ps.setBigDecimal(1, totalExtras);
            ps.setInt(2, mp.id);
            ps.setLong(3, cobroId);
            int n = ps.executeUpdate();
            if (n == 0) throw new SQLException("El cobro de extras ya no está pendiente (concurrencia).");
        }

        // 4) Movimiento de caja enlazado a este cobro
        final String insMov = "INSERT INTO caja_movimientos " +
                "(sucursal_id, usuario_id, tipo, monto, descripcion, cobro_id) " +
                "VALUES (?, ?, 'ENTRADA', ?, 'PAGO SERVICIOS EXTRA', ?)";
        try (PreparedStatement ps = con.prepareStatement(insMov)) {
            ps.setInt(1, sucursalId);
            ps.setInt(2, usuarioId);
            ps.setBigDecimal(3, totalExtras);
            ps.setLong(4, cobroId);
            ps.executeUpdate();
        }

        return cobroId;
    }
}
