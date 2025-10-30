import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.FileWriter;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

public class HerramientasBitacoras {

    // ----- componentes del .form -----
    private JPanel panelMain;
    private JTable tblDatos;
    private JPanel panelTabla;
    private JPanel panelBotones;
    private JPanel panelFecha;
    private JPanel panelFiltrar;
    private JScrollPane scrDatos;

    private JComboBox<String>  cmbMes;       // Mes (texto)
    private JComboBox<String>  cmbFecha;     // Año (uso el nombre original)
    private JComboBox<Integer> cmbDesde;     // Día desde (1..31)
    private JComboBox<Integer> cmbHasta;     // Día hasta (1..31)

    private JPanel panelOpciones;
    private JCheckBox checkSucursal;
    private JButton btnCSV;
    private JButton btnSalir;
    private JPanel panelTitulo;
    private JComboBox<ComboItem> cmbSucursal;
    private JComboBox<ComboItem> cmbTrabajador;
    private JCheckBox checkTrabajador;
    private JComboBox<String> cmbFiltrar; // reservado si decides usarlo

    // ----- contexto -----
    private final int usuarioId;
    private final int sucursalIdSesion;

    // ----- estado / ventana -----
    private JDialog dialog;
    private static JDialog instanciaUnica; // evita abrir 2
    private volatile boolean cargando = false; // evita cargas concurrentes/parpadeos

    // ----- formato -----
    private static final DateTimeFormatter DF_FECHA_HH = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ====== PALETA / TEMA (consistente con PantallaAsesor) ======
    private static final Color GREEN_DARK   = new Color(0x0A6B2A);
    private static final Color GREEN_BASE   = new Color(0x16A34A);
    private static final Color GREEN_SOFT   = new Color(0x22C55E);
    private static final Color BG_CANVAS    = new Color(0xF3F4F6);
    private static final Color CARD_BG      = Color.WHITE;
    private static final Color TEXT_PRIMARY = new Color(0x111827);
    private static final Color TEXT_MUTED   = new Color(0x6B7280);
    private static final Color BORDER_SOFT  = new Color(0xE5E7EB);
    private static final Color BORDER_FOCUS = new Color(0x059669);
    private static final Color TABLE_ALT    = new Color(0xF9FAFB);
    private static final Color TABLE_SEL_BG = new Color(0xE6F7EE);

    // ======= API =======
    /** Abre la ventana evitando duplicados y trayéndola al frente. */
    public static void abrir(Window owner, int usuarioId, int sucursalIdSesion) {
        if (instanciaUnica != null && instanciaUnica.isDisplayable()) {
            instanciaUnica.setAlwaysOnTop(true);
            instanciaUnica.toFront();
            instanciaUnica.requestFocus();
            instanciaUnica.setAlwaysOnTop(false);
            return;
        }
        new HerramientasBitacoras(owner, usuarioId, sucursalIdSesion);
    }

    // ======= ctor =======
    private HerramientasBitacoras(Window owner, int usuarioId, int sucursalIdSesion) {
        this.usuarioId = usuarioId;
        this.sucursalIdSesion = sucursalIdSesion;

        dialog = new JDialog(owner, "Bitácoras de cobros", Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(panelMain);
        dialog.setMinimumSize(new Dimension(1000, 640));
        dialog.setLocationRelativeTo(owner != null && owner.isShowing() ? owner : null);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed (WindowEvent e) { instanciaUnica = null; }
            @Override public void windowClosing(WindowEvent e) { instanciaUnica = null; }
        });
        instanciaUnica = dialog;

        aplicarEstilo();
        prepararTabla();
        prepararFiltros();
        cablearEventos();

        // Carga inicial
        llenarSucursales();
        setHoyPorDefecto();
        cargarTrabajadoresSegunSucursal();
        recargarTabla();

