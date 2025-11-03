import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.*;
import javax.swing.RowFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SeleccionarCliente2 implements Refrescable {

    // Índices de columnas (modelo)
    private static final int COL_NOMBRE     = 0;
    private static final int COL_TELEFONO   = 1;
    private static final int COL_CURP       = 2;
    private static final int COL_PENSIONADO = 3;
    private static final int COL_RFC        = 4;
    private static final int COL_NSS        = 5;
    private static final int COL_CORREO     = 6;
    private final int usuarioId;
    private final Refrescable refrescable;

    // UI (inyectada por el .form)
    private JPanel panel1;
    private JTable tblClientes;
    private JTextField txtBuscar;   // búsqueda global
    private JButton btnCancelar;
    private JPanel panelMain;
    private JPanel panelTabla;
    private JLabel lblTitulo;
    private JLabel lblBuscar;

    private JFrame frame;
    private DefaultTableModel modelo;
    private TableRowSorter<DefaultTableModel> sorter;

    // ===== Paleta / tema (alineado con Login y ConsultarCliente) =====
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

    public SeleccionarCliente2(Refrescable parent, int usuarioId) {
        this.refrescable = parent;
        this.usuarioId = usuarioId;

        setUIFont(new Font("Segoe UI", Font.PLAIN, 13));
        construirFrame();   // crea frame + gradiente + card (usa panel1 del .form)
        configurarTabla();  // modelo + renderers
        cablearBusquedaInline();
        cablearEventos();

        cargarClientesDesdeBD();    // llena y ajusta columnas/ventana

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // ==================== FRAME + DISEÑO ====================
    private void construirFrame() {
        frame = new JFrame("Seleccionar Cliente");
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Esquinas redondeadas
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                frame.setShape(new RoundRectangle2D.Double(
                        0, 0, frame.getWidth(), frame.getHeight(), 24, 24
                ));
            }
        });

        // Fondo en gradiente
        GradientPanel root = new GradientPanel(BG_TOP, BG_BOT);
        root.setLayout(new GridBagLayout());

        // Card central con sombra
        CardPanel card = new CardPanel();
        card.setLayout(new BorderLayout());

        // Asegura panel1
        if (panel1 == null) panel1 = new JPanel();
        panel1.setOpaque(true);
        panel1.setBackground(CARD_BG);
        panel1.setBorder(new EmptyBorder(16, 18, 16, 18));

        // Estilo del buscador y botón
        if (txtBuscar != null) {
            styleSearchField(txtBuscar);
            setPlaceholderIfEmpty(txtBuscar, "Buscar por nombre, teléfono, CURP, RFC, correo o NSS");
        }
        styleExitButton(btnCancelar);
        decorateAsCard(panelMain);
        decorateAsCard(panelTabla);
        decorateAsCard(panel1);
        card.add(panel1, BorderLayout.CENTER);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gbc.gridy = 0;
        gbc.insets = new Insets(24, 24, 24, 24);
        gbc.fill = GridBagConstraints.BOTH;
        root.add(card, gbc);

        frame.setContentPane(root);

        // Permitir arrastrar la ventana por la card/panel
        MouseAdapter dragger = new MouseAdapter() {
            Point click;
            @Override public void mousePressed(MouseEvent e) { click = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) {
                Point p = e.getLocationOnScreen();
                frame.setLocation(p.x - click.x, p.y - click.y);
            }
        };
        card.addMouseListener(dragger);
        card.addMouseMotionListener(dragger);
        panel1.addMouseListener(dragger);
        panel1.addMouseMotionListener(dragger);
    }

    // ---------------- UI / Tabla ----------------
    private void configurarTabla() {
        String[] columnas = {"Nombre", "Teléfono", "CURP", "Pensionado", "RFC", "NSS", "Correo"};
        modelo = new DefaultTableModel(columnas, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int col) { return String.class; }
        };
        tblClientes.setModel(modelo);

        sorter = new TableRowSorter<>(modelo);
        tblClientes.setRowSorter(sorter);

        // Mostrar todo el contenido + scroll horizontal cuando haga falta
        tblClientes.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        tblClientes.getTableHeader().setReorderingAllowed(false);
        tblClientes.setRowHeight(26);
        tblClientes.setShowGrid(false);
        tblClientes.setIntercellSpacing(new Dimension(0,0));

        // Header estilizado
        JTableHeader h = tblClientes.getTableHeader();
        h.setDefaultRenderer(new HeaderRenderer(h.getDefaultRenderer(), GREEN_DARK, Color.WHITE));
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, 32));

        // Zebra + tooltip con texto completo
        tblClientes.setDefaultRenderer(Object.class, new ZebraRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t,Object v,boolean sel,boolean foc,int r,int c){
                Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                if (comp instanceof JComponent jc) jc.setToolTipText(v == null ? "" : v.toString());
                return comp;
            }
        });

        // Configurar scrollpane contenedor (lo trae el .form)
        JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, tblClientes);
        if (sp != null) {
            sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            sp.setBorder(new MatteBorder(1,1,1,1, BORDER_SOFT));
            sp.getViewport().setBackground(CARD_BG);
        }
    }

    private void cablearEventos() {
        // Doble clic
        tblClientes.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) abrirModificarSeleccionado();
            }
        });

        // Enter abre selección
        tblClientes.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("ENTER"), "open");
        tblClientes.getActionMap().put("open", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                abrirModificarSeleccionado();
            }
        });

        // Cancelar
        if (btnCancelar != null) btnCancelar.addActionListener(e -> frame.dispose());

        // ESC cierra
        panel1.registerKeyboardAction(
                e -> frame.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        );
    }

    // ---------------- Búsqueda ----------------
    private void cablearBusquedaInline() {
        if (txtBuscar == null) return;

        txtBuscar.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { aplicarFiltro(txtBuscar.getText().trim()); }
            public void removeUpdate(DocumentEvent e)  { aplicarFiltro(txtBuscar.getText().trim()); }
            public void changedUpdate(DocumentEvent e) { aplicarFiltro(txtBuscar.getText().trim()); }
        });

        txtBuscar.addActionListener(e -> aplicarFiltro(txtBuscar.getText().trim()));

        // ESC para limpiar
        txtBuscar.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "clear");
        txtBuscar.getActionMap().put("clear", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                txtBuscar.setText("");
                aplicarFiltro("");
            }
        });
    }

    private void aplicarFiltro(String q) {
        if (sorter == null) return;
        if (q == null || q.isBlank()) {
            sorter.setRowFilter(null);
            return;
        }
        String regex = "(?i)" + java.util.regex.Pattern.quote(q);
        List<RowFilter<Object,Object>> cols = new ArrayList<>();
        for (int c = 0; c < modelo.getColumnCount(); c++) {
            cols.add(RowFilter.regexFilter(regex, c));
        }
        sorter.setRowFilter(RowFilter.orFilter(cols));
    }

    private void abrirModificarSeleccionado() {
        int viewRow = tblClientes.getSelectedRow();
        if (viewRow < 0) return;

        int row = tblClientes.convertRowIndexToModel(viewRow);

        String nombre     = getStr(row, COL_NOMBRE);
        String telefono   = getStr(row, COL_TELEFONO);
        String curp       = getStr(row, COL_CURP);
        String pensionado = getStr(row, COL_PENSIONADO);
        String rfc        = getStr(row, COL_RFC);
        String correo     = getStr(row, COL_CORREO);
        // Nota: NSS se muestra/filtra, pero el constructor legacy no lo recibe.

        new ModificarCliente(refrescable, nombre, telefono, curp, rfc, correo, pensionado, usuarioId);
    }

    private String getStr(int row, int col) {
        Object v = modelo.getValueAt(row, col);
        return v == null ? "" : v.toString();
    }

    // ---------------- Datos ----------------
    public void cargarClientesDesdeBD() {
        final String sql = "SELECT nombre, telefono, CURP, pensionado, RFC, NSS, correo FROM Clientes ORDER BY nombre";

        try (Connection conn = DB.get();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            modelo.setRowCount(0);

            while (rs.next()) {
                String nombre     = rs.getString(1);
                String telefono   = rs.getString(2);
                String curp       = rs.getString(3);
                String pensionado = rs.getBoolean(4) ? "Sí" : "No";
                String rfc        = rs.getString(5);
                String nss        = rs.getString(6);
                String correo     = rs.getString(7);
                modelo.addRow(new Object[]{nombre, telefono, curp, pensionado, rfc, nss, correo});
            }

            // Ajusta anchos por contenido y tamaño de ventana
            packAllColumns(tblClientes, 16, 120, 420); // margen=16, min=120, max=420 por columna
            ajustarVentanaSegunTabla();

        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(panel1, "Error al cargar clientes: " + e.getMessage());
        }
    }

    // ---------------- Refrescable ----------------
    @Override
    public void refrescarDatos() {
        cargarClientesDesdeBD();
    }

    // ==================== RENDERERS / ESTILO ====================
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
            setHorizontalAlignment(SwingConstants.LEFT);
            return comp;
        }
    }

    // ==================== Ajustes de columnas/ventana ====================
    /** Calcula el ancho óptimo de cada columna según header + celdas (muestra todo). */
    private void packAllColumns(JTable table, int margin, int minWidth, int maxWidth) {
        TableColumnModel colModel = table.getColumnModel();
        for (int col = 0; col < colModel.getColumnCount(); col++) {
            int width = 0;

            // Header
            TableCellRenderer hRenderer = table.getTableHeader().getDefaultRenderer();
            Component hComp = hRenderer.getTableCellRendererComponent(table,
                    table.getColumnModel().getColumn(col).getHeaderValue(), false, false, 0, col);
            width = Math.max(width, hComp.getPreferredSize().width);

            // Celdas
            for (int row = 0; row < table.getRowCount(); row++) {
                TableCellRenderer r = table.getCellRenderer(row, col);
                Component c = r.getTableCellRendererComponent(table, table.getValueAt(row, col), false, false, row, col);
                width = Math.max(width, c.getPreferredSize().width);
            }

            width += margin; // padding
            width = Math.max(minWidth, Math.min(width, maxWidth));
            colModel.getColumn(col).setPreferredWidth(width);
        }
    }

    /** Ajusta el tamaño de la ventana para que quepa bien la tabla sin “comerse” la pantalla. */
    private void ajustarVentanaSegunTabla() {
        JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, tblClientes);
        if (sp == null) { frame.pack(); return; }

        // Suma de anchos de columnas
        int totalCols = 0;
        TableColumnModel cm = tblClientes.getColumnModel();
        for (int i = 0; i < cm.getColumnCount(); i++) totalCols += cm.getColumn(i).getPreferredWidth();

        int tableH = Math.min(520, Math.max(300,
                tblClientes.getRowHeight() * Math.min(tblClientes.getRowCount() + 1, 12) + 60));
        int tableW = Math.min(totalCols + 28, Toolkit.getDefaultToolkit().getScreenSize().width - 200);

        sp.setPreferredSize(new Dimension(tableW, tableH));
        frame.pack();

        // No abarcar toda la pantalla
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        int maxW = (int)(scr.width * 0.9);
        int maxH = (int)(scr.height * 0.85);
        frame.setSize(Math.min(frame.getWidth(), maxW), Math.min(frame.getHeight(), maxH));
        frame.setLocationRelativeTo(null);
    }

    // ==================== Buscador: estilo pill con lupa ====================
    private void styleSearchField(JTextField tf) {
        if (tf == null) return;
        tf.setOpaque(true);
        tf.setBackground(Color.WHITE);
        tf.setForeground(TEXT_PRIMARY);
        tf.setCaretColor(TEXT_PRIMARY);
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 16));

        Insets pad = new Insets(10, 38, 10, 12); // espacio a la izquierda para el ícono
        tf.setBorder(new SearchCompoundBorder(BORDER_SOFT, 18, 1, pad));

        tf.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                tf.setBorder(new SearchCompoundBorder(BORDER_FOCUS, 18, 2, pad));
            }
            @Override public void focusLost(FocusEvent e) {
                tf.setBorder(new SearchCompoundBorder(BORDER_SOFT, 18, 1, pad));
            }
        });
    }

    private void setPlaceholderIfEmpty(JTextField tf, String ph) {
        if (tf == null || ph == null) return;
        if (tf.getText() == null || tf.getText().isBlank()) {
            tf.setForeground(TEXT_MUTED);
            tf.setText(ph);
        }
        tf.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (tf.getText().equals(ph)) { tf.setText(""); tf.setForeground(TEXT_PRIMARY); }
            }
            @Override public void focusLost(FocusEvent e) {
                if (tf.getText().isBlank()) { tf.setForeground(TEXT_MUTED); tf.setText(ph); }
            }
        });
    }

    /** Borde redondeado con icono de lupa a la izquierda + padding interno. */
    static class SearchCompoundBorder extends javax.swing.border.CompoundBorder {
        SearchCompoundBorder(Color line, int arc, int thickness, Insets innerPad) {
            super(new SearchRoundedLineBorder(line, arc, thickness), new EmptyBorder(innerPad));
        }
    }
    static class SearchRoundedLineBorder extends javax.swing.border.AbstractBorder {
        private final Color color; private final int arc; private final int thickness;
        SearchRoundedLineBorder(Color color, int arc, int thickness) { this.color=color; this.arc=arc; this.thickness=thickness; }

        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Borde pill
            g2.setColor(color);
            for (int i = 0; i < thickness; i++) {
                g2.drawRoundRect(x + i, y + i, w - 1 - 2*i, h - 1 - 2*i, arc, arc);
            }

            // Dibujar lupa (círculo + mango)
            int cx = x + 14;                  // posición izquierda
            int cy = y + h/2 - 6;             // centrado vertical
            g2.drawOval(cx, cy, 12, 12);
            g2.drawLine(cx + 10, cy + 10, cx + 16, cy + 16);

            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) { return new Insets(thickness, thickness, thickness, thickness); }
        @Override public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(thickness, thickness, thickness, thickness); return insets;
        }
    }

    // ==================== UI helpers reutilizables ====================
    static class ModernButtonUI extends BasicButtonUI {
        private final Color bg, hover, press, fg; private final int arc; private final boolean filled;
        ModernButtonUI(Color bg, Color hover, Color press, Color fg, int arc, boolean filled) {
            this.bg=bg; this.hover=hover; this.press=press; this.fg=fg; this.arc=arc; this.filled=filled;
        }
        @Override public void installUI(JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setOpaque(false);
            b.setRolloverEnabled(true);
            b.setFocusPainted(false);
            b.setForeground(fg);
        }
        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ButtonModel m = b.getModel();
            Color fill = m.isPressed() ? press : (m.isRollover() ? hover : bg);
            if (filled) {
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), arc, arc);
            } else {
                g2.setColor(new Color(255, 255, 255, 100));
                g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, arc, arc);
            }
            g2.dispose();
            super.paint(g, c);
        }
    }

    static class GradientPanel extends JPanel {
        private final Color top, bot;
        GradientPanel(Color top, Color bot) { this.top = top; this.bot = bot; setOpaque(true); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            GradientPaint gp = new GradientPaint(0, 0, top, 0, getHeight(), bot);
            g2.setPaint(gp);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Card con sombra suave y bordes redondeados. */
    static class CardPanel extends JPanel {
        private final int arc = 20;
        CardPanel() { setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            // sombra suave
            for (int i = 0; i < 10; i++) {
                float alpha = 0.06f * (10 - i);
                g2.setColor(new Color(0, 0, 0, (int) (alpha * 255)));
                g2.fillRoundRect(10 - i, 12 - i, w - (10 - i) * 2, h - (12 - i) * 2, arc + i, arc + i);
            }
            // fondo tarjeta
            g2.setColor(CARD_BG);
            g2.fillRoundRect(0, 0, w, h, arc, arc);
            g2.setColor(new Color(0,0,0,30));
            g2.drawRoundRect(0, 0, w-1, h-1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ===== Fuente global helper =====
    private void setUIFont(Font f) {
        try {
            var keys = UIManager.getDefaults().keys();
            while (keys.hasMoreElements()) {
                Object k = keys.nextElement();
                Object v = UIManager.get(k);
                if (v instanceof Font) UIManager.put(k, f);
            }
        } catch (Exception ignored) {}
    }

    private void styleExitButton(JButton b) {
        // Botón rojo consistente con tu estilo
        Color ROJO_BASE    = new Color(0xDC2626);
        Color GRIS_HOVER   = new Color(0xD1D5DB);
        Color GRIS_PRESSED = new Color(0x9CA3AF);
        b.setUI(new Login.ModernButtonUI(ROJO_BASE, GRIS_HOVER, GRIS_PRESSED, Color.BLACK, 22, true));
        b.setBorder(new EmptyBorder(10,18,10,28));
        b.setForeground(Color.WHITE);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    private void decorateAsCard(JComponent c) {
        if (c == null) return;
        c.setOpaque(true);
        c.setBackground(CARD_BG);
        c.setBorder(new PantallaAdmin.CompoundRoundShadowBorder(14, BORDER_SOFT, new Color(0,0,0,28)));
    }
}
