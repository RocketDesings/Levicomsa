import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.FileWriter;
import java.sql.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class CorteCaja {
    // Zona horaria oficial para Tepic/Nayarit
    private static final ZoneId ZONA_TEPIC = ZoneId.of("America/Mazatlan");

    // ==== componentes del .form ====
    private JPanel main;
    private JLabel lbl1;
    private JLabel lblSucursal;
    private JLabel lblFecha;
    private JLabel lblFondoInicial;
    private JLabel lblIngresosEfectivo;
    private JLabel lblIngresosTransferencia;
    private JLabel lblsalidas;
    private JTable tblResumen;
    private JLabel lblEfectivoTeorico;
    private JLabel lblVentas;
    private JLabel lblExtras;
    private JLabel lblIngresosTotales;
    private JLabel lblDiferencia;
    private JButton btnContarEfectivoButton;
    private JButton btnExportarCSV;
    private JButton btnCerrarCorte;
    private JLabel lblTotalContadores;
    private JLabel lblContado;
    private JLabel lblEntradas;
    private JButton btnHonorarios;

    // ==== contexto ====
    private final int sucursalId;
    private final int usuarioId;

    // dialog
    private JDialog dialog;

    // referencia para evitar duplicados (Honorarios)
    private JDialog dlgHonorarios;

    // formato
    private static final DateTimeFormatter DF_FECHA   = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DF_FECHAHH = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Colores (coinciden con InterfazCajero)
    private static final Color GREEN_DARK   = new Color(0x0A6B2A);
    private static final Color GREEN_BASE   = new Color(0x16A34A);
    private static final Color GREEN_SOFT   = new Color(0x22C55E);
    private static final Color BG_CANVAS    = new Color(0xF3F4F6);
    private static final Color TEXT_PRIMARY = new Color(0x111827);
    private static final Color BORDER_SOFT  = new Color(0xE5E7EB);
    private static final Color TABLE_ALT    = new Color(0xF9FAFB);
    private static final Color TABLE_SEL_BG = new Color(0xE6F7EE);

    // ================== APERTURA ==================
    public static void mostrar(Window owner, int sucursalId, int usuarioId) {
        CorteCaja ui = new CorteCaja(sucursalId, usuarioId);
        ui.dialog = new JDialog(owner, "Corte de caja", Dialog.ModalityType.APPLICATION_MODAL);
        ui.dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        ui.dialog.setContentPane(ui.wrap(ui.main));
        ui.dialog.pack();
        ui.dialog.setResizable(false);
        ui.dialog.setLocationRelativeTo(owner != null && owner.isShowing() ? owner : null);
        ui.dialog.setVisible(true);
    }

    public CorteCaja(int sucursalId, int usuarioId) {
        this.sucursalId = sucursalId;
        this.usuarioId  = usuarioId;

        aplicarEstilo();
        inicializarCabecera();
        configurarTabla();
        cablearBotones();

        // Cargar datos del día
        cargarTablaResumenDelDia();
        recalcularTotalesDelDia();
    }

    // ================== UI / ESTILO ==================
    private JComponent wrap(JComponent inner) {
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(8,8,8,8));
        root.add(inner, BorderLayout.CENTER);
        return root;
    }

    private void aplicarEstilo() {
        if (main != null) main.setBackground(BG_CANVAS);
        if (lbl1  != null) lbl1.setBorder(new MatteBorder(0,0,2,0, BORDER_SOFT));

        stylePrimaryButton(btnContarEfectivoButton);
        stylePrimaryButton(btnExportarCSV);
        styleExitButton(btnCerrarCorte);
        styleOutlineButton(btnHonorarios);
    }

    private void inicializarCabecera() {
        // Fecha: hoy si no hay texto
        LocalDate hoy = LocalDate.now(ZONA_TEPIC);
        if (lblFecha != null) lblFecha.setText(DF_FECHA.format(hoy));

        // Nombre de sucursal (si hay etiqueta)
        if (lblSucursal != null) {
            String nombre = "";
            if (sucursalId > 0) {
                final String sql = "SELECT nombre FROM sucursales WHERE id=?";
                try (Connection con = DB.get();
                     PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, sucursalId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) nombre = rs.getString(1);
                    }
                } catch (SQLException ignore) { }
            }
            lblSucursal.setText(nombre != null ? "Sucursal " + nombre : "");
            lblSucursal.setFont(new Font("Segoe UI", Font.BOLD, 16));
            lblSucursal.setForeground(TEXT_PRIMARY);
        }
    }

    private void configurarTabla() {
        DefaultTableModel m = new DefaultTableModel(
                new String[]{"Concepto", "Descripción", "Monto", "Método", "Fecha"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 2 ? Double.class : String.class; }
        };
        tblResumen.setModel(m);
        tblResumen.setRowHeight(26);
        tblResumen.setShowGrid(false);
        tblResumen.setIntercellSpacing(new Dimension(0,0));
        tblResumen.getTableHeader().setReorderingAllowed(false);

        // Header verde
        JTableHeader h = tblResumen.getTableHeader();
        h.setDefaultRenderer(new HeaderRenderer(h.getDefaultRenderer(), GREEN_DARK, Color.WHITE));
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, 32));

        // Zebra
        tblResumen.setDefaultRenderer(Object.class, new ZebraRenderer());

        // Anchos
        int[] w = {200, 280, 110, 120, 160};
        for (int i = 0; i < tblResumen.getColumnCount() && i < w.length; i++) {
            tblResumen.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
        }
    }

    private void cablearBotones() {
        if (btnContarEfectivoButton != null) {
            btnContarEfectivoButton.addActionListener(e -> {
                String v = JOptionPane.showInputDialog(dialog, "Efectivo contado ($):", "0");
                if (v == null) return;
                v = v.replace("$","").replace(",","").trim();
                double contado = 0;
                try { contado = Double.parseDouble(v); } catch (Exception ignore) {}
                lblContado.setText(formatea(contado));
                recalcularTotalesDelDia(); // actualiza diferencia
            });
        }
        if (btnExportarCSV != null) {
            btnExportarCSV.addActionListener(e -> exportarCSV());
        }
        if (btnCerrarCorte != null) {
            btnCerrarCorte.addActionListener(e ->
                    JOptionPane.showMessageDialog(dialog, "Cierre de corte (pendiente implementar persistencia)."));
        }
        // === abrir Honorarios (simple, sin duplicados) ===
        if (btnHonorarios != null) {
            btnHonorarios.addActionListener(e -> abrirHonorarios());
        }
    }

    // ================== CARGA DE DATOS ==================
    private LocalDate fechaCorte() {
        try {
            return LocalDate.parse(lblFecha.getText().trim(), DF_FECHA);
        } catch (Exception ignore) {
            return LocalDate.now(ZONA_TEPIC);
        }
    }

    /** Llena la tabla con Cobros pagados + Movimientos de caja del día/sucursal. */
    private void cargarTablaResumenDelDia() {
        DefaultTableModel m = (DefaultTableModel) tblResumen.getModel();
        m.setRowCount(0);

        if (sucursalId <= 0) {
            JOptionPane.showMessageDialog(dialog, "Sucursal no definida.");
            return;
        }

        LocalDate f = fechaCorte();
        Timestamp t0 = startOf(f);
        Timestamp t1 = endOf(f);

        final String sql = """
        SELECT concepto, descripcion, monto, metodo, fecha
        FROM (
            /* Cobros pagados del día */
            SELECT 
                CONCAT('COBRO ', 
                       CASE WHEN COALESCE(NULLIF(c.notas,''), '') <> '' 
                            THEN c.notas ELSE CONCAT('#',c.id) END)                       AS concepto,
                COALESCE(c.notas,'')                                                      AS descripcion,
                c.total                                                                   AS monto,
                COALESCE(mp.nombre,'')                                                    AS metodo,
                c.fecha                                                                   AS fecha
            FROM cobros c
            LEFT JOIN metodos_pago mp ON mp.id = c.metodo_pago_id
            WHERE c.estado='pagado'
              AND c.sucursal_id = ?
              AND c.fecha >= ? AND c.fecha < ?

            UNION ALL

            /* Movimientos de caja del día (entradas/salidas) */
            SELECT 
                m.descripcion                                                             AS concepto,
                CASE WHEN m.cobro_id IS NOT NULL THEN CONCAT('Ref cobro #', m.cobro_id) 
                     ELSE '' END                                                          AS descripcion,
                CASE WHEN m.tipo='SALIDA' THEN -m.monto ELSE m.monto END                  AS monto,
                'Caja'                                                                    AS metodo,
                m.fecha                                                                   AS fecha
            FROM caja_movimientos m
            WHERE m.sucursal_id = ?
              AND m.fecha >= ? AND m.fecha < ?
              AND m.cobro_id IS NULL   -- evita duplicar pagos ya listados en COBROS
        ) t
        ORDER BY fecha
        """;

        boolean hayFilas = false;
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, sucursalId); ps.setTimestamp(2, t0); ps.setTimestamp(3, t1);
            ps.setInt(4, sucursalId); ps.setTimestamp(5, t0); ps.setTimestamp(6, t1);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String concepto = rs.getString(1);
                    String descr    = rs.getString(2);
                    double monto    = rs.getDouble(3);
                    String metodo   = rs.getString(4);
                    Timestamp ts    = rs.getTimestamp(5);
                    String fechaTx = (ts != null ? DF_FECHAHH.format(ZonedDateTime.ofInstant(ts.toInstant(), ZONA_TEPIC)) : "");
                    m.addRow(new Object[]{concepto, descr, monto, metodo, fechaTx});
                    hayFilas = true;
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(dialog, "Error al cargar resumen:\n" + ex.getMessage());
            ex.printStackTrace();
        }

        if (!hayFilas) {
            m.addRow(new Object[]{"Sin registros para el día", "", 0.0, "", ""});
        }
    }

    /** Calcula totales y pinta labels (efectivo incluye entradas de caja). */
    private void recalcularTotalesDelDia() {
        LocalDate f = fechaCorte();
        Timestamp t0 = startOf(f);
        Timestamp t1 = endOf(f);

        double fondoInicial = 0.0;
        double salidas      = 0.0;
        double entradasCaja = 0.0;
        double ventasEfe    = 0.0;
        double ventasTrans  = 0.0;
        double ventasTotal  = 0.0;

        // -- Movimientos de caja
        final String qMovs = """
        SELECT
            COALESCE(SUM(CASE WHEN tipo='ENTRADA' AND descripcion LIKE 'INICIO TURNO CAJA%' THEN monto END), 0) AS fondo,
            COALESCE(SUM(CASE WHEN tipo='SALIDA'  THEN monto END), 0)                                           AS salidas,
            COALESCE(SUM(CASE WHEN tipo='ENTRADA' AND descripcion NOT LIKE 'INICIO TURNO CAJA%' THEN monto END), 0) AS entradas
        FROM caja_movimientos
        WHERE sucursal_id = ?
          AND fecha >= ? AND fecha < ?
        """;
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(qMovs)) {
            ps.setInt(1, sucursalId);
            ps.setTimestamp(2, t0);
            ps.setTimestamp(3, t1);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    fondoInicial = rs.getDouble("fondo");
                    salidas      = rs.getDouble("salidas");
                    entradasCaja = rs.getDouble("entradas");
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(dialog, "Error en movimientos:\n" + ex.getMessage());
            ex.printStackTrace();
        }

        // -- Cobros pagados: separar por método y servicios vs extras
        final String qCobros = """
            SELECT 
                COALESCE(SUM(CASE WHEN metodo_pago_id = 1 THEN total END), 0) AS efec,
                COALESCE(SUM(CASE WHEN metodo_pago_id = 3 THEN total END), 0) AS transf,
                COALESCE(SUM(CASE WHEN cliente_id IS NOT NULL THEN total END), 0) AS servicios,
                COALESCE(SUM(CASE WHEN cliente_id IS NULL  THEN total END), 0) AS extras
            FROM cobros
            WHERE estado='pagado'
              AND sucursal_id = ?
              AND fecha >= ? AND fecha < ?
        """;
        double extrasTotal = 0.0;
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(qCobros)) {
            ps.setInt(1, sucursalId);
            ps.setTimestamp(2, t0);
            ps.setTimestamp(3, t1);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ventasEfe    = rs.getDouble("efec");
                    ventasTrans  = rs.getDouble("transf");
                    ventasTotal  = rs.getDouble("servicios");
                    extrasTotal  = rs.getDouble("extras");
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(dialog, "Error en cobros:\n" + ex.getMessage());
            ex.printStackTrace();
        }

        // lblIngresosEfectivo = cobros en efectivo + movimientos ENTRADA
        double ingresosEfectivoLbl = ventasEfe + entradasCaja;

        // Efectivo teórico y “ingresos totales”
        double efectivoTeorico = fondoInicial + ventasEfe + entradasCaja - salidas;
        double ingresosTotales = ventasTotal + extrasTotal + entradasCaja;

        double contado = parseMoney(lblContado != null ? lblContado.getText() : "0");
        double diferencia = efectivoTeorico - contado;


        // Pintar
        pintar(lblFondoInicial,          fondoInicial);
        pintar(lblIngresosEfectivo,      ingresosEfectivoLbl);
        pintar(lblIngresosTransferencia, ventasTrans);
        pintar(lblEntradas,              entradasCaja);
        pintar(lblsalidas,               salidas);
        pintar(lblVentas,                ventasTotal);
        pintar(lblExtras,                extrasTotal);
        pintar(lblEfectivoTeorico,       efectivoTeorico);
        pintar(lblDiferencia,            diferencia);

        // 1) Calcula honorarios (60%) de contadores rol 1/4/5 en el rango del corte
        double honorariosContadores = calcularHonorariosContadores(t0, t1);

