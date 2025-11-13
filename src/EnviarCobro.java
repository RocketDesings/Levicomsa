import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.math.BigDecimal;
import java.sql.*;
import java.util.Enumeration;
import java.util.function.BiConsumer;

public class EnviarCobro {
    // ====== UI del .form ======
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
    private JPanel panelContenedor;
    private JComboBox<IdNombre> cmbMetodoPago;
    private JCheckBox checkTransfer;

    // ====== contexto ======
    private final int sucursalId;
    private final int usuarioId;
    private Integer clienteIdSel; // se llena al elegir cliente

    private JFrame frame;

    // ===== Paleta / tema =====
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

    // ===== helpers DTO =====
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

    // ====== CONSTRUCTORES ======
    public EnviarCobro(int sucursalId, int usuarioId) { this(sucursalId, null, usuarioId); }

    public EnviarCobro(int sucursalId, Integer clienteIdInicial, int usuarioId) {
        this.sucursalId = sucursalId;
        this.usuarioId  = usuarioId;
        this.clienteIdSel = clienteIdInicial;

        setUIFont(new Font("Segoe UI", Font.PLAIN, 13));
        construirFrame();
        cablearEventos();

        if (clienteIdSel != null) cargarNombreCliente(clienteIdSel);
        cargarCategorias();
        cargarMetodosPago();
        actualizarEstadoMetodoPago();

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // ==================== FRAME + DISEÑO ====================
    private void construirFrame() {
        frame = new JFrame("Enviar cobro");
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        frame.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                frame.setShape(new RoundRectangle2D.Double(0, 0, frame.getWidth(), frame.getHeight(), 24, 24));
            }
        });

        GradientPanel root = new GradientPanel(BG_TOP, BG_BOT);
        root.setLayout(new GridBagLayout());

        CardPanel card = new CardPanel();
        card.setLayout(new BorderLayout());

        if (panelMain == null) panelMain = new JPanel(new BorderLayout());
        panelMain.setOpaque(true);
        panelMain.setBackground(CARD_BG);
        panelMain.setBorder(new EmptyBorder(16,18,16,18));
        card.add(panelMain, BorderLayout.CENTER);

        decorateAsCard(panelCliente);
        decorateAsCard(panelCombos);
        decorateAsCard(panelMonto);
        decorateAsCard(panelExtra);
        decorateAsCard(panelInfo);
        decorateAsCard(panelBotones);
        decorateAsCard(panelMain);

        if (txtCliente != null) {
            styleTextFieldPill(txtCliente);
            txtCliente.setEditable(false);
            setPlaceholderIfEmpty(txtCliente, "Selecciona un cliente…");
        }
        if (btnSeleccionarCliente != null) styleNeutralButton(btnSeleccionarCliente);

        if (cmbCategoria   != null) styleCombo(cmbCategoria);
        if (cmbServicio    != null) styleCombo(cmbServicio);
        if (cmbMetodoPago  != null) styleCombo(cmbMetodoPago);

        if (txtMonto != null) {
            styleTextField(txtMonto);
            setPlaceholderIfEmpty(txtMonto, "Monto (opcional: usa sugerido)");
            if (txtMonto.getDocument() instanceof AbstractDocument ad) {
                ad.setDocumentFilter(new NumericFilter());
            }
        }

        if (lblPrecioSugerido != null) {
            lblPrecioSugerido.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 14));
            lblPrecioSugerido.setForeground(GREEN_DARK);
        }

        if (btnEnviar != null) {
            stylePrimaryButton(btnEnviar);
            frame.getRootPane().setDefaultButton(btnEnviar);
        }
        if (btnCancelar != null) styleDangerButton(btnCancelar);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gbc.gridy = 0;
        gbc.insets = new Insets(24,24,24,24);
        gbc.fill = GridBagConstraints.BOTH;
        root.add(card, gbc);

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
        panelMain.addMouseListener(dragger);
        panelMain.addMouseMotionListener(dragger);

        frame.setContentPane(root);
        frame.pack();
    }

    // ==================== EVENTOS ====================
    private void cablearEventos() {
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
            btnCancelar.addActionListener(e -> frame.dispose());
        }
        if (btnEnviar != null) {
            btnEnviar.addActionListener(e -> onEnviar(frame));
        }
        if (checkTransfer != null) {
            checkTransfer.addActionListener(e -> actualizarEstadoMetodoPago());
        }

        if (panelMain != null) {
            panelMain.registerKeyboardAction(
                    e -> frame.dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
            );
        }
    }

    // ==================== CLIENTE ====================
    private void abrirSelectorCliente() {
        BiConsumer<Integer,String> listener = (id, nombre) -> {
            clienteIdSel = id;
            if (txtCliente != null) txtCliente.setText(nombre);
        };
        new SeleccionarCliente(listener);
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

    // ==================== CATEGORÍAS / SERVICIOS ====================
    private boolean puedeVerContabilidad() {
        int rol = -1;
        final String sql = "SELECT rol_id FROM Usuarios WHERE id=?";
        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) rol = rs.getInt(1); }
        } catch (SQLException ignored) {}
        return rol == 1 || rol == 4 || rol == 5;
    }

    private void cargarCategorias() {
        DefaultComboBoxModel<IdNombre> model = new DefaultComboBoxModel<>();
        if (cmbCategoria != null) cmbCategoria.setModel(model);

        boolean verContab = puedeVerContabilidad();

        final String sql = """
                SELECT id, nombre
                FROM categorias_servicio
                WHERE activo=1
                  AND (LOWER(nombre) <> 'contabilidad' OR ?)
                ORDER BY nombre
                """;
        try (Connection con = DB.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setBoolean(1, verContab);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) model.addElement(new IdNombre(rs.getInt(1), rs.getString(2)));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(panelMain, "No se pudieron cargar categorías:\n" + ex.getMessage());
        }

        styleCombo(cmbCategoria);

        if (model.getSize() > 0 && cmbCategoria != null) cmbCategoria.setSelectedIndex(0);
        cargarServiciosPorCategoria();
    }

    private void cargarServiciosPorCategoria() {
        if (cmbCategoria == null || cmbServicio == null) return;
        IdNombre cat = (IdNombre) cmbCategoria.getSelectedItem();

        DefaultComboBoxModel<ServicioItem> model = new DefaultComboBoxModel<>();
        cmbServicio.setModel(model);

        if (lblPrecioSugerido != null) lblPrecioSugerido.setText("—");
        if (cat == null) { styleCombo(cmbServicio); return; }

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

        styleCombo(cmbServicio);

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

    // ==================== MÉTODOS DE PAGO ====================
    private void cargarMetodosPago() {
        if (cmbMetodoPago == null) return;

        DefaultComboBoxModel<IdNombre> model = new DefaultComboBoxModel<>();
        cmbMetodoPago.setModel(model);

        final String sql = """
            SELECT id, nombre
            FROM metodos_pago
            WHERE activo = 1
              AND id IN (6, 7, 8, 9)
            ORDER BY nombre
            """;

        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String nombre = rs.getString("nombre");
                model.addElement(new IdNombre(id, nombre));
            }

        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(panelMain,
                    "No se pudieron cargar los métodos de pago:\n" + ex.getMessage());
        }

        styleCombo(cmbMetodoPago);
        if (model.getSize() > 0) {
            cmbMetodoPago.setSelectedIndex(0);
        }
    }

    private void actualizarEstadoMetodoPago() {
        boolean enabled = checkTransfer != null && checkTransfer.isSelected();
        if (cmbMetodoPago != null) {
            cmbMetodoPago.setEnabled(enabled);
            if (!enabled) {
                cmbMetodoPago.setSelectedIndex(-1);
            } else if (cmbMetodoPago.getItemCount() > 0 && cmbMetodoPago.getSelectedIndex() == -1) {
                cmbMetodoPago.setSelectedIndex(0);
            }
        }
    }

    // ==================== ENVIAR ====================
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

        // Metodo de pago (solo si es transferencia)
        IdNombre metodoPago = null;
        if (checkTransfer != null && checkTransfer.isSelected()) {
            metodoPago = (IdNombre) cmbMetodoPago.getSelectedItem();
            if (metodoPago == null) {
                JOptionPane.showMessageDialog(panelMain, "Selecciona un método de pago (cuenta de transferencia).");
                return;
            }
        }

        // Estado: si hay metodo de pago (transferencia) lo marcamos como pagado
        String estado = (metodoPago != null) ? "pagado" : "pendiente";

        try (Connection con = DB.get()) {
            con.setAutoCommit(false);

            // bitácora
            try (PreparedStatement ps = con.prepareStatement("SET @app_user_id = ?")) {
                ps.setInt(1, usuarioId);
                ps.executeUpdate();
            }

            long cobroId;

            // AHORA también insertamos estado
            final String insCobro =
                    "INSERT INTO cobros (sucursal_id, cliente_id, usuario_id, metodo_pago_id, estado, notas) " +
                            "VALUES (?,?,?,?,?,?)";

            try (PreparedStatement ps = con.prepareStatement(insCobro, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, sucursalId);
                ps.setInt(2, clienteIdSel);
                ps.setInt(3, usuarioId);

                if (metodoPago != null) {
                    ps.setInt(4, metodoPago.id);       // metodo_pago_id
                } else {
                    ps.setNull(4, Types.INTEGER);
                }

                ps.setString(5, estado);                // estado

                String notas = "Servicio: " + s.nombre;
                if (metodoPago != null) {
                    notas += " | Método pago: " + metodoPago.nombre;
                }
                ps.setString(6, notas);                // notas

                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("No se obtuvo id de cobro");
                    cobroId = rs.getLong(1);
                }
            }

            // Detalle
            final String insDet =
                    "INSERT INTO cobro_detalle (cobro_id, servicio_id, cantidad, precio_unit) " +
                            "VALUES (?,?,1,?)";
            try (PreparedStatement ps = con.prepareStatement(insDet)) {
                ps.setLong(1, cobroId);
                ps.setInt(2, s.id);
                ps.setBigDecimal(3, monto);
                ps.executeUpdate();
            }

            con.commit();
            JOptionPane.showMessageDialog(panelMain,
                    "Cobro creado (" + estado + ") para " +
                            (txtCliente != null ? txtCliente.getText() : "cliente"));
            if (owner != null) owner.dispose();

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(panelMain, "No se pudo crear el cobro:\n" + ex.getMessage());
        }
    }


    // ==================== HELPERS DE ESTILO ====================
    private void styleTextField(JTextField tf) {
        if (tf == null) return;
        tf.setOpaque(true);
        tf.setBackground(Color.WHITE);
        tf.setForeground(TEXT_PRIMARY);
        tf.setCaretColor(TEXT_PRIMARY);
        tf.setBorder(new CompoundBorderRounded(BORDER_SOFT, 12, 1, new Insets(10, 12, 10, 12)));
        tf.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                tf.setBorder(new CompoundBorderRounded(BORDER_FOCUS, 12, 2, new Insets(10,12,10,12)));
            }
            @Override public void focusLost(FocusEvent e) {
                tf.setBorder(new CompoundBorderRounded(BORDER_SOFT, 12, 1, new Insets(10,12,10,12)));
            }
        });
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 16));
    }

    private void styleTextFieldPill(JTextField tf) {
        if (tf == null) return;
        tf.setOpaque(true);
        tf.setBackground(Color.WHITE);
        tf.setForeground(TEXT_PRIMARY);
        tf.setCaretColor(TEXT_PRIMARY);
        tf.setBorder(new CompoundBorderRounded(BORDER_SOFT, 18, 1, new Insets(10, 14, 10, 12)));
        tf.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                tf.setBorder(new CompoundBorderRounded(BORDER_FOCUS, 18, 2, new Insets(10,14,10,12)));
            }
            @Override public void focusLost(FocusEvent e) {
                tf.setBorder(new CompoundBorderRounded(BORDER_SOFT, 18, 1, new Insets(10,14,10,12)));
            }
        });
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 16));
    }

    private <T> void styleCombo(JComboBox<T> cb) {
        if (cb == null) return;
        cb.setBackground(Color.WHITE);
        cb.setForeground(TEXT_PRIMARY);
        cb.setBorder(new MatteBorder(1,1,1,1, BORDER_SOFT));
        cb.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        if (cb.getItemCount() > 0) cb.setPrototypeDisplayValue(cb.getItemAt(0));
    }

    private void stylePrimaryButton(JButton b) {
        if (b == null) return;
        b.setUI(new ModernButtonUI(GREEN_BASE, GREEN_SOFT, GREEN_DARK, Color.WHITE, 12, true));
        b.setBorder(new EmptyBorder(12,18,12,18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void styleDangerButton(JButton b) {
        if (b == null) return;
        Color ROJO = new Color(0xDC2626);
        b.setUI(new ModernButtonUI(ROJO, ROJO.brighter(), ROJO.darker(), Color.WHITE, 12, true));
        b.setBorder(new EmptyBorder(12,18,12,18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void styleNeutralButton(JButton b) {
        if (b == null) return;
        Color BASE = new Color(0x374151);
        b.setUI(new ModernButtonUI(new Color(0xF3F4F6), new Color(0xE5E7EB), new Color(0xD1D5DB), BASE, 12, true));
        b.setForeground(BASE);
        b.setBorder(new EmptyBorder(10,16,10,16));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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

    // ======== DISEÑO: clases reutilizables ========
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

    static class CardPanel extends JPanel {
        private final int arc = 20;
        CardPanel() { setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            for (int i = 0; i < 10; i++) {
                float alpha = 0.06f * (10 - i);
                g2.setColor(new Color(0, 0, 0, (int) (alpha * 255)));
                g2.fillRoundRect(10 - i, 12 - i, w - (10 - i) * 2, h - (12 - i) * 2, arc + i, arc + i);
            }
            g2.setColor(CARD_BG);
            g2.fillRoundRect(0, 0, w, h, arc, arc);
            g2.setColor(new Color(0,0,0,30));
            g2.drawRoundRect(0, 0, w-1, h-1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class CompoundBorderRounded extends javax.swing.border.CompoundBorder {
        CompoundBorderRounded(Color line, int arc, int thickness, Insets innerPad) {
            super(new RoundedLineBorder(line, arc, thickness), new EmptyBorder(innerPad));
        }
    }
    static class RoundedLineBorder extends javax.swing.border.AbstractBorder {
        private final Color color; private final int arc; private final int thickness;
        RoundedLineBorder(Color color, int arc, int thickness) { this.color = color; this.arc = arc; this.thickness = thickness; }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            for (int i = 0; i < thickness; i++) {
                g2.drawRoundRect(x + i, y + i, w - 1 - 2 * i, h - 1 - 2 * i, arc, arc);
            }
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) { return new Insets(thickness, thickness, thickness, thickness); }
        @Override public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(thickness, thickness, thickness, thickness); return insets;
        }
    }

    static class CompoundRoundShadowBorder extends EmptyBorder {
        private final int arc; private final Color border; private final Color shadow;
        public CompoundRoundShadowBorder(int arc, Color border, Color shadow) {
            super(12,12,12,12);
            this.arc = arc; this.border = border; this.shadow = shadow;
        }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(shadow);
            for (int i=0;i<8;i++) {
                float alpha = 0.08f;
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2.fillRoundRect(x+2+i, y+4+i, w-4, h-6, arc, arc);
            }
            g2.setComposite(AlphaComposite.SrcOver);
            g2.setColor(border);
            g2.drawRoundRect(x+1, y+1, w-3, h-3, arc, arc);
            g2.dispose();
        }
    }

    static class NumericFilter extends DocumentFilter {
        @Override public void insertString(FilterBypass fb, int off, String str, AttributeSet a) throws BadLocationException {
            if (str == null) return;
            String n = sanitize(fb.getDocument().getText(0, fb.getDocument().getLength()), str);
            if (n != null) super.insertString(fb, off, str, a);
        }
        @Override public void replace(FilterBypass fb, int off, int len, String str, AttributeSet a) throws BadLocationException {
            String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
            String next = (str == null) ? cur : cur.substring(0, off) + str + cur.substring(off + len);
            if (sanitize("", next) != null) super.replace(fb, off, len, str, a);
        }
        private String sanitize(String cur, String add) {
            String s = (cur + add).replace(",", ".").trim();
            if (s.isEmpty()) return s;
            if (!s.matches("\\d+(\\.\\d{0,2})?")) return null;
            return s;
        }
    }

    public static void mostrar(int sucursalId, int usuarioId) {
        SwingUtilities.invokeLater(() -> new EnviarCobro(sucursalId, usuarioId));
    }
    public static void mostrar(int sucursalId, Integer clienteId, int usuarioId) {
        SwingUtilities.invokeLater(() -> new EnviarCobro(sucursalId, clienteId, usuarioId));
    }

    private void setUIFont(Font f) {
        try {
            Enumeration<Object> keys = UIManager.getDefaults().keys();
            while (keys.hasMoreElements()) {
                Object k = keys.nextElement();
                Object v = UIManager.get(k);
                if (v instanceof Font) UIManager.put(k, f);
            }
        } catch (Exception ignored) {}
    }
    private void styleExitButton(JButton b) {
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
