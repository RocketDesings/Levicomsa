import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import javax.swing.RowFilter;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class SeleccionarCliente implements Refrescable {
    private final Refrescable refrescable;                 // opcional, por si quieres refrescar afuera
    private final BiConsumer<Integer, String> onPick;      // devuelve (clienteId, nombre)

    // UI (del .form)
    private JPanel panelMain;
    private JTable tblClientes;
    private JButton btnCancelar;
    private JPanel panelContenedor;
    private JLabel lblTitulo;
    private JPanel panelBusqueda;
    private JPanel panelTabla;
    private JScrollPane scrTabla;
    private JLabel lblBuscar;
    // Búsqueda global (si existe en tu .form, se usará)
    private JTextField txtBuscar;     // <— crea este campo en el .form
    private JButton buscarButton;     // (opcional)
    private JFrame frame;
    private DefaultTableModel modelo;
    private TableRowSorter<DefaultTableModel> sorter;
    //COLORES
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
    // Índices en el MODELO
    private static final int COL_ID         = 0;
    private static final int COL_NOMBRE     = 1;
    private static final int COL_TELEFONO   = 2;
    private static final int COL_CURP       = 3;
    private static final int COL_PENSIONADO = 4;
    private static final int COL_RFC        = 5;
    private static final int COL_NSS        = 6;
    private static final int COL_CORREO     = 7;

    public SeleccionarCliente(BiConsumer<Integer,String> onPick) { this(null, onPick); }

    public SeleccionarCliente(Refrescable parent, BiConsumer<Integer,String> onPick) {
        this.refrescable = parent;
        this.onPick = onPick;

        frame = new JFrame("Seleccionar Cliente");
        frame.setUndecorated(true);
        frame.setContentPane(panelMain);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        configurarTabla();
        cargarClientesDesdeBD();
        cablearEventos();
        cablearBusqueda(); // usa txtBuscar si existe

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        if (txtBuscar != null) {
            styleSearchField(txtBuscar);
        }
        decorateAsCard(panelMain);
        decorateAsCard(panelContenedor);
        decorateAsCard(panelTabla);
        styleExitButton(btnCancelar);
    }

    private void configurarTabla() {
        // Col 0 = ID (se oculta)
        String[] cols = {"ID","Nombre","Teléfono","CURP","Pensionado","RFC","NSS","Correo","Notas"};
        modelo = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tblClientes.setModel(modelo);

        sorter = new TableRowSorter<>(modelo);
        tblClientes.setRowSorter(sorter);

        if (tblClientes.getColumnModel().getColumnCount() > 0) {
            tblClientes.removeColumn(tblClientes.getColumnModel().getColumn(0)); // oculta ID
        }

        JTableHeader h = tblClientes.getTableHeader();
        h.setDefaultRenderer(new HeaderRenderer(h.getDefaultRenderer(), GREEN_DARK, Color.WHITE));
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, 32));

        tblClientes.setDefaultRenderer(Object.class, new ZebraRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t,Object v,boolean sel,boolean foc,int r,int c){
                Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                if (comp instanceof JComponent jc) jc.setToolTipText(v == null ? "" : v.toString());
                return comp;
            }
        });

        tblClientes.setRowHeight(26);
        tblClientes.getTableHeader().setReorderingAllowed(false);
        tblClientes.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    }


    private void cablearEventos() {
        // Doble clic
        tblClientes.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) seleccionarActualYSalir();
            }
        });

        // ENTER selecciona
        tblClientes.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("ENTER"), "pick");
        tblClientes.getActionMap().put("pick", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { seleccionarActualYSalir(); }
        });

        // Cancelar y ESC cierran
        btnCancelar.addActionListener(e -> frame.dispose());
        panelMain.registerKeyboardAction(
                e -> frame.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        );
    }

    /** Búsqueda global por todas las columnas visibles (si txtBuscar existe). */
    private void cablearBusqueda() {
        if (txtBuscar == null) return; // si no lo tienes en el .form, no pasa nada

        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e){ aplicarFiltroGlobal(); }
            public void removeUpdate(DocumentEvent e){ aplicarFiltroGlobal(); }
            public void changedUpdate(DocumentEvent e){ aplicarFiltroGlobal(); }
        };
        txtBuscar.getDocument().addDocumentListener(dl);
        txtBuscar.addActionListener(e -> aplicarFiltroGlobal());

        if (buscarButton != null) buscarButton.addActionListener(e -> aplicarFiltroGlobal());

        // ESC limpia
        txtBuscar.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ESCAPE"), "clear");
        txtBuscar.getActionMap().put("clear", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { txtBuscar.setText(""); aplicarFiltroGlobal(); }
        });
    }

    private void aplicarFiltroGlobal() {
        if (sorter == null) return;
        String q = txtBuscar.getText().trim();
        if (q.isEmpty()) { sorter.setRowFilter(null); return; }

        String regex = "(?i)" + java.util.regex.Pattern.quote(q);
        List<RowFilter<Object,Object>> cols = new ArrayList<>();
        cols.add(RowFilter.regexFilter(regex, COL_NOMBRE));
        cols.add(RowFilter.regexFilter(regex, COL_TELEFONO));
        cols.add(RowFilter.regexFilter(regex, COL_CURP));
        cols.add(RowFilter.regexFilter(regex, COL_PENSIONADO));
        cols.add(RowFilter.regexFilter(regex, COL_RFC));
        cols.add(RowFilter.regexFilter(regex, COL_NSS));
        cols.add(RowFilter.regexFilter(regex, COL_CORREO));

        sorter.setRowFilter(RowFilter.orFilter(cols));
    }

    private void seleccionarActualYSalir() {
        int viewRow = tblClientes.getSelectedRow();
        if (viewRow == -1) return;
        int modelRow = tblClientes.convertRowIndexToModel(viewRow);

        int clienteId = Integer.parseInt(modelo.getValueAt(modelRow, COL_ID).toString());
        String nombre = modelo.getValueAt(modelRow, COL_NOMBRE).toString();

        if (onPick != null) onPick.accept(clienteId, nombre);
        frame.dispose();
    }

    public void cargarClientesDesdeBD() {
        final String sql = """
        SELECT
          id         AS Id,
          nombre     AS Nombre,
          telefono   AS Telefono,
          CURP       AS CURP,
          pensionado AS Pensionado,
          RFC        AS RFC,
          NSS        AS NSS,
          correo     AS Correo,
          COALESCE(notas,'') AS Notas
        FROM Clientes
        ORDER BY nombre
        """;

        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            modelo.setRowCount(0);
            while (rs.next()) {
                Object[] fila = {
                        rs.getInt("Id"),
                        rs.getString("Nombre"),
                        rs.getString("Telefono"),
                        rs.getString("CURP"),
                        rs.getBoolean("Pensionado") ? "Sí" : "No",
                        rs.getString("RFC"),
                        rs.getString("NSS"),
                        rs.getString("Correo"),
                        rs.getString("Notas")
                };
                modelo.addRow(fila);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Error al cargar clientes:\n" + ex.getMessage());
        }
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
    private void styleSearchField(JTextField tf) {
        if (tf == null) return;
        tf.setOpaque(true);
        tf.setBackground(Color.WHITE);
        tf.setForeground(TEXT_PRIMARY);
        tf.setCaretColor(TEXT_PRIMARY);
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 16));

        Insets pad = new Insets(10, 38, 10, 12); // espacio a la izquierda para el ícono
        tf.setBorder(new SeleccionarCliente2.SearchCompoundBorder(BORDER_SOFT, 18, 1, pad));

        tf.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                tf.setBorder(new SeleccionarCliente2.SearchCompoundBorder(BORDER_FOCUS, 18, 2, pad));
            }
            @Override public void focusLost(FocusEvent e) {
                tf.setBorder(new SeleccionarCliente2.SearchCompoundBorder(BORDER_SOFT, 18, 1, pad));
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
    @Override
    public void refrescarDatos() {
        cargarClientesDesdeBD();
    }
}
