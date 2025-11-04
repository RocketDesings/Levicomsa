import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.geom.RoundRectangle2D;
import java.math.BigDecimal;
import java.sql.*;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

/** UI y carga de datos para Honorarios del Cajero. */
public class HonorariosCajero {

    // ===== componentes (.form o fallback) =====
    JPanel panelMain;
    private JTable tblHonorarios;
    private JScrollPane scrHonorarios;
    private JPanel panelTablaa;
    private JPanel panelBotones;
    private JButton btnSalir;

    // ===== contexto =====
    private final int sucursalId;
    private final int usuarioId;

    // ===== paleta =====
    private static final Color BG_CANVAS    = new Color(0xF3F4F6);
    private static final Color BG_TOP       = new Color(0x052E16);
    private static final Color BG_BOT       = new Color(0x064E3B);
    private static final Color TEXT_MUTED   = new Color(0x67676E);
    private static final Color TABLE_ALT    = new Color(0xF9FAFB);
    private static final Color TABLE_SEL_BG = new Color(0xE6F7EE);
    private static final Color BORDER_SOFT  = new Color(0x535353);
    private static final Color CARD_BG      = new Color(255, 255, 255);
    private static final Color GREEN_DARK   = new Color(0x0A6B2A);
    private static final Color GREEN_BASE   = new Color(0x16A34A);
    private static final Color GREEN_SOFT   = new Color(0x22C55E);
    private static final Color TEXT_PRIMARY = new Color(0x111827);
    private static final Color BORDER_FOCUS = new Color(0x059669);
    private final Font fText   = new Font("Segoe UI", Font.PLAIN, 16);
    private final Font fTitle  = new Font("Segoe UI", Font.BOLD, 22);


    private static final NumberFormat MXN = NumberFormat.getCurrencyInstance(new Locale("es","MX"));

    // ===== ctor =====
    public HonorariosCajero(int sucursalId, int usuarioId) {
        this.sucursalId = sucursalId;
        this.usuarioId  = usuarioId;

        ensureUI();
        applyTheme();
        setupTable();
        wireActions();
        loadHonorarios(); // <-- carga datos de BD
    }

    /** Crea el diálogo MODELESS listo para usarse. */
    public static JDialog createDialog(Window owner, int sucursalId, int usuarioId) {
        HonorariosCajero ui = new HonorariosCajero(sucursalId, usuarioId);

        JDialog dlg = new JDialog(owner, "Honorarios del Cajero", Dialog.ModalityType.MODELESS);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JComponent content = ui.panelMain != null ? ui.panelMain : new JPanel();
        dlg.setContentPane(content);
        dlg.pack();
        dlg.setResizable(true);
        dlg.setLocationRelativeTo((owner != null && owner.isShowing()) ? owner : null);

        if (ui.btnSalir != null) ui.btnSalir.addActionListener(e -> dlg.dispose());
        return dlg;
    }

    // ================== UI programática (por si el .form no inyecta) ==================
    private void ensureUI() {
        if (panelMain != null) return;
        panelMain = new JPanel(new BorderLayout(12, 12));
        panelMain.setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel title = new JLabel("Honorarios del Cajero (60% de cobros pagados)");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(TEXT_PRIMARY);
        title.setBorder(new MatteBorder(0, 0, 1, 0, new Color(0xE5E7EB)));
        panelMain.add(title, BorderLayout.NORTH);

        panelTablaa = new JPanel(new BorderLayout());
        tblHonorarios = new JTable();
        scrHonorarios = new JScrollPane(tblHonorarios);
        panelTablaa.add(scrHonorarios, BorderLayout.CENTER);
        panelMain.add(panelTablaa, BorderLayout.CENTER);

        panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        btnSalir = new JButton("Salir");
        panelBotones.add(btnSalir);
        panelMain.add(panelBotones, BorderLayout.SOUTH);
    }

    private void applyTheme() {
        panelMain.setBackground(BG_CANVAS);
        if (panelTablaa != null) panelTablaa.setBackground(Color.WHITE);
        if (panelBotones != null) panelBotones.setBackground(BG_CANVAS);
        if (btnSalir != null) styleExitButton(btnSalir);
        decorateAsCard(panelMain);
        decorateAsCard(panelTablaa);
        decorateAsCard(panelBotones);
    }

