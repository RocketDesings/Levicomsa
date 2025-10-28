import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.function.BiConsumer;

public class EnviarCobro {
    private JPanel panelMain;
    private JPanel panelInfo;
    private JPanel panelCombos;
    private JPanel panelExtra;
    private JPanel panelBotones;
    private JComboBox<IdNombre> cmbCategoria;
    private JComboBox<ServicioItem> cmbServicio;
    private JPanel panelMonto;
    private JTextField txtMonto;
    private JLabel lblPrecioSugerido;
    private JButton btnCancelar;
    private JButton btnEnviar;
    private JPanel panelCliente;
    private JButton btnSeleccionarCliente;
    private JTextField txtCliente;

    // contexto
    private final int sucursalId;
    private final int usuarioId;
    private Integer clienteIdSel; // se llena al elegir cliente

    // helpers
    static class IdNombre {
        final int id; final String nombre;
        IdNombre(int id, String nombre){ this.id=id; this.nombre=nombre; }
        @Override public String toString(){ return nombre; }
    }
    static class ServicioItem extends IdNombre {
        final BigDecimal precio;
        ServicioItem(int id, String nombre, BigDecimal precio){ super(id,nombre); this.precio=precio; }
        @Override public String toString(){ return nombre; }
    }

    public EnviarCobro(int sucursalId, int usuarioId) {
        this(sucursalId, null, usuarioId);
    }
    public EnviarCobro(int sucursalId, Integer clienteIdInicial, int usuarioId) {
        this.sucursalId = sucursalId;
        this.usuarioId  = usuarioId;
        this.clienteIdSel = clienteIdInicial;

        JFrame f = new JFrame("Enviar cobro");
        f.setContentPane(panelMain);
        f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        f.pack();
        f.setLocationRelativeTo(null);

        // estilos mínimos (sin tocar lógica)
        if (btnEnviar!=null){ btnEnviar.setBackground(new Color(0x0E7C0E)); btnEnviar.setForeground(Color.WHITE); }
        if (btnCancelar!=null){ btnCancelar.setBackground(new Color(0xB00020)); btnCancelar.setForeground(Color.WHITE); }
        if (lblPrecioSugerido!=null){ lblPrecioSugerido.setForeground(new Color(0x0E7C0E)); }
        if (txtCliente!=null) txtCliente.setEditable(false);

        // cliente inicial (si vino del caller)
        if (clienteIdSel != null) {
            cargarNombreCliente(clienteIdSel);
        }

        // listeners
        if (btnSeleccionarCliente != null) {
            btnSeleccionarCliente.addActionListener(e -> abrirSelectorCliente());
        }
        if (cmbCategoria != null) {
            cmbCategoria.addActionListener(e -> cargarServiciosPorCategoria());
        }
        if (cmbServicio != null) {
            cmbServicio.addActionListener(e -> actualizarPrecioSugerido());
        }
        if (btnCancelar != null) {
            btnCancelar.addActionListener(e -> f.dispose());
        }
        if (btnEnviar != null) {
            btnEnviar.addActionListener(e -> onEnviar(f));
        }

        // carga inicial
        cargarCategorias();
        f.setVisible(true);
    }

    // ======== CLIENTE ========
    private void abrirSelectorCliente() {
        BiConsumer<Integer,String> listener = (id, nombre) -> {
            clienteIdSel = id;
            if (txtCliente != null) txtCliente.setText(nombre);
        };
        new SeleccionarCliente(listener); // no cierra esta ventana; sólo devuelve selección
    }

