import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import javax.swing.RowFilter;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.*;

public class CRUDServicios {
    private JPanel panelTabla;                 // del .form (contiene la tabla)
    private JScrollPane scrTablaServicios;     // del .form (scroll de la tabla)
    private JTable tblServicios;               // del .form
    private JPanel panelBotones;               // del .form (botonera)
    private JButton btnAgregarServicio;        // del .form
    private JButton btnModificarServicio;      // del .form
    private JButton btnEliminarServicio;       // del .form
    private JButton btnCancelar;               // del .form

    private JComboBox<CategoriaItem> cmbCategoria; // del .form (filtro por categoría)
    private JPanel panelCategoria;
    private JPanel panelMain;
    private JLabel lblEtiqueta;

    // ===== contexto =====
    private final int usuarioId;
    private final int sucursalId;

    // ===== tabla =====
    private DefaultTableModel modelo;
    private TableRowSorter<DefaultTableModel> sorter;

    // índice de la columna OCULTA con el categoria_id
    private static final int COL_CAT_ID_HIDDEN = 6;

    public CRUDServicios(int usuarioId, int sucursalId) {
        this.usuarioId = usuarioId;
        this.sucursalId = sucursalId;

        configurarTabla();
        cargarCategoriasEnCombo();
        cargarServicios();

        // ---- Estilo (más compacto) ----
        compactLookAndFeel();

        // filtro por combo
        if (cmbCategoria != null) {
            cmbCategoria.addActionListener(e -> aplicarFiltroCategoria());
        }

        // salir/cerrar
        if (btnCancelar != null) {
            btnCancelar.addActionListener(e -> {
                Window w = SwingUtilities.getWindowAncestor(btnCancelar);
                if (w instanceof JDialog d) d.dispose();
            });
        }

        // agregar
        if (btnAgregarServicio != null) {
            btnAgregarServicio.addActionListener(e -> {
                Window owner = SwingUtilities.getWindowAncestor(btnAgregarServicio);
                JDialog d = AgregarServicio.createDialog(
                        owner,
                        usuarioId,
                        sucursalId,
                        this::cargarServicios   // refresca al guardar
                );
                d.setVisible(true);
            });
        }

        // modificar
        if (btnModificarServicio != null) {
            btnModificarServicio.addActionListener(e -> {
                Window owner = SwingUtilities.getWindowAncestor(btnModificarServicio);
                ModificarServicio.open(owner, usuarioId, sucursalId, this::cargarServicios);
            });
        }

        // eliminar
        if (btnEliminarServicio != null) {
            btnEliminarServicio.addActionListener(e -> eliminarServicioSeleccionado());
        }
    }

