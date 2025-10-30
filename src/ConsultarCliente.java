import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.geom.RoundRectangle2D;
import java.sql.*;
import java.time.format.DateTimeFormatter;

public class ConsultarCliente {
    // ====== componentes del .form ======
    private JPanel panelMain;
    private JPanel panelBotones;
    private JPanel panelInfo;
    private JPanel panelTabla;
    private JScrollPane scrTabla;
    private JTable tblDatosCliente;
    private JButton btnSalir;

    // ====== estado ======
    private JDialog dialog;
    private static JDialog instancia; // evita duplicados
    private final int clienteId;
    private final String nombreCliente;

    // ====== formato/tema ======
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color GREEN_DARK   = new Color(0x0A6B2A);
    private static final Color GREEN_BASE   = new Color(0x16A34A);
    private static final Color GREEN_SOFT   = new Color(0x22C55E);
    private static final Color BG_CANVAS    = new Color(0xF3F4F6);
    private static final Color CARD_BG      = Color.WHITE;
    private static final Color TEXT_PRIMARY = new Color(0x111827);
    private static final Color BORDER_SOFT  = new Color(0xE5E7EB);
    private static final Color TABLE_ALT    = new Color(0xF9FAFB);
    private static final Color TABLE_SEL_BG = new Color(0xE6F7EE);

    // ====== API ======
    public static void mostrar(Window owner, int clienteId, String nombreCliente) {
        if (instancia != null && instancia.isDisplayable()) {
            instancia.setAlwaysOnTop(true);
            instancia.toFront();
            instancia.requestFocus();
            instancia.setAlwaysOnTop(false);
            return;
        }
        new ConsultarCliente(owner, clienteId, nombreCliente);
    }

