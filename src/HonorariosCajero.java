import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.geom.RoundRectangle2D;

/** UI simple para Honorarios del Cajero (abre desde CorteCaja.btnHonorarios). */
public class HonorariosCajero {

    // ===== componentes (del .form). Si el .form no los inicializa, hago un fallback en ensureUI() =====
    JPanel panelMain;
    private JTable tblHonorarios;
    private JScrollPane scrHonorarios;
    private JPanel panelTablaa;   // respeta el nombre que pasaste
    private JPanel panelBotones;
    private JButton btnSalir;

    // ===== contexto =====
    private final int sucursalId;
    private final int usuarioId;

    // ===== paleta consistente =====
    private static final Color GREEN_DARK   = new Color(0x0A6B2A);
    private static final Color GREEN_BASE   = new Color(0x16A34A);
    private static final Color GREEN_SOFT   = new Color(0x22C55E);
    private static final Color BG_CANVAS    = new Color(0xF3F4F6);
    private static final Color TEXT_PRIMARY = new Color(0x111827);
    private static final Color TABLE_ALT    = new Color(0xF9FAFB);
    private static final Color TABLE_SEL_BG = new Color(0xE6F7EE);

    // ===== ctor =====
    public HonorariosCajero(int sucursalId, int usuarioId) {
        this.sucursalId = sucursalId;
        this.usuarioId  = usuarioId;

        ensureUI();     // crea UI si el .form no lo hizo
        applyTheme();   // pinta estilos
        setupTable();   // modelo y renderers
        wireActions();  // botón salir, etc.
    }

    /** Crea el diálogo MODELESS listo para usarse. Lo devuelve sin mostrar (para que el caller decida). */
    public static JDialog createDialog(Window owner, int sucursalId, int usuarioId) {
        HonorariosCajero ui = new HonorariosCajero(sucursalId, usuarioId);

        JDialog dlg = new JDialog(owner, "Honorarios del Cajero", Dialog.ModalityType.MODELESS);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        // Si por .form existe panelMain, se usa; si no, ya lo generó ensureUI()
        JComponent content = ui.panelMain != null ? ui.panelMain : new JPanel();
        dlg.setContentPane(content);
        dlg.pack();
        dlg.setResizable(true);
        dlg.setLocationRelativeTo((owner != null && owner.isShowing()) ? owner : null);

        // Conectar botón salir si existe
        if (ui.btnSalir != null) {
            ui.btnSalir.addActionListener(e -> dlg.dispose());
        }

        return dlg;
    }

    // ================== UI programática de respaldo ==================
    private void ensureUI() {
        if (panelMain != null) return; // ya existe desde el .form
        panelMain = new JPanel(new BorderLayout(12, 12));
        panelMain.setBorder(new EmptyBorder(12, 12, 12, 12));

        // título sutil
        JLabel title = new JLabel("Honorarios del Cajero");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(TEXT_PRIMARY);
        title.setBorder(new MatteBorder(0, 0, 1, 0, new Color(0xE5E7EB)));
        panelMain.add(title, BorderLayout.NORTH);

        // tabla
        panelTablaa = new JPanel(new BorderLayout());
        tblHonorarios = new JTable();
        scrHonorarios = new JScrollPane(tblHonorarios);
        panelTablaa.add(scrHonorarios, BorderLayout.CENTER);
        panelMain.add(panelTablaa, BorderLayout.CENTER);

        // botones
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
    }

    private void setupTable() {
        if (tblHonorarios == null) return;

        DefaultTableModel m = new DefaultTableModel(
                new String[]{"Fecha", "Concepto", "Importe", "Notas"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return switch (c) {
                    case 2 -> Double.class;  // Importe
                    default -> String.class;
                };
            }
        };
        tblHonorarios.setModel(m);
        tblHonorarios.setRowHeight(26);
        tblHonorarios.setShowGrid(false);
        tblHonorarios.setIntercellSpacing(new Dimension(0,0));
        tblHonorarios.getTableHeader().setReorderingAllowed(false);

        // Header estilizado
        JTableHeader h = tblHonorarios.getTableHeader();
        h.setDefaultRenderer(new HeaderRenderer(h.getDefaultRenderer(), GREEN_DARK, Color.WHITE));
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, 32));

        // Zebra renderer
        tblHonorarios.setDefaultRenderer(Object.class, new ZebraRenderer());

        // Anchos
        int[] w = {140, 260, 120, 400};
        for (int i = 0; i < Math.min(w.length, tblHonorarios.getColumnCount()); i++) {
            tblHonorarios.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
        }

        // Placeholder (fila ‘sin datos’) si está vacío
        if (m.getRowCount() == 0) {
            m.addRow(new Object[]{"—", "Sin registros", 0.0, "—"});
        }
    }

    private void wireActions() {
        // btnSalir se enlaza en createDialog para usar dispose() del JDialog
        // Aquí puedes cablear más acciones locales si añades botones extra.
    }

    // ================== Estilos reusables ==================
    private void styleExitButton(JButton b) {
        if (b == null) return;
        Color ROJO_BASE = new Color(0xDC2626);
        Color GRIS_HOV  = new Color(0xD1D5DB);
        Color GRIS_PRE  = new Color(0x9CA3AF);
        b.setUI(new ModernButtonUI(ROJO_BASE, GRIS_HOV, GRIS_PRE, Color.BLACK, 12, true));
        b.setBorder(new EmptyBorder(10,18,10,18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
            if (c == 2 && v instanceof Number) setHorizontalAlignment(SwingConstants.RIGHT);
            else setHorizontalAlignment(SwingConstants.LEFT);
            return comp;
        }
    }
}