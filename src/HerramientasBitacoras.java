import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.*;
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

    private JComboBox<String> cmbMes;       // Mes (texto)
    private JComboBox<String> cmbFecha;     // Año (uso el nombre original)
    private JComboBox<Integer> cmbDesde;    // Día desde (1..31)
    private JComboBox<Integer> cmbHasta;    // Día hasta (1..31)

    private JPanel panelOpciones;
    private JCheckBox checkSucursal;
    private JButton btnCSV;
    private JButton btnSalir;
    private JPanel panelTitulo;
    private JComboBox<ComboItem> cmbSucursal;
    private JComboBox<ComboItem> cmbTrabajador;
    private JCheckBox checkTrabajador;
    private JComboBox<String> cmbFiltrar; // reservado si lo usas

    // ----- contexto -----
    private final int usuarioId;
    private final int sucursalIdSesion;

    // ----- estado / ventana -----
    private JDialog dialog;
    private static JDialog instanciaUnica; // evita abrir 2

    // ----- formato -----
    private static final DateTimeFormatter DF_FECHA_HH = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

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

    // ======= UI =======
    private void aplicarEstilo() {
        if (panelMain != null) panelMain.setBorder(new EmptyBorder(8,8,8,8));
        if (tblDatos  != null) tblDatos.setRowHeight(26);
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
        JTableHeader h = tblDatos.getTableHeader();
        h.setReorderingAllowed(false);

        int[] w = {60, 180, 180, 150, 120, 100, 380};
        for (int i = 0; i < Math.min(w.length, tblDatos.getColumnCount()); i++) {
            tblDatos.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
        }
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

    private void cablearEventos() {
        // no-op adicional para combos
    }

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

    // ======= Tabla =======
    private void recargarTabla() {
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
        int dMin = Math.min(dDesde, dHasta);
        int dMax = Math.max(dDesde, dHasta);

        LocalDate f0 = ym.atDay(dMin);
        LocalDate f1 = ym.atDay(dMax);

        Timestamp t0 = Timestamp.valueOf(f0.atStartOfDay());
        Timestamp t1 = Timestamp.valueOf(f1.plusDays(1).atStartOfDay()); // exclusivo

        Integer sucFiltro =
                (checkSucursal.isSelected() && cmbSucursal.getSelectedItem() instanceof ComboItem itS) ? itS.id : null;
        Integer trabFiltro =
                (checkTrabajador.isSelected() && cmbTrabajador.getSelectedItem() instanceof ComboItem itT) ? itT.id : null;

        String sql = """
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
              AND (? IS NULL OR t.id          = ?)   -- filtra por TRABAJADOR
            ORDER BY c.fecha
        """;

        DefaultTableModel m = (DefaultTableModel) tblDatos.getModel();
        m.setRowCount(0);

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
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(dialog, "Error al cargar cobros: " + ex.getMessage());
        }

        // reflejo por si el usuario dejó un día fuera de rango y fue clamped
        cmbDesde.setSelectedItem(dMin);
        cmbHasta.setSelectedItem(dMax);
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

    // ======= helpers =======
    /** Item para combos con id + texto. */
    private static class ComboItem {
        final int id; final String nombre;
        ComboItem(int id, String nombre) { this.id=id; this.nombre=nombre; }
        @Override public String toString() { return nombre; }
    }
}
