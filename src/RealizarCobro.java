import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;
import java.util.List;

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
    private JLabel lblTituloTabla2;

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

    // ====== Paleta y estilos (alineados a PantallaAdmin / InterfazCajero) ======
    private static final Color GREEN_DARK   = new Color(0x0A6B2A);
    private static final Color GREEN_BASE   = new Color(0x16A34A);
    private static final Color GREEN_SOFT   = new Color(0x22C55E);
    private static final Color BG_CANVAS    = new Color(0xF3F4F6);
    private static final Color CARD_BG      = new Color(255, 255, 255);
    private static final Color TEXT_PRIMARY = new Color(0x111827);
    private static final Color TEXT_MUTED   = new Color(0x67676E);
    private static final Color BORDER_SOFT  = new Color(0x535353);
    private static final Color BORDER_FOCUS = new Color(0x059669);
    private static final Color TABLE_ALT    = new Color(0xF9FAFB);
    private static final Color TABLE_SEL_BG = new Color(0xE6F7EE);
    private static final Color TABLE_SEL_TX = TEXT_PRIMARY;

    private final Font fText   = new Font("Segoe UI", Font.PLAIN, 16);
    private final Font fTitle  = new Font("Segoe UI", Font.BOLD, 22);
    private final Font fH1     = new Font("Segoe UI Black", Font.BOLD, 28);

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

        form.init();                // estilos + datos + eventos
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
        try { setUIFont(new Font("Segoe UI", Font.BOLD, 13)); } catch (Exception ignored) {}
        construirModelos();
        aplicarTheme();            // <<< DISEÑO / ESTILO
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
        tblTablaCobros.setRowHeight(28);
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
        tblObjetosTotal.setRowHeight(26);

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

        if (btnAnadirObjetoExtra != null) btnAnadirObjetoExtra.addActionListener(e -> agregarExtra());

        // cambio en recibido
        if (txtCobro != null) {
            txtCobro.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { actualizarTotalYCambio(); }
                public void removeUpdate(DocumentEvent e) { actualizarTotalYCambio(); }
                public void changedUpdate(DocumentEvent e) { actualizarTotalYCambio(); }
            });
            // Solo números, punto o coma
            txtCobro.addKeyListener(new KeyAdapter() {
                @Override public void keyTyped(KeyEvent e) {
                    char c = e.getKeyChar();
                    if (!Character.isDigit(c) && c != '.' && c != ',' && c != '\b') e.consume();
                }
            });
        }

        // Cantidad solo números
        if (txtCantidad != null) {
            txtCantidad.addKeyListener(new KeyAdapter() {
                @Override public void keyTyped(KeyEvent e) {
                    char c = e.getKeyChar();
                    if (!Character.isDigit(c) && c != '\b') e.consume();
                }
            });
        }

        // cobrar
        if (btnCobrar != null) btnCobrar.addActionListener(e -> onCobrar());

        // cancelar
        if (btnCancelarButton != null) btnCancelarButton.addActionListener(e -> dialog.dispose());

        if (cmbMetodoPago != null) {
            cmbMetodoPago.addActionListener(e -> {
                MetodoPagoItem mp = (MetodoPagoItem) cmbMetodoPago.getSelectedItem();
                if (mp != null && mp.nombre.equalsIgnoreCase("Transferencia")) {
                    if (txtCobro != null) { txtCobro.setText(""); txtCobro.setEditable(false); }
                } else {
                    if (txtCobro != null) txtCobro.setEditable(true);
                }
            });
        }

        if (btnEliminarObjeto != null) {
            btnEliminarObjeto.addActionListener(e -> eliminarItemSeleccionado());
        }

        // Acceso rápido: ESC cierra el diálogo
        if (panelMain != null) {
            panelMain.registerKeyboardAction(
                    e -> dialog.dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
            );
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
        if (lblMetodoPago != null) { lblMetodoPago.setText("Método de pago:"); lblMetodoPago.setFont(fText); }
        if (lblRecibido  != null)  { lblRecibido.setText("Recibido:"); lblRecibido.setFont(fText); }
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
            totalPorCobro.put(primerCobro, calcularTotalDelCobroEnTicket(primerCobro));
            totalPorCobro.remove(0);
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

    // ==================== ESTILO / THEME ====================
    private void aplicarTheme() {
        // Fondo general y jerarquía
        if (panelMain != null) panelMain.setBackground(BG_CANVAS);

        // “Cards” (bordes redondeados + sombra suave) en secciones relevantes
        decorateAsCard(panelTabla);
        decorateAsCard(panelObjetos);
        decorateAsCard(panelAgregarServicios);
        decorateAsCard(panelInfoPago);
        decorateAsCard(panelInfoPago2);
        decorateAsCard(panelAñadir);
        decorateAsCard(panelRapidos);
        decorateAsCard(panelTotal);
        decorateAsCard(panelBotones);
        decorateAsCard(panelExtra);
        decorateAsCard(panelLabel);

        // Scrollbars modernos
        if (scrTabla != null) {
            scrTabla.getVerticalScrollBar().setUI(new SmoothScrollBarUI());
            scrTabla.getHorizontalScrollBar().setUI(new SmoothScrollBarUI());
            scrTabla.setBorder(new MatteBorder(1,1,1,1,BORDER_SOFT));
            scrTabla.getViewport().setBackground(CARD_BG);
        }
        if (scrObjetos != null) {
            scrObjetos.getVerticalScrollBar().setUI(new SmoothScrollBarUI());
            scrObjetos.getHorizontalScrollBar().setUI(new SmoothScrollBarUI());
            scrObjetos.setBorder(new MatteBorder(1,1,1,1,BORDER_SOFT));
            scrObjetos.getViewport().setBackground(CARD_BG);
        }

        // Tablas: header verde, zebra, selección accesible
        if (tblTablaCobros != null) {
            JTableHeader h = tblTablaCobros.getTableHeader();
            h.setDefaultRenderer(new HeaderRenderer(h.getDefaultRenderer(), GREEN_DARK, Color.WHITE));
            h.setPreferredSize(new Dimension(h.getPreferredSize().width, 38));
            tblTablaCobros.setDefaultRenderer(Object.class, new ZebraRenderer());
            tblTablaCobros.setShowGrid(false);
            tblTablaCobros.setIntercellSpacing(new Dimension(0,0));
            tblTablaCobros.setSelectionBackground(TABLE_SEL_BG);
            tblTablaCobros.setSelectionForeground(TABLE_SEL_TX);
        }
        if (tblObjetosTotal != null) {
            JTableHeader h2 = tblObjetosTotal.getTableHeader();
            h2.setDefaultRenderer(new HeaderRenderer(h2.getDefaultRenderer(), GREEN_DARK, Color.WHITE));
            h2.setPreferredSize(new Dimension(h2.getPreferredSize().width, 36));
            tblObjetosTotal.setDefaultRenderer(Object.class, new ZebraRenderer());
            tblObjetosTotal.setShowGrid(false);
            tblObjetosTotal.setIntercellSpacing(new Dimension(0,0));
            tblObjetosTotal.setSelectionBackground(TABLE_SEL_BG);
            tblObjetosTotal.setSelectionForeground(TABLE_SEL_TX);
        }

        // Títulos y tipografías clave
        if (lblTituloTabla != null)  { lblTituloTabla.setFont(fTitle);  lblTituloTabla.setForeground(TEXT_PRIMARY); }
        if (lblTituloTabla2 != null)  { lblTituloTabla2.setFont(fTitle);  lblTituloTabla2.setForeground(TEXT_PRIMARY); }
        if (lblTituloTotal != null)  { lblTituloTotal.setFont(fTitle);  lblTituloTotal.setForeground(TEXT_PRIMARY); }
        if (lblTotal != null)        { lblTotal.setFont(fH1);           lblTotal.setForeground(GREEN_DARK); }

        // Inputs
        styleTextField(txtCantidad);
        styleTextField(txtCobro);
        styleTextField(txtCambio);

        // Botones
        stylePrimaryButton(btnCobrar);
        styleExitButton(btnCancelarButton);
        stylePrimaryButton(btnAnadirObjetoExtra);
        styleGhostButton(btnEliminarObjeto);
    }

    private void stylePrimaryButton(JButton b) {
        if (b == null) return;
        b.setFont(fText);
        b.setUI(new PantallaAdmin.ModernButtonUI(GREEN_DARK, GREEN_SOFT, GREEN_DARK, Color.WHITE, 15, true));
        b.setBorder(new EmptyBorder(10,20,10,24));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void styleGhostButton(JButton b) {
        if (b == null) return;
        b.setFont(fText);
        b.setUI(new PantallaAdmin.ModernButtonUI(new Color(0,0,0,18), new Color(0,0,0,35), new Color(0,0,0,60), TEXT_PRIMARY, 12, false));
        b.setBorder(new EmptyBorder(10,18,10,18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void styleExitButton(JButton b) {
        if (b == null) return;
        Color ROJO_BASE = new Color(0xDC2626);
        Color GRIS_HOV  = new Color(0xD1D5DB);
        Color GRIS_PRE  = new Color(0x9CA3AF);
        b.setFont(fText);
        b.setUI(new PantallaAdmin.ModernButtonUI(ROJO_BASE, GRIS_HOV, GRIS_PRE, Color.BLACK, 12, true));
        b.setBorder(new EmptyBorder(10,18,10,18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void styleTextField(JTextField tf) {
        if (tf == null) return;
        tf.setFont(fText);
        tf.setOpaque(true);
        tf.setBackground(Color.WHITE);
        tf.setForeground(TEXT_PRIMARY);
        tf.setCaretColor(TEXT_PRIMARY);
        tf.setBorder(new RoundBorder(BORDER_SOFT, 12, 1, new Insets(8, 12, 8, 12)));
        tf.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                tf.setBorder(new RoundBorder(BORDER_FOCUS, 12, 2, new Insets(8,12,8,12)));
            }
            @Override public void focusLost(FocusEvent e) {
                tf.setBorder(new RoundBorder(BORDER_SOFT, 12, 1, new Insets(8,12,8,12)));
            }
        });
    }

    private void decorateAsCard(JComponent c) {
        if (c == null) return;
        c.setOpaque(true);
        c.setBackground(CARD_BG);
        c.setBorder(new PantallaAdmin.CompoundRoundShadowBorder(14, BORDER_SOFT, new Color(0,0,0,28)));
    }

    private void setUIFont(Font f) {
        var keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object k = keys.nextElement();
            Object v = UIManager.get(k);
            if (v instanceof Font) UIManager.put(k, f);
        }
    }

    // ===== Renderers / Bordes / Scrollbar =====
    private static class HeaderRenderer extends DefaultTableCellRenderer {
        private final TableCellRenderer base;
        private final Color bg, fg;
        HeaderRenderer(TableCellRenderer base, Color bg, Color fg) { this.base=base; this.bg=bg; this.fg=fg; }
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            Component comp = base.getTableCellRendererComponent(t, v, sel, foc, r, c);
            comp.setBackground(bg);
            comp.setForeground(fg);
            comp.setFont(comp.getFont().deriveFont(Font.BOLD));
            if (comp instanceof JComponent jc) jc.setBorder(new MatteBorder(0,0,1,0, GREEN_BASE.darker()));
            return comp;
        }
    }

    private class ZebraRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            if (sel) {
                comp.setBackground(TABLE_SEL_BG);
                comp.setForeground(TABLE_SEL_TX);
            } else {
                comp.setBackground((r % 2 == 0) ? Color.WHITE : TABLE_ALT);
                comp.setForeground(TEXT_PRIMARY);
            }
            setBorder(new EmptyBorder(6,8,6,8));
            return comp;
        }
    }

    private static class RoundBorder extends MatteBorder {
        private final int arc;
        private final Insets pad;
        private final Color color;
        public RoundBorder(Color color, int arc, int thickness, Insets padding) {
            super(thickness, thickness, thickness, thickness, color);
            this.arc = arc; this.pad = padding; this.color = color;
        }
        @Override public Insets getBorderInsets(Component c) {
            return new Insets(pad.top + top, pad.left + left, pad.bottom + bottom, pad.right + right);
        }
        @Override public boolean isBorderOpaque() { return false; }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = width  - 1, h = height - 1;
            g2.setColor(color);
            g2.drawRoundRect(x, y, w, h, arc, arc);
            g2.dispose();
        }
    }

    private static class SmoothScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            this.thumbColor = new Color(0xCBD5E1);
            this.trackColor = new Color(0xF1F5F9);
        }
        @Override protected Dimension getMinimumThumbSize() { return new Dimension(8, 40); }
        @Override protected JButton createDecreaseButton(int orientation) { return botonVacio(); }
        @Override protected JButton createIncreaseButton(int orientation) { return botonVacio(); }
        private JButton botonVacio() {
            JButton b = new JButton(); b.setPreferredSize(new Dimension(0,0)); b.setOpaque(false); b.setBorder(null); return b;
        }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            g2.fillRoundRect(r.x+2, r.y+2, r.width-4, r.height-4, 8, 8);
            g2.dispose();
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2=(Graphics2D)g.create();
            g2.setColor(trackColor);
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);
            g2.dispose();
        }
    }
}