// 2) Pinta el label dedicado a contadores
        pintar(lblTotalContadores, honorariosContadores);

// 3) Resta honorarios del TOTAL (lo que queda para la empresa)
        double ingresosTotalesNetos = ingresosTotales - honorariosContadores;
        pintar(lblIngresosTotales, ingresosTotalesNetos);




        if (lblContado != null && (lblContado.getText() == null || lblContado.getText().isBlank()))
            lblContado.setText(formatea(0));

    }

    // ================== EXPORTAR ==================
    private void exportarCSV() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Exportar corte");
        fc.setSelectedFile(new java.io.File("corte_caja_" + LocalDate.now(ZONA_TEPIC) + ".csv"));
        if (fc.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) return;

        try (FileWriter wr = new FileWriter(fc.getSelectedFile())) {
            for (int c = 0; c < tblResumen.getColumnCount(); c++) {
                wr.write(escapeCSV(tblResumen.getColumnName(c)));
                if (c < tblResumen.getColumnCount() - 1) wr.write(",");
            }
            wr.write("\n");
            for (int r = 0; r < tblResumen.getRowCount(); r++) {
                for (int c = 0; c < tblResumen.getColumnCount(); c++) {
                    Object v = tblResumen.getValueAt(r, c);
                    wr.write(escapeCSV(v != null ? v.toString() : ""));
                    if (c < tblResumen.getColumnCount() - 1) wr.write(",");
                }
                wr.write("\n");
            }
            wr.flush();
            JOptionPane.showMessageDialog(dialog, "Exportado a:\n" + fc.getSelectedFile().getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(dialog, "No se pudo exportar:\n" + ex.getMessage());
        }
    }
    private String escapeCSV(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"","\"\"") + "\"";
        }
        return s;
    }

    // ================== HELPERS ==================
    private void pintar(JLabel l, double v) { if (l != null) l.setText(formatea(v)); }
    private String formatea(double v) { return String.format("$%,.2f", v); }
    private double parseMoney(String s) {
        if (s == null) return 0;
        try { return Double.parseDouble(s.replace("$","").replace(",","").trim()); }
        catch (Exception e) { return 0; }
    }
    private java.sql.Timestamp startOf(java.time.LocalDate d) {
        return java.sql.Timestamp.from(d.atStartOfDay(ZONA_TEPIC).toInstant());
    }
    private java.sql.Timestamp endOf(java.time.LocalDate d) {
        return java.sql.Timestamp.from(d.plusDays(1).atStartOfDay(ZONA_TEPIC).toInstant());
    }

    // ===== estilos =====
    private void stylePrimaryButton(JButton b) {
        if (b == null) return;
        b.setUI(new ModernButtonUI(GREEN_BASE, GREEN_SOFT, GREEN_DARK, Color.WHITE, 12, true));
        b.setBorder(new EmptyBorder(10,18,10,18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    private void styleOutlineButton(JButton b) {
        if (b == null) return;
        b.setUI(new ModernButtonUI(new Color(0,0,0,0), new Color(0,0,0,25), new Color(0,0,0,45), TEXT_PRIMARY, 12, false));
        b.setBorder(new EmptyBorder(10,18,10,18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    private void styleExitButton(JButton b) {
        if (b == null) return;
        Color ROJO_BASE = new Color(0xDC2626);
        Color GRIS_HOV  = new Color(0xD1D5DB);
        Color GRIS_PRE  = new Color(0x9CA3AF);
        b.setUI(new ModernButtonUI(ROJO_BASE, GRIS_HOV, GRIS_PRE, Color.BLACK, 12, true));
        b.setBorder(new EmptyBorder(10,18,10,18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    private static class ModernButtonUI extends BasicButtonUI {
        private final Color base, hover, pressed, text; private final int radius; private final boolean filled;
        ModernButtonUI(Color base, Color hover, Color pressed, Color text, int radius, boolean filled) {
            this.base=base; this.hover=hover; this.pressed=pressed; this.text=text; this.radius=radius; this.filled=filled;
        }
        @Override public void installUI(JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setOpaque(false); b.setBorderPainted(false); b.setForeground(text);
            b.addMouseListener(new MouseAdapter(){}); // habilita rollover
        }
        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c; Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ButtonModel m = b.getModel(); Color fill = m.isPressed()?pressed:(m.isRollover()?hover:base);
            Shape rr = new RoundRectangle2D.Float(0,0,b.getWidth(),b.getHeight(), radius*2f, radius*2f);
            g2.setColor(fill); g2.fill(rr); g2.setColor(new Color(0,0,0,25)); g2.draw(rr);
            g2.setColor(text);
            FontMetrics fm = g2.getFontMetrics();
            int tx = (b.getWidth()-fm.stringWidth(b.getText()))/2;
            int ty = (b.getHeight()+fm.getAscent()-fm.getDescent())/2;
            g2.drawString(b.getText(), tx, ty);
            g2.dispose();
        }
    }
    private static class HeaderRenderer extends DefaultTableCellRenderer {
        private final TableCellRenderer base; private final Color bg, fg;
        HeaderRenderer(TableCellRenderer base, Color bg, Color fg) { this.base=base; this.bg=bg; this.fg=fg; }
        @Override public Component getTableCellRendererComponent(JTable t,Object v,boolean s,boolean f,int r,int c){
            Component comp = base.getTableCellRendererComponent(t, v, s, f, r, c);
            comp.setBackground(bg); comp.setForeground(fg);
            comp.setFont(comp.getFont().deriveFont(Font.BOLD));
            if (comp instanceof JComponent jc) jc.setBorder(new MatteBorder(0,0,1,0,bg.darker()));
            return comp;
        }
    }
    private class ZebraRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t,Object v,boolean sel,boolean foc,int r,int c){
            Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            if (sel) { comp.setBackground(TABLE_SEL_BG); comp.setForeground(TEXT_PRIMARY); }
            else     { comp.setBackground((r%2==0)?Color.WHITE:TABLE_ALT); comp.setForeground(TEXT_PRIMARY); }
            setBorder(new EmptyBorder(6,8,6,8));
            if (c==2 && v instanceof Number) setHorizontalAlignment(SwingConstants.RIGHT);
            else setHorizontalAlignment(SwingConstants.LEFT);
            return comp;
        }
    }

    // ===== Honorarios: abrir simple sin duplicar =====
    private void abrirHonorarios() {
        if (dlgHonorarios != null && dlgHonorarios.isDisplayable()) {
            dlgHonorarios.toFront();
            dlgHonorarios.requestFocus();
            return;
        }
        // Debes tener un factory estático en tu clase HonorariosCajero
        // con la firma: createDialog(Window owner, int sucursalId, int usuarioId)
        dlgHonorarios = HonorariosCajero.createDialog(dialog, sucursalId, usuarioId);
        dlgHonorarios.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed (WindowEvent e) { dlgHonorarios = null; }
            @Override public void windowClosing(WindowEvent e) { dlgHonorarios = null; }
        });
        dlgHonorarios.setLocationRelativeTo(dialog != null && dialog.isShowing() ? dialog : null);
        dlgHonorarios.setVisible(true);
    }

    // === Config para "Servicios contables" ===
    private static final String SERVICIO_CONTABLE_EXACTO = "Servicios contables";
    // Solo estos usuarios pueden realizar cobros de ese servicio
    private static final String CONTADORES_IN = "(1,2,5)";

    /** Suma el 60% de los cobros del rango [t0, t1) hechos por usuarios con rol CONTADOR.
     *  En tu base: roles de contadores son id 1, 4 y 5. Además, por seguridad, también
     *  considera nombres que contengan 'contador' (ej. 'Asesor/Contador').
     */
    private double calcularHonorariosContadores(java.sql.Timestamp t0, java.sql.Timestamp t1) {
        final String qHonor = """
        SELECT COALESCE(SUM(c.total * 0.60), 0) AS total
        FROM cobros c
        JOIN Usuarios u ON u.id = c.usuario_id
        LEFT JOIN roles   r ON r.id = u.rol_id
        WHERE c.estado = 'pagado'
          AND c.sucursal_id = ?
          AND c.fecha >= ? AND c.fecha < ?
          AND (
                u.rol_id IN (1, 4, 5)
                OR LOWER(r.nombre) LIKE '%contador%'
              )
    """;
        try (java.sql.Connection con = DB.get();
             java.sql.PreparedStatement ps = con.prepareStatement(qHonor)) {
            ps.setInt(1, sucursalId);        // usa tu variable de sucursal actual
            ps.setTimestamp(2, t0);
            ps.setTimestamp(3, t1);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("total");
            }
        } catch (Exception e) {
            e.printStackTrace();
            javax.swing.JOptionPane.showMessageDialog(dialog,
                    "No se pudo calcular honorarios de contadores:\n" + e.getMessage());
        }
        return 0.0;
    }





}