    // ============== DIALOG HELPER ==============
    /** Construye un root propio para el diálogo (evita layouts raros). */
    private JComponent buildRoot() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(8, 10, 8, 10));

        if (panelCategoria != null)         root.add(panelCategoria, BorderLayout.NORTH);
        if (scrTablaServicios != null)      root.add(scrTablaServicios, BorderLayout.CENTER);
        else if (panelTabla != null)        root.add(panelTabla, BorderLayout.CENTER);
        if (panelBotones != null)           root.add(panelBotones, BorderLayout.SOUTH);

        return root;
    }

    /** Crea el diálogo de CRUD Servicios centrado. */
    public static JDialog createDialog(Window owner, int usuarioId, int sucursalId) {
        CRUDServicios ui = new CRUDServicios(usuarioId, sucursalId);

        JDialog d = new JDialog(owner, "Servicios", Dialog.ModalityType.MODELESS);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(ui.panelMain);

        // tamaño antes de centrar
        d.setMinimumSize(new Dimension(900, 520));
        d.pack();

        // centra sobre el owner si está visible; si no, al centro de pantalla
        if (owner != null && owner.isShowing()) d.setLocationRelativeTo(owner);
        else d.setLocationRelativeTo(null);

        return d;
    }

    // ============== TABLA ==============
    private void configurarTabla() {
        // Visibles: ID, Categoría, Nombre, Descripción, Precio, Activo
        // Oculta:   __catId  (para filtrar por combo)
        String[] cols = {"ID", "Categoría", "Nombre", "Descripción", "Precio", "Activo", "__catId"};
        modelo = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return switch (c) {
                    case 0, COL_CAT_ID_HIDDEN -> Integer.class; // ID y oculto
                    default -> String.class;
                };
            }
        };
        tblServicios.setModel(modelo);
        sorter = new TableRowSorter<>(modelo);
        tblServicios.setRowSorter(sorter);

        tblServicios.setRowHeight(26);
        tblServicios.setShowGrid(false);
        tblServicios.setIntercellSpacing(new Dimension(0, 0));

        JTableHeader h = tblServicios.getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, 32));

        int[] widths = {60, 160, 260, 360, 100, 80, 0};
        for (int i = 0; i < Math.min(widths.length, tblServicios.getColumnCount()); i++) {
            tblServicios.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // Ocultar la columna del catId
        ocultarColumna(COL_CAT_ID_HIDDEN);
        // Renderer zebra y alineaciones
        ZebraRenderer zebra = new ZebraRenderer(tblServicios);
// Para todas las clases (texto y números)
        tblServicios.setDefaultRenderer(Object.class, zebra);
        tblServicios.setDefaultRenderer(Number.class, zebra);
// Asegura que la columna ID use este renderer (alineación izquierda)
        tblServicios.getColumnModel().getColumn(0).setCellRenderer(zebra);

    }

    private void ocultarColumna(int col) {
        if (col < 0 || col >= tblServicios.getColumnCount()) return;
        tblServicios.getColumnModel().getColumn(col).setMinWidth(0);
        tblServicios.getColumnModel().getColumn(col).setMaxWidth(0);
        tblServicios.getColumnModel().getColumn(col).setPreferredWidth(0);
    }

    /** Carga servicios y llena la tabla con nombre de categoría. */
    public final void cargarServicios() {
        final String sql = """
            SELECT s.id, s.categoria_id, c.nombre AS categoria, s.nombre, s.descripcion, s.precio, s.activo
            FROM servicios s
            LEFT JOIN categorias_servicio c ON c.id = s.categoria_id
            ORDER BY s.nombre
        """;
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            modelo.setRowCount(0);
            while (rs.next()) {
                int id          = rs.getInt("id");
                int categoriaId = rs.getInt("categoria_id");
                String catName  = nvl(rs.getString("categoria"));
                String nombre   = nvl(rs.getString("nombre"));
                String descr    = nvl(rs.getString("descripcion"));
                BigDecimal p    = rs.getBigDecimal("precio");
                String precioTx = (p != null ? p.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString() : "0.00");
                String activoTx = (rs.getInt("activo") == 1) ? "1" : "0";

                modelo.addRow(new Object[]{ id, catName, nombre, descr, precioTx, activoTx, categoriaId });
            }
            aplicarFiltroCategoria();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(panelTabla, "Error al cargar servicios: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ============== CATEGORÍAS (combo + filtro) ==============
    private void cargarCategoriasEnCombo() {
        if (cmbCategoria == null) return;
        DefaultComboBoxModel<CategoriaItem> model = new DefaultComboBoxModel<>();
        model.addElement(CategoriaItem.TODAS());

        final String sql = "SELECT id, nombre FROM categorias_servicio WHERE activo = 1 ORDER BY nombre";
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                model.addElement(new CategoriaItem(rs.getInt("id"), nvl(rs.getString("nombre"))));
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(panelTabla, "Error al cargar categorías: " + e.getMessage());
        }
        cmbCategoria.setModel(model);
        cmbCategoria.setSelectedIndex(0);
    }

    private void aplicarFiltroCategoria() {
        if (sorter == null || cmbCategoria == null) return;
        CategoriaItem it = (CategoriaItem) cmbCategoria.getSelectedItem();
        if (it == null || it.id == null) { sorter.setRowFilter(null); return; }

        final int categoriaId = it.id;
        sorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                Object v = entry.getValue(COL_CAT_ID_HIDDEN);
                if (v == null) return false;
                int rowCat = (v instanceof Integer) ? (Integer) v : Integer.parseInt(String.valueOf(v));
                return rowCat == categoriaId;
            }
        });
    }

    // ============== ACCIONES ==============
    private Integer getSelectedServicioId() {
        int viewRow = tblServicios.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = tblServicios.convertRowIndexToModel(viewRow);
        Object v = modelo.getValueAt(modelRow, 0);
        return (v instanceof Integer) ? (Integer) v : Integer.parseInt(String.valueOf(v));
    }

    private void eliminarServicioSeleccionado() {
        Integer id = getSelectedServicioId();
        if (id == null) {
            JOptionPane.showMessageDialog(panelTabla, "Selecciona un servicio.");
            return;
        }
        int ok = JOptionPane.showConfirmDialog(
                panelTabla,
                "¿Eliminar definitivamente el servicio ID " + id + "?",
                "Confirmar",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (ok != JOptionPane.YES_OPTION) return;

        final String sql = "DELETE FROM servicios WHERE id = ?";
        try (Connection con = DB.get()) {
            if (usuarioId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_user_id = ?")) { p.setInt(1, usuarioId); p.executeUpdate(); }
            if (sucursalId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_sucursal_id = ?")) { p.setInt(1, sucursalId); p.executeUpdate(); }

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, id);
                int n = ps.executeUpdate();
                JOptionPane.showMessageDialog(panelTabla, n > 0 ? "Servicio eliminado." : "No se encontró el servicio.");
                cargarServicios();
            }
        } catch (SQLException e) {
            if (isFK(e)) {
                int ch = JOptionPane.showConfirmDialog(
                        panelTabla,
                        "El servicio está referenciado. ¿Desactivarlo (activo=0) en su lugar?",
                        "Servicio referenciado",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );
                if (ch == JOptionPane.YES_OPTION) softDeleteServicio(id);
            } else {
                JOptionPane.showMessageDialog(panelTabla, "Error al eliminar: " + e.getMessage());
            }
        }
    }

    private void softDeleteServicio(int id) {
        final String sql = "UPDATE servicios SET activo = 0, actualizado_en = NOW() WHERE id = ?";
        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            int n = ps.executeUpdate();
            JOptionPane.showMessageDialog(panelTabla, n > 0 ? "Servicio desactivado." : "No se pudo desactivar.");
            cargarServicios();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(panelTabla, "Error al desactivar: " + ex.getMessage());
        }
    }

    private static boolean isFK(SQLException e) {
        return "23000".equals(e.getSQLState()) || e.getErrorCode() == 1451 ||
                (e.getMessage() != null && e.getMessage().toLowerCase().contains("foreign key"));
    }

    // ============== helpers ==============
    private static String nvl(String s) { return s != null ? s : ""; }

    // item para el combo
    static class CategoriaItem {
        final Integer id; final String nombre;
        CategoriaItem(Integer id, String nombre) { this.id = id; this.nombre = nombre; }
        static CategoriaItem TODAS() { return new CategoriaItem(null, "Todas las categorías"); }
        @Override public String toString() { return nombre; }
    }

    // ======== COMPACT LOOK & BUTTON STYLE ========
    private void compactLookAndFeel() {
        // 1) Márgenes generales del contenedor principal
        if (panelMain != null) panelMain.setBorder(new EmptyBorder(8, 10, 10, 10));

        // 2) Combo de categoría más pequeño
        if (cmbCategoria != null) {
            cmbCategoria.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            Dimension small = new Dimension(220, 30);
            cmbCategoria.setPreferredSize(small);
            cmbCategoria.setMinimumSize(small);
            cmbCategoria.setMaximumSize(new Dimension(260, 34));
            cmbCategoria.setPrototypeDisplayValue(new CategoriaItem(9999, "XXXXXXXXXXXXXX"));
        }

        // 3) Botones más grandes
        makeBig(btnAgregarServicio, true);
        makeBig(btnModificarServicio, true);
        makeBig(btnEliminarServicio, false);
        makeBig(btnCancelar, false);

        // 4) Estilos de color
        stylePrimaryButton(btnAgregarServicio);
        stylePrimaryButton(btnModificarServicio);
        styleDangerButton(btnEliminarServicio);
        styleDangerButton(btnCancelar);

        // 5) Quitar “loguito/título” superior si existe
        hideHeaderIconOrTitle(panelMain);

    }

    private void makeBig(JButton b, boolean primary) {
        if (b == null) return;
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        Dimension big = new Dimension(140, 40); // tamaño objetivo
        b.setPreferredSize(big);
        b.setMinimumSize(big);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void hideHeaderIconOrTitle(Container root) {
        if (root == null) return;
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel lbl) {
                String t = lbl.getText() != null ? lbl.getText().trim().toLowerCase() : "";
                boolean looksTitle = t.contains("servicio"); // "Servicios", "Servicios Registrados", etc.
                boolean hasIcon = lbl.getIcon() != null;
                if (looksTitle || hasIcon) {
                    lbl.setVisible(false);
                }
            }
            if (c instanceof Container child) hideHeaderIconOrTitle(child);
        }
    }
    // --- Renderer zebra para la tabla ---
    static class ZebraRenderer extends javax.swing.table.DefaultTableCellRenderer {
        private final JTable table;
        private final Color even = Color.WHITE;
        private final Color odd  = new Color(236, 253, 245); // verde pastel MUY ligero

        ZebraRenderer(JTable table) { this.table = table; }

        @Override
        public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);

            // Colores de fondo alternados (según fila vista, no modelo)
            if (!isSelected) setBackground((row % 2 == 0) ? even : odd);

            // Alineaciones por columna
            // 0: ID (izquierda), 4: Precio (derecha), 5: Activo (centro), resto izquierda
            if (column == 0) {
                setHorizontalAlignment(LEFT);
            } else if (column == 4) {
                setHorizontalAlignment(RIGHT);
            } else if (column == 5) {
                setHorizontalAlignment(CENTER);
            } else {
                setHorizontalAlignment(LEFT);
            }

            return this;
        }
    }

    // ======== estilos de botón (idénticos a ModificarServicio) ========
    private void stylePrimaryButton(JButton b) {
        if (b == null) return;
        b.setUI(new ModernButtonUI(new Color(0x22C55E), new Color(0x16A34A), new Color(0x15803D),
                Color.WHITE, 12, true));
    }
    private void styleDangerButton(JButton b) {
        if (b == null) return;
        b.setUI(new ModernButtonUI(new Color(0xEF4444), new Color(0xDC2626), new Color(0xB91C1C),
                Color.WHITE, 12, true));
    }

    static class ModernButtonUI extends BasicButtonUI {
        private final Color bg, hover, press, fg;
        private final int arc;
        private final boolean filled;

        ModernButtonUI(Color bg, Color hover, Color press, Color fg, int arc, boolean filled) {
            this.bg = bg; this.hover = hover; this.press = press; this.fg = fg; this.arc = arc; this.filled = filled;
        }

        @Override public void installUI(JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setOpaque(false);
            b.setBorder(new EmptyBorder(10, 24, 10, 24)); // un poco más grandes
            b.setRolloverEnabled(true);
            b.setFocusPainted(false);
            b.setForeground(fg);
        }

        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel m = b.getModel();
            Color fill = m.isPressed() ? press : (m.isRollover() ? hover : bg);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), arc, arc);
            g2.dispose();

            super.paint(g, c);
        }
    }
}