    public ConsultarCliente(Window owner, int clienteId, String nombreCliente) {
        this.clienteId = clienteId;
        this.nombreCliente = nombreCliente != null ? nombreCliente : "Cliente";

        dialog = new JDialog(owner, "Historial de cobros — " + this.nombreCliente,
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setContentPane(panelMain);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setMinimumSize(new Dimension(900, 560));
        dialog.setLocationRelativeTo(owner != null && owner.isShowing() ? owner : null);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e)  { instancia = null; }
            @Override public void windowClosing(java.awt.event.WindowEvent e) { instancia = null; }
        });
        instancia = dialog;

        aplicarEstilo();
        prepararTabla();
        cargarCobros();

        btnSalir.addActionListener(e -> dialog.dispose());

        dialog.setVisible(true);
    }

    // ====== UI/estilo ======
    private void aplicarEstilo() {
        if (panelMain  != null) panelMain.setBackground(BG_CANVAS);
        if (panelTabla != null) decorateAsCard(panelTabla);
        if (panelInfo  != null) decorateAsCard(panelInfo);
        if (panelBotones != null) decorateAsCard(panelBotones);

        if (scrTabla != null) {
            scrTabla.setBorder(new MatteBorder(1,1,1,1, BORDER_SOFT));
            scrTabla.getViewport().setBackground(CARD_BG);
        }

        stylePrimaryButton(btnSalir, new Color(0xDC2626)); // rojo salir
    }
    private void decorateAsCard(JComponent c) {
        c.setOpaque(true);
        c.setBackground(CARD_BG);
        c.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1,1,1,1, BORDER_SOFT),
                new EmptyBorder(10,10,10,10)
        ));
    }
    private void stylePrimaryButton(JButton b, Color base) {
        if (b == null) return;
        b.setUI(new ModernButtonUI(base, base.brighter(), base.darker(), Color.WHITE, 12, true));
        b.setBorder(new EmptyBorder(10,18,10,18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    private static class ModernButtonUI extends BasicButtonUI {
        private final Color base, hover, pressed, text; private final int radius; private final boolean filled;
        ModernButtonUI(Color base, Color hover, Color pressed, Color text, int radius, boolean filled) {
            this.base=base; this.hover=hover; this.pressed=pressed; this.text=text; this.radius=radius; this.filled=filled;
        }
        @Override public void installUI(JComponent c){ super.installUI(c); ((AbstractButton)c).setOpaque(false); ((AbstractButton)c).setBorderPainted(false); }
        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b=(AbstractButton)c; Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ButtonModel m=b.getModel(); Color fill=m.isPressed()?pressed:(m.isRollover()?hover:base);
            Shape rr=new RoundRectangle2D.Float(0,0,b.getWidth(),b.getHeight(),24,24);
            g2.setColor(fill); g2.fill(rr); g2.setColor(new Color(0,0,0,25)); g2.draw(rr);
            g2.setColor(text); FontMetrics fm=g2.getFontMetrics();
            int tx=(b.getWidth()-fm.stringWidth(b.getText()))/2, ty=(b.getHeight()+fm.getAscent()-fm.getDescent())/2;
            g2.drawString(b.getText(), tx, ty); g2.dispose();
        }
    }
    private static class HeaderRenderer extends DefaultTableCellRenderer {
        private final TableCellRenderer base; private final Color bg, fg;
        HeaderRenderer(TableCellRenderer base, Color bg, Color fg){ this.base=base; this.bg=bg; this.fg=fg; }
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
            if (c==3 && v instanceof Number) setHorizontalAlignment(SwingConstants.RIGHT);
            else setHorizontalAlignment(SwingConstants.LEFT);
            return comp;
        }
    }

    private void prepararTabla() {
        DefaultTableModel m = new DefaultTableModel(
                new String[]{"Servicio", "Sucursal", "Atendió", "Monto", "Fecha"}, 0) {
            @Override public boolean isCellEditable(int r,int c){ return false; }
            @Override public Class<?> getColumnClass(int c){ return c==3 ? Double.class : String.class; }
        };
        tblDatosCliente.setModel(m);
        tblDatosCliente.setRowHeight(26);
        tblDatosCliente.setShowGrid(false);
        tblDatosCliente.setIntercellSpacing(new Dimension(0,0));
        JTableHeader h = tblDatosCliente.getTableHeader();
        h.setDefaultRenderer(new HeaderRenderer(h.getDefaultRenderer(), GREEN_DARK, Color.WHITE));
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, 32));
        tblDatosCliente.setDefaultRenderer(Object.class, new ZebraRenderer());

        int[] w = {280, 180, 160, 100, 160};
        for (int i=0; i< Math.min(w.length, tblDatosCliente.getColumnCount()); i++) {
            tblDatosCliente.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
        }
    }

    // ====== Datos ======
    private void cargarCobros() {
        final String sql = """
            SELECT
                COALESCE(s.nombre,'') AS sucursal,
                COALESCE(u.nombre, t.nombre, u.usuario) AS atendio,
                c.total,
                c.fecha,
                COALESCE(c.notas,'') AS notas
            FROM cobros c
            LEFT JOIN sucursales s ON s.id = c.sucursal_id
            LEFT JOIN Usuarios u   ON u.id = c.usuario_id
            LEFT JOIN trabajadores t ON t.id = u.trabajador_id
            WHERE c.cliente_id = ?
            ORDER BY c.fecha DESC
        """;

        DefaultTableModel m = (DefaultTableModel) tblDatosCliente.getModel();
        m.setRowCount(0);

        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String suc    = rs.getString("sucursal");
                    String atend  = rs.getString("atendio");
                    double monto  = rs.getDouble("total");
                    Timestamp ts  = rs.getTimestamp("fecha");
                    String fecha  = ts != null ? DF.format(ts.toLocalDateTime()) : "";
                    String notas  = rs.getString("notas");
                    String serv   = extraerServicio(notas);
                    m.addRow(new Object[]{serv, suc, atend, monto, fecha});
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(dialog, "Error al cargar cobros: " + ex.getMessage());
        }
    }

    /** Intenta leer "Servicio: XYZ" desde el campo notas. */
    private String extraerServicio(String notas) {
        if (notas == null) return "—";
        String n = notas.trim();
        int idx = n.toLowerCase().indexOf("servicio:");
        if (idx >= 0) {
            String tail = n.substring(idx + "servicio:".length()).trim();
            // corta en salto de línea o punto final si existe
            int corte = tail.indexOf('\n');
            if (corte < 0) corte = tail.indexOf('\r');
            if (corte < 0) corte = tail.indexOf(';');
            if (corte < 0) corte = tail.indexOf('.');
            if (corte > 0) tail = tail.substring(0, corte);
            return tail.trim();
        }
        return n.isEmpty() ? "—" : n;
    }
}