    private void setupTable() {
        if (tblHonorarios == null) return;

        // Columnas requeridas: Nombre, Monto Honorarios, Fecha
        DefaultTableModel m = new DefaultTableModel(
                new String[]{"Nombre", "Monto Honorarios", "Fecha"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return switch (c) {
                    case 1 -> BigDecimal.class; // monto
                    default -> String.class;
                };
            }
        };
        tblHonorarios.setModel(m);
        tblHonorarios.setRowHeight(26);
        tblHonorarios.setShowGrid(false);
        tblHonorarios.setIntercellSpacing(new Dimension(0,0));
        tblHonorarios.getTableHeader().setReorderingAllowed(false);

        JTableHeader h = tblHonorarios.getTableHeader();
        h.setDefaultRenderer(new HeaderRenderer(h.getDefaultRenderer(), GREEN_DARK, Color.WHITE));
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, 32));

        // Zebra por defecto + renderer monetario en la columna 1
        tblHonorarios.setDefaultRenderer(Object.class, new ZebraRenderer());
        tblHonorarios.getColumnModel().getColumn(1).setCellRenderer(new MoneyZebraRenderer());

        int[] w = {280, 160, 140};
        for (int i = 0; i < Math.min(w.length, tblHonorarios.getColumnCount()); i++) {
            tblHonorarios.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
        }

        // Placeholder si viene vacío
        if (m.getRowCount() == 0) m.addRow(new Object[]{"—", BigDecimal.ZERO, "—"});
    }

    private void wireActions() { /* más acciones si agregas botones */ }

    // ================== Carga de datos (BD) ==================
    private void loadHonorarios() {
        if (tblHonorarios == null) return;
        DefaultTableModel m = (DefaultTableModel) tblHonorarios.getModel();
        m.setRowCount(0);

        // Ventana de hoy en Mazatlán
        Timestamp[] win = hoyMazatlanWindow(); // [t0, t1]
        Timestamp t0 = win[0], t1 = win[1];

        // SOLO contables; roles 1,4,5; pagados; ventana hoy (Mazatlán)
        final StringBuilder sb = new StringBuilder();
        sb.append("""
        SELECT 
            COALESCE(NULLIF(u.nombre,''), u.usuario) AS nombre,
            DATE(c.fecha) AS fecha,
            ROUND(SUM(d.cantidad * d.precio_unit) * 0.60, 2) AS honorarios
        FROM cobros c
        JOIN Usuarios u                  ON u.id = c.usuario_id
        JOIN cobro_detalle d             ON d.cobro_id = c.id
        JOIN servicios s                 ON s.id = d.servicio_id
        JOIN categorias_servicio cs      ON cs.id = s.categoria_id
        WHERE c.estado = 'pagado'
          AND u.rol_id IN (1,4,5)
          AND c.fecha >= ? AND c.fecha < ?
          AND (
                LOWER(cs.nombre) = 'contabilidad'
                OR LOWER(s.nombre) = 'servicios contables 1'
              )
    """);
        if (sucursalId > 0) sb.append("  AND c.sucursal_id = ?\n");
        sb.append("GROUP BY u.id, DATE(c.fecha)\nORDER BY fecha DESC, nombre ASC");

        try (Connection con = DB.get()) {
            // Intenta fijar TZ de sesión (si el servidor tiene tablas TZ); si no, no pasa nada.
            try (PreparedStatement tz = con.prepareStatement("SET time_zone = 'America/Mazatlan'")) {
                tz.executeUpdate();
            } catch (SQLException ignore) { /* no crítico */ }

            try (PreparedStatement ps = con.prepareStatement(sb.toString())) {
                int idx = 1;
                ps.setTimestamp(idx++, t0);
                ps.setTimestamp(idx++, t1);
                if (sucursalId > 0) ps.setInt(idx++, sucursalId);

                try (ResultSet rs = ps.executeQuery()) {
                    int rows = 0;
                    while (rs.next()) {
                        String nombre    = rs.getString("nombre");
                        BigDecimal honor = rs.getBigDecimal("honorarios");
                        Date fecha       = rs.getDate("fecha");
                        m.addRow(new Object[]{
                                (nombre != null && !nombre.isBlank() ? nombre : "—"),
                                (honor != null ? honor : BigDecimal.ZERO),
                                (fecha != null ? fecha.toString() : "—")
                        });
                        rows++;
                    }
                    if (rows == 0) m.addRow(new Object[]{"—", BigDecimal.ZERO, "—"});
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(panelMain,
                    "Error al cargar honorarios:\n" + e.getMessage(),
                    "BD", JOptionPane.ERROR_MESSAGE);
            if (m.getRowCount() == 0) m.addRow(new Object[]{"—", BigDecimal.ZERO, "—"});
        }
    }

    /** Hoy [00:00, 24:00) en America/Mazatlan, retornado como Timestamps. */
    private static java.sql.Timestamp[] hoyMazatlanWindow() {
        ZoneId zone = ZoneId.of("America/Mazatlan");
        ZonedDateTime start = LocalDate.now(zone).atStartOfDay(zone);
        ZonedDateTime end   = start.plusDays(1);
        return new java.sql.Timestamp[]{
                java.sql.Timestamp.from(start.toInstant()),
                java.sql.Timestamp.from(end.toInstant())
        };
    }


    private static class ModernButtonUI extends javax.swing.plaf.basic.BasicButtonUI {
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
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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

            FontMetrics fm = g2.getFontMetrics();
            int tx = (b.getWidth() - fm.stringWidth(b.getText())) / 2;
            int ty = (b.getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.setColor(text);
            g2.drawString(b.getText(), tx, ty);
            g2.dispose();
        }
    }

    private static class HeaderRenderer extends DefaultTableCellRenderer {
        private final TableCellRenderer base; private final Color bg, fg;
        HeaderRenderer(TableCellRenderer base, Color bg, Color fg) { this.base=base; this.bg=bg; this.fg=fg; }
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            Component comp = base.getTableCellRendererComponent(t, v, s, f, r, c);
            comp.setBackground(bg); comp.setForeground(fg);
            comp.setFont(comp.getFont().deriveFont(Font.BOLD));
            if (comp instanceof JComponent jc) jc.setBorder(new MatteBorder(0,0,1,0, bg.darker()));
            return comp;
        }
    }

    private static class ZebraRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            if (sel) { comp.setBackground(TABLE_SEL_BG); comp.setForeground(TEXT_PRIMARY); }
            else     { comp.setBackground((r % 2 == 0) ? Color.WHITE : TABLE_ALT); comp.setForeground(TEXT_PRIMARY); }
            setBorder(new EmptyBorder(6,8,6,8));
            setHorizontalAlignment(LEFT);
            return comp;
        }
    }

    /** Renderer monetario con zebra y alineación derecha. */
    private static class MoneyZebraRenderer extends DefaultTableCellRenderer {
        @Override protected void setValue(Object value) {
            if (value instanceof Number n) setText(MXN.format(n.doubleValue()));
            else setText(value != null ? value.toString() : "");
        }
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            if (sel) { comp.setBackground(TABLE_SEL_BG); comp.setForeground(TEXT_PRIMARY); }
            else     { comp.setBackground((r % 2 == 0) ? Color.WHITE : TABLE_ALT); comp.setForeground(TEXT_PRIMARY); }
            setBorder(new EmptyBorder(6,8,6,8));
            setHorizontalAlignment(RIGHT);
            return comp;
        }
    }
    // Botón rojo consistente con tu estilo
    private void styleExitButton(JButton b) {
        Color ROJO_BASE    = new Color(0xDC2626);
        Color GRIS_HOVER   = new Color(0xD1D5DB);
        Color GRIS_PRESSED = new Color(0x9CA3AF);
        b.setUI(new Login.ModernButtonUI(ROJO_BASE, GRIS_HOVER, GRIS_PRESSED, Color.BLACK, 22, true));
        b.setBorder(new EmptyBorder(10,18,10,28));
        b.setForeground(Color.WHITE);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
    }

    private void decorateAsCard(JComponent c) {
        if (c == null) return;
        c.setOpaque(true);
        c.setBackground(CARD_BG);
        c.setBorder(new PantallaAdmin.CompoundRoundShadowBorder(14, BORDER_SOFT, new Color(0,0,0,28)));
    }

}