        dialog.setVisible(true);
        dialog.setAlwaysOnTop(true);
        dialog.toFront();
        dialog.requestFocus();
        dialog.setAlwaysOnTop(false);
    }

    // ======= UI / ESTILO =======
    private void aplicarEstilo() {
        // fondo general
        if (panelMain != null) panelMain.setBackground(BG_CANVAS);

        // “tarjetas”
        decorateAsCard(panelFecha);
        decorateAsCard(panelFiltrar);
        decorateAsCard(panelOpciones);
        decorateAsCard(panelTabla);
        decorateAsCard(panelTitulo);

        // tipografías básicas
        Font base = new Font("Segoe UI", Font.PLAIN, 13);
        UIManager.put("Label.font", base);
        UIManager.put("ComboBox.font", base);
        UIManager.put("CheckBox.font", base);
        UIManager.put("Table.font", base);
        UIManager.put("TableHeader.font", base.deriveFont(Font.BOLD));

        // botones
        stylePrimaryButton(btnCSV);
        styleExitButton(btnSalir);

        // scroll
        if (scrDatos != null) {
            scrDatos.setBorder(new MatteBorder(1,1,1,1, BORDER_SOFT));
            scrDatos.getViewport().setBackground(CARD_BG);
        }

        // checks / combos colores sutiles
        if (checkSucursal != null)   checkSucursal.setForeground(TEXT_PRIMARY);
        if (checkTrabajador != null) checkTrabajador.setForeground(TEXT_PRIMARY);
        if (cmbMes != null)          cmbMes.setForeground(TEXT_PRIMARY);
        if (cmbFecha != null)        cmbFecha.setForeground(TEXT_PRIMARY);
        if (cmbDesde != null)        cmbDesde.setForeground(TEXT_PRIMARY);
        if (cmbHasta != null)        cmbHasta.setForeground(TEXT_PRIMARY);
        if (cmbSucursal != null)     cmbSucursal.setForeground(TEXT_PRIMARY);
        if (cmbTrabajador != null)   cmbTrabajador.setForeground(TEXT_PRIMARY);

        if (tblDatos != null) tblDatos.setRowHeight(26);
    }

    private void decorateAsCard(JComponent c) {
        if (c == null) return;
        c.setOpaque(true);
        c.setBackground(CARD_BG);
        c.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1,1,1,1, BORDER_SOFT),
                new EmptyBorder(10,10,10,10)
        ));
    }

    private void prepararTabla() {
        DefaultTableModel m = new DefaultTableModel(
                new String[]{"ID", "Sucursal", "Usuario", "Fecha", "Método", "Total", "Notas"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return switch (c) {
                    case 0 -> Integer.class;
                    case 5 -> Double.class;
                    default -> String.class;
                };
            }
        };
        tblDatos.setModel(m);

        // estilos de tabla
        tblDatos.setShowGrid(false);
        tblDatos.setIntercellSpacing(new Dimension(0,0));
        tblDatos.setSelectionBackground(TABLE_SEL_BG);
        tblDatos.setSelectionForeground(TEXT_PRIMARY);
        tblDatos.setDefaultRenderer(Object.class, new ZebraRenderer());

        JTableHeader h = tblDatos.getTableHeader();
        h.setReorderingAllowed(false);
        h.setDefaultRenderer(new HeaderRenderer(h.getDefaultRenderer(), GREEN_DARK, Color.WHITE));
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, 34));

        ajustarAnchosTabla();
    }

    private void prepararFiltros() {
        // Meses
        String[] meses = {"Enero","Febrero","Marzo","Abril","Mayo","Junio","Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"};
        DefaultComboBoxModel<String> mm = new DefaultComboBoxModel<>(meses);
        cmbMes.setModel(mm);
        cmbMes.setSelectedIndex(LocalDate.now().getMonthValue()-1);

        // Años (actual y +/- 4)
        int anio = LocalDate.now().getYear();
        DefaultComboBoxModel<String> ya = new DefaultComboBoxModel<>();
        for (int y = anio-4; y <= anio+1; y++) ya.addElement(String.valueOf(y));
        cmbFecha.setModel(ya);
        cmbFecha.setSelectedItem(String.valueOf(anio));

        // Días (1..max del mes seleccionado)
        refrescarDiasDelMes(true);

        // Check habilitan combos
        checkSucursal.addItemListener(e -> {
            cmbSucursal.setEnabled(checkSucursal.isSelected());
            cargarTrabajadoresSegunSucursal();
            recargarTabla();
        });
        checkTrabajador.addItemListener(e -> {
            cmbTrabajador.setEnabled(checkTrabajador.isSelected());
            recargarTabla();
        });

        // Cambiar mes/año → refresca días y recalcula
        ActionListener mesAnioListener = e -> {
            refrescarDiasDelMes(false);
            recargarTabla();
        };
        cmbMes.addActionListener(mesAnioListener);
        cmbFecha.addActionListener(mesAnioListener);

        // Cambios en días → recarga
        cmbDesde.addActionListener(e -> recargarTabla());
        cmbHasta.addActionListener(e -> recargarTabla());

        // Sucursal/Trabajador combos
        cmbSucursal.addActionListener(e -> {
            if (cmbSucursal.isEnabled()) {
                cargarTrabajadoresSegunSucursal();
                recargarTabla();
            }
        });
        cmbTrabajador.addActionListener(e -> { if (cmbTrabajador.isEnabled()) recargarTabla(); });

        // Salir / CSV
        btnSalir.addActionListener(e -> dialog.dispose());
        btnCSV.addActionListener(e -> exportarCSV());
    }

    private void cablearEventos() { /* no-op extra */ }

    private void setHoyPorDefecto() {
        LocalDate hoy = LocalDate.now();
        cmbMes.setSelectedIndex(hoy.getMonthValue()-1);
        cmbFecha.setSelectedItem(String.valueOf(hoy.getYear()));
        refrescarDiasDelMes(false);
        cmbDesde.setSelectedItem(hoy.getDayOfMonth());
        cmbHasta.setSelectedItem(hoy.getDayOfMonth());
    }

    // ======= Carga de combos =======
    private void llenarSucursales() {
        DefaultComboBoxModel<ComboItem> model = new DefaultComboBoxModel<>();
        String sql = "SELECT id, nombre FROM sucursales WHERE activo=1 ORDER BY nombre";
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                model.addElement(new ComboItem(rs.getInt("id"), rs.getString("nombre")));
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(dialog, "Error al cargar sucursales: " + ex.getMessage());
        }
        cmbSucursal.setModel(model);

        // Selecciona la sucursal de la sesión si existe en la lista
        if (sucursalIdSesion > 0) {
            for (int i = 0; i < model.getSize(); i++) {
                if (model.getElementAt(i).id == sucursalIdSesion) {
                    cmbSucursal.setSelectedIndex(i);
                    break;
                }
            }
        }
        cmbSucursal.setEnabled(checkSucursal.isSelected());
    }

    private void cargarTrabajadoresSegunSucursal() {
        DefaultComboBoxModel<ComboItem> model = new DefaultComboBoxModel<>();
        Integer sucId = null;
        if (checkSucursal.isSelected() && cmbSucursal.getSelectedItem() instanceof ComboItem it) {
            sucId = it.id;
        } else if (!checkSucursal.isSelected() && sucursalIdSesion > 0) {
            sucId = sucursalIdSesion; // por sesión si no filtras manualmente
        }

        String sql = """
            SELECT t.id, t.nombre
            FROM trabajadores t
            WHERE (? IS NULL OR t.sucursal_id = ?)
            ORDER BY t.nombre
        """;
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (sucId == null) {
                ps.setNull(1, Types.INTEGER); ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(1, sucId); ps.setInt(2, sucId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    model.addElement(new ComboItem(rs.getInt(1), rs.getString(2)));
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(dialog, "Error al cargar trabajadores: " + ex.getMessage());
        }
        cmbTrabajador.setModel(model);
        cmbTrabajador.setEnabled(checkTrabajador.isSelected());
    }

    // ======= Tabla (sin "Cargando...", carga en background) =======
    private void recargarTabla() {
        if (cargando) return;          // evita cargas simultáneas / parpadeo
        cargando = true;
        setBusy(true);

        // año / mes
        int year  = Integer.parseInt((String) cmbFecha.getSelectedItem());
        int month = cmbMes.getSelectedIndex() + 1;
        YearMonth ym = YearMonth.of(year, month);

        // días seleccionados (clamp de seguridad)
        int dDesde = (cmbDesde.getSelectedItem() instanceof Integer) ? (Integer) cmbDesde.getSelectedItem() : 1;
        int dHasta = (cmbHasta.getSelectedItem() instanceof Integer) ? (Integer) cmbHasta.getSelectedItem() : ym.lengthOfMonth();
        int maxDia = ym.lengthOfMonth();
        if (dDesde < 1) dDesde = 1; if (dDesde > maxDia) dDesde = maxDia;
        if (dHasta < 1) dHasta = 1; if (dHasta > maxDia) dHasta = maxDia;

        // corrige inversión
        final int dMin = Math.min(dDesde, dHasta);
        final int dMax = Math.max(dDesde, dHasta);

        final LocalDate f0 = ym.atDay(dMin);
        final LocalDate f1 = ym.atDay(dMax);

        final Timestamp t0 = Timestamp.valueOf(f0.atStartOfDay());
        final Timestamp t1 = Timestamp.valueOf(f1.plusDays(1).atStartOfDay()); // exclusivo

        ComboItem sucItem  = (checkSucursal.isSelected()  && cmbSucursal.getSelectedItem()  instanceof ComboItem) ? (ComboItem) cmbSucursal.getSelectedItem()  : null;
        ComboItem trabItem = (checkTrabajador.isSelected() && cmbTrabajador.getSelectedItem() instanceof ComboItem) ? (ComboItem) cmbTrabajador.getSelectedItem() : null;

        final Integer sucFiltro  = (sucItem  != null) ? sucItem.id  : null;
        final Integer trabFiltro = (trabItem != null) ? trabItem.id : null;

        final String sql = """
            SELECT c.id,
                   COALESCE(s.nombre,'') AS sucursal,
                   COALESCE(u.nombre, t.nombre, '') AS usuario,
                   c.fecha,
                   COALESCE(mp.nombre,'') AS metodo,
                   c.total,
                   COALESCE(c.notas,'') AS notas
            FROM cobros c
            LEFT JOIN sucursales s    ON s.id = c.sucursal_id
            LEFT JOIN Usuarios u      ON u.id = c.usuario_id
            LEFT JOIN trabajadores t  ON t.id = u.trabajador_id
            LEFT JOIN metodos_pago mp ON mp.id = c.metodo_pago_id
            WHERE c.estado='pagado'
              AND c.fecha >= ? AND c.fecha < ?
              AND (? IS NULL OR c.sucursal_id = ?)
              AND (? IS NULL OR t.id          = ?)
            ORDER BY c.fecha
        """;

        new SwingWorker<DefaultTableModel, Void>() {
            @Override
            protected DefaultTableModel doInBackground() throws Exception {
                DefaultTableModel m = new DefaultTableModel(
                        new String[]{"ID","Sucursal","Usuario","Fecha","Método","Total","Notas"}, 0) {
                    @Override public boolean isCellEditable(int r,int c){ return false; }
                    @Override public Class<?> getColumnClass(int c){
                        return switch(c){ case 0 -> Integer.class; case 5 -> Double.class; default -> String.class; };
                    }
                };

                try (Connection con = DB.get();
                     PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setTimestamp(1, t0);
                    ps.setTimestamp(2, t1);

                    if (sucFiltro == null) { ps.setNull(3, Types.INTEGER); ps.setNull(4, Types.INTEGER); }
                    else { ps.setInt(3, sucFiltro); ps.setInt(4, sucFiltro); }

                    if (trabFiltro == null) { ps.setNull(5, Types.INTEGER); ps.setNull(6, Types.INTEGER); }
                    else { ps.setInt(5, trabFiltro); ps.setInt(6, trabFiltro); }

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int id = rs.getInt(1);
                            String suc = rs.getString(2);
                            String usuario = rs.getString(3);
                            Timestamp ts = rs.getTimestamp(4);
                            String fechaTx = ts != null ? DF_FECHA_HH.format(ts.toLocalDateTime()) : "";
                            String metodo = rs.getString(5);
                            double total  = rs.getDouble(6);
                            String notas  = rs.getString(7);
                            m.addRow(new Object[]{id, suc, usuario, fechaTx, metodo, total, notas});
                        }
                    }
                }
                return m;
            }
            @Override
            protected void done() {
                try {
                    DefaultTableModel nuevo = get();
                    tblDatos.setModel(nuevo);

                    // re-aplicar estilos tras cambiar el modelo
                    JTableHeader h = tblDatos.getTableHeader();
                    h.setReorderingAllowed(false);
                    h.setDefaultRenderer(new HeaderRenderer(h.getDefaultRenderer(), GREEN_DARK, Color.WHITE));
                    tblDatos.setDefaultRenderer(Object.class, new ZebraRenderer());
                    tblDatos.setShowGrid(false);
                    tblDatos.setIntercellSpacing(new Dimension(0,0));
                    ajustarAnchosTabla();

                    // refleja selección final por si se “clamp”earon los días
                    cmbDesde.setSelectedItem(dMin);
                    cmbHasta.setSelectedItem(dMax);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, "Error al cargar cobros: " + ex.getMessage());
                } finally {
                    setBusy(false);
                    cargando = false;
                }
            }
        }.execute();
    }

    // Refresca los combos de días cuando cambia mes/año
    private void refrescarDiasDelMes(boolean inicial) {
        int year  = Integer.parseInt((String) cmbFecha.getSelectedItem());
        int month = cmbMes.getSelectedIndex() + 1;
        YearMonth ym = YearMonth.of(year, month);
        int max = ym.lengthOfMonth();

        Integer selDesde = (cmbDesde != null && cmbDesde.getSelectedItem() instanceof Integer)
                ? (Integer) cmbDesde.getSelectedItem() : LocalDate.now().getDayOfMonth();
        Integer selHasta = (cmbHasta != null && cmbHasta.getSelectedItem() instanceof Integer)
                ? (Integer) cmbHasta.getSelectedItem() : LocalDate.now().getDayOfMonth();

        DefaultComboBoxModel<Integer> md = new DefaultComboBoxModel<>();
        DefaultComboBoxModel<Integer> mh = new DefaultComboBoxModel<>();
        for (int d = 1; d <= max; d++) { md.addElement(d); mh.addElement(d); }
        cmbDesde.setModel(md);
        cmbHasta.setModel(mh);

        // selección por defecto/previa (clamped)
        int hoyDia = LocalDate.now().getDayOfMonth();
        if (inicial) {
            cmbDesde.setSelectedItem(Math.min(hoyDia, max));
            cmbHasta.setSelectedItem(Math.min(hoyDia, max));
        } else {
            cmbDesde.setSelectedItem(Math.min(selDesde, max));
            cmbHasta.setSelectedItem(Math.min(selHasta, max));
        }
    }

    // ======= Exportar =======
    private void exportarCSV() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Exportar bitácora (CSV)");
        fc.setSelectedFile(new java.io.File("bitacora_cobros_" + LocalDate.now() + ".csv"));
        if (fc.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) return;

        try (FileWriter wr = new FileWriter(fc.getSelectedFile())) {
            // encabezados
            for (int c = 0; c < tblDatos.getColumnCount(); c++) {
                wr.write(esc(tblDatos.getColumnName(c)));
                if (c < tblDatos.getColumnCount()-1) wr.write(",");
            }
            wr.write("\n");
            // filas
            for (int r = 0; r < tblDatos.getRowCount(); r++) {
                for (int c = 0; c < tblDatos.getColumnCount(); c++) {
                    Object v = tblDatos.getValueAt(r, c);
                    wr.write(esc(v != null ? v.toString() : ""));
                    if (c < tblDatos.getColumnCount()-1) wr.write(",");
                }
                wr.write("\n");
            }
            wr.flush();
            JOptionPane.showMessageDialog(dialog, "Exportado a:\n" + fc.getSelectedFile().getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(dialog, "No se pudo exportar: " + ex.getMessage());
        }
    }

    private String esc(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private void ajustarAnchosTabla() {
        int[] w = {60, 180, 180, 150, 120, 100, 380};
        for (int i = 0; i < Math.min(w.length, tblDatos.getColumnCount()); i++) {
            tblDatos.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
        }
    }

    private void setBusy(boolean busy) {
        Cursor c = busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
                : Cursor.getDefaultCursor();
        dialog.setCursor(c);
        tblDatos.setCursor(c);
        if (btnCSV != null) btnCSV.setEnabled(!busy);
    }

    // ======= estilos reutilizables =======
    private void stylePrimaryButton(JButton b) {
        if (b == null) return;
        b.setUI(new ModernButtonUI(GREEN_BASE, GREEN_SOFT, GREEN_DARK, Color.WHITE, 12, true));
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
        private final Color base, hover, pressed, text;
        private final int radius;
        private final boolean filled;
        ModernButtonUI(Color base, Color hover, Color pressed, Color text, int radius, boolean filled) {
            this.base=base; this.hover=hover; this.pressed=pressed; this.text=text; this.radius=radius; this.filled=filled;
        }
        @Override public void installUI (JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setOpaque(false);
            b.setBorderPainted(false);
            b.setForeground(text);
            b.addMouseListener(new MouseAdapter() {});
        }
        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            ButtonModel m = b.getModel();
            Color fill = base;
            if (m.isPressed()) fill = pressed;
            else if (m.isRollover()) fill = hover;

            Shape rr = new RoundRectangle2D.Float(0, 0, b.getWidth(), b.getHeight(), radius * 2f, radius * 2f);
            if (filled) {
                g2.setColor(fill);
                g2.fill(rr);
                g2.setColor(new Color(0,0,0,25));
                g2.draw(rr);
            } else {
                g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 35));
                g2.fill(rr);
                g2.setColor(new Color(b.getForeground().getRed(), b.getForeground().getGreen(), b.getForeground().getBlue(), 160));
                g2.draw(rr);
            }

            // texto
            FontMetrics fm = g2.getFontMetrics();
            int tx = (b.getWidth() - fm.stringWidth(b.getText())) / 2;
            int ty = (b.getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.setColor(text);
            g2.drawString(b.getText(), tx, ty);

            g2.dispose();
        }
    }

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
                comp.setForeground(TEXT_PRIMARY);
            } else {
                comp.setBackground((r % 2 == 0) ? Color.WHITE : TABLE_ALT);
                comp.setForeground(TEXT_PRIMARY);
            }
            setBorder(new EmptyBorder(6,8,6,8));
            if (c == 5 && v instanceof Number) setHorizontalAlignment(SwingConstants.RIGHT);
            else setHorizontalAlignment(SwingConstants.LEFT);
            return comp;
        }
    }

    // ======= helpers =======
    /** Item para combos con id + texto. */
    private static class ComboItem {
        final int id; final String nombre;
        ComboItem(int id, String nombre) { this.id=id; this.nombre=nombre; }
        @Override public String toString() { return nombre; }
    }
}