    private void cargarNombreCliente(int clienteId) {
        final String sql = "SELECT nombre FROM Clientes WHERE id=?";
        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && txtCliente != null) txtCliente.setText(rs.getString(1));
            }
        } catch (Exception ignored) {}
    }

    // ======== CATEGORÍAS / SERVICIOS ========
    /** Devuelve true si el usuario puede ver la categoría “Contabilidad”. */
    private boolean puedeVerContabilidad() {
        int rol = -1;
        final String sql = "SELECT rol_id FROM Usuarios WHERE id=?";
        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) rol = rs.getInt(1);
            }
        } catch (SQLException ignored) {}
        return rol == 1 || rol == 4 || rol == 5;
    }

    private void cargarCategorias() {
        DefaultComboBoxModel<IdNombre> model = new DefaultComboBoxModel<>();
        if (cmbCategoria != null) cmbCategoria.setModel(model);

        boolean verContab = puedeVerContabilidad();

        // Si NO puede ver contabilidad, la excluimos por nombre (case-insensitive)
        final String sql = """
                SELECT id, nombre
                FROM categorias_servicio
                WHERE activo=1
                  AND (LOWER(nombre) <> 'contabilidad' OR ?)
                ORDER BY nombre
                """;
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setBoolean(1, verContab); // si true → no filtra; si false → excluye "contabilidad"
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) model.addElement(new IdNombre(rs.getInt(1), rs.getString(2)));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(panelMain, "No se pudieron cargar categorías:\n" + ex.getMessage());
        }

        if (model.getSize() > 0 && cmbCategoria != null) cmbCategoria.setSelectedIndex(0);
        cargarServiciosPorCategoria();
    }

    private void cargarServiciosPorCategoria() {
        if (cmbCategoria == null || cmbServicio == null) return;
        IdNombre cat = (IdNombre) cmbCategoria.getSelectedItem();

        DefaultComboBoxModel<ServicioItem> model = new DefaultComboBoxModel<>();
        cmbServicio.setModel(model);
        if (lblPrecioSugerido != null) lblPrecioSugerido.setText("—");
        if (cat == null) return;

        final String sql = "SELECT id, nombre, precio FROM servicios WHERE activo=1 AND categoria_id=? ORDER BY nombre";
        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, cat.id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    model.addElement(new ServicioItem(
                            rs.getInt("id"),
                            rs.getString("nombre"),
                            rs.getBigDecimal("precio")
                    ));
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(panelMain, "No se pudieron cargar servicios:\n" + ex.getMessage());
        }
        if (model.getSize() > 0) cmbServicio.setSelectedIndex(0);
        actualizarPrecioSugerido();
    }

    private void actualizarPrecioSugerido() {
        if (cmbServicio == null || lblPrecioSugerido == null) return;
        ServicioItem s = (ServicioItem) cmbServicio.getSelectedItem();
        if (s == null) { lblPrecioSugerido.setText("—"); return; }
        lblPrecioSugerido.setText("Sugerido: $" + s.precio.toPlainString());
        if (txtMonto != null && txtMonto.getText().trim().isEmpty()) {
            txtMonto.setText(s.precio.toPlainString());
        }
    }

    // ======== ENVIAR ========
    private void onEnviar(JFrame owner) {
        if (clienteIdSel == null || clienteIdSel <= 0) {
            JOptionPane.showMessageDialog(panelMain, "Selecciona un cliente.");
            return;
        }
        ServicioItem s = (ServicioItem) cmbServicio.getSelectedItem();
        if (s == null) {
            JOptionPane.showMessageDialog(panelMain, "Selecciona un servicio.");
            return;
        }

        BigDecimal monto;
        try {
            String raw = txtMonto != null ? txtMonto.getText().trim() : "";
            if (raw.isEmpty()) monto = s.precio;
            else {
                raw = raw.replace(",", ".");
                monto = new BigDecimal(raw);
            }
            if (monto.compareTo(BigDecimal.ZERO) <= 0) {
                JOptionPane.showMessageDialog(panelMain, "El monto debe ser mayor a 0.");
                return;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(panelMain, "Monto inválido.");
            return;
        }

        try (Connection con = DB.get()) {
            con.setAutoCommit(false);

            // bitácora
            try (PreparedStatement ps = con.prepareStatement("SET @app_user_id = ?")) {
                ps.setInt(1, usuarioId);
                ps.executeUpdate();
            }

            // 1) cobro (pendiente)
            long cobroId;
            final String insCobro = "INSERT INTO cobros (sucursal_id, cliente_id, usuario_id, notas) VALUES (?,?,?,?)";
            try (PreparedStatement ps = con.prepareStatement(insCobro, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, sucursalId);
                ps.setInt(2, clienteIdSel);
                ps.setInt(3, usuarioId);
                ps.setString(4, "Servicio: " + s.nombre);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("No se obtuvo id de cobro");
                    cobroId = rs.getLong(1);
                }
            }

            // 2) detalle
            final String insDet = "INSERT INTO cobro_detalle (cobro_id, servicio_id, cantidad, precio_unit) VALUES (?,?,1,?)";
            try (PreparedStatement ps = con.prepareStatement(insDet)) {
                ps.setLong(1, cobroId);
                ps.setInt(2, s.id);
                ps.setBigDecimal(3, monto);
                ps.executeUpdate();
            }

            con.commit();
            JOptionPane.showMessageDialog(panelMain, "Cobro creado (Pendiente) para " + (txtCliente != null ? txtCliente.getText() : "cliente"));
            if (owner != null) owner.dispose();

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(panelMain, "No se pudo crear el cobro:\n" + ex.getMessage());
        }
    }

    // helper para abrir rápido
    public static void mostrar(int sucursalId, int usuarioId) {
        new EnviarCobro(sucursalId, usuarioId);
    }
}
