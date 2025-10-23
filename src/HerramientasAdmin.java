import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;

public class HerramientasAdmin extends JDialog {
    private JPanel panelMain;
    private JButton btnGestionarUsuario;
    private JButton btnGestionarServicios;
    private JButton btnGestionarEmpleados;
    private JButton btnGestionarSucursales;
    private JButton btnCancelar;
    private JPanel panelBtns;
    private JPanel panelBtn2;
    private JPanel panelBtn1;
    private JButton btnGestionarCategorias;

    // contexto
    private final int usuarioId;
    private final int sucursalId;

    // refs para evitar duplicados
    private JDialog dlgSucursales;
    private JDialog dlgEmpleados;
    private JDialog dlgUsuarios;
    private JDialog dlgServicios;
    private JDialog dlgCategorias;

    // ===== Paleta (coincide con InterfazCajero) =====
    private static final Color GREEN_DARK   = new Color(0x0A6B2A);
    private static final Color GREEN_BASE   = new Color(0x16A34A);
    private static final Color GREEN_SOFT   = new Color(0x22C55E);
    private static final Color RED_BASE     = new Color(0xDC2626);
    private static final Color RED_HOV      = new Color(0xD1D5DB);
    private static final Color RED_PR       = new Color(0x9CA3AF);
    private static final Color BG_CANVAS    = new Color(0xF3F4F6);
    private static final Color CARD_BG      = Color.WHITE;
    private static final Color BORDER_SOFT  = new Color(0xE5E7EB);
    private static final Color TEXT_PRIMARY = new Color(0x111827);
    private static final Color TEXT_MUTED   = new Color(0x6B7280);

    public HerramientasAdmin(Window owner, int usuarioId, int sucursalId) {
        super(owner, "Herramientas de administración", ModalityType.MODELESS);
        this.usuarioId = usuarioId;
        this.sucursalId = sucursalId;

        // Contenedor raíz sin “bordes raros”
        setContentPane(buildRoot());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(640, 420));
        pack();
        setLocationRelativeTo(owner);

        applyTheme();

        if (btnCancelar != null) btnCancelar.addActionListener(e -> dispose());

        // Sucursales
        if (btnGestionarSucursales != null) {
            btnGestionarSucursales.addActionListener(e -> abrirCRUDSucursales());
        }
        // Empleados
        if (btnGestionarEmpleados != null) {
            btnGestionarEmpleados.addActionListener(e -> abrirCRUDEmpleados());
        }
        // Usuarios (tal cual lo pediste)
        if (btnGestionarUsuario != null) {
            btnGestionarUsuario.addActionListener(e -> {
                if (dlgUsuarios != null && dlgUsuarios.isDisplayable()) {
                    dlgUsuarios.toFront(); dlgUsuarios.requestFocus(); return;
                }
                dlgUsuarios = CRUDUsuarios.createDialog(this, usuarioId, sucursalId);
                dlgUsuarios.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override public void windowClosed (java.awt.event.WindowEvent e) { dlgUsuarios = null; }
                    @Override public void windowClosing(java.awt.event.WindowEvent e) { dlgUsuarios = null; }
                });
                dlgUsuarios.setVisible(true);
            });
        }
        // Categorías
        if (btnGestionarCategorias != null) {
            btnGestionarCategorias.addActionListener(e -> {
                if (dlgCategorias != null && dlgCategorias.isDisplayable()) {
                    dlgCategorias.toFront(); dlgCategorias.requestFocus(); return;
                }
                dlgCategorias = CRUDCategorias.createDialog(this, usuarioId, sucursalId);
                dlgCategorias.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override public void windowClosed (java.awt.event.WindowEvent e) { dlgCategorias = null; }
                    @Override public void windowClosing(java.awt.event.WindowEvent e) { dlgCategorias = null; }
                });
                dlgCategorias.setVisible(true);
            });
        }
        // Servicios
        if (btnGestionarServicios != null) {
            btnGestionarServicios.addActionListener(e -> abrirCRUDServicios());
        }
    }

    /** Root sin márgenes extra ni bordes raros; coloca tu panelMain como “card” central. */
    private JComponent buildRoot() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(0,0,0,0));
        if (panelMain == null) panelMain = new JPanel();
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(CARD_BG);
        wrap.setBorder(new EmptyBorder(16,16,16,16)); // padding limpio
        wrap.add(panelMain, BorderLayout.CENTER);
        root.add(wrap, BorderLayout.CENTER);
        return root;
    }

    // ===== Estilo / Tema =====
    private void applyTheme() {
        // Fondo general
        getContentPane().setBackground(BG_CANVAS);
        if (panelMain != null) {
            panelMain.setOpaque(true);
            panelMain.setBackground(CARD_BG);
            // línea sutil inferior para separar si hay títulos en tu .form
            panelMain.setBorder(new MatteBorder(1,1,1,1, BORDER_SOFT));
        }
        for (JPanel p : new JPanel[]{panelBtns, panelBtn1, panelBtn2}) {
            if (p != null) { p.setOpaque(false); p.setBorder(null); }
        }

        // Botones
        stylePrimaryButton(btnGestionarUsuario);
        stylePrimaryButton(btnGestionarServicios);
        stylePrimaryButton(btnGestionarEmpleados);
        stylePrimaryButton(btnGestionarSucursales);
        stylePrimaryButton(btnGestionarCategorias);
        styleExitButton(btnCancelar);
    }

    private void stylePrimaryButton(JButton b) {
        if (b == null) return;
        b.setUI(new ModernButtonUI(GREEN_BASE, GREEN_SOFT, GREEN_DARK, Color.WHITE, 14, true));
        b.setBorder(new EmptyBorder(12,18,12,18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
    }
    private void styleExitButton(JButton b) {
        if (b == null) return;
        b.setUI(new ModernButtonUI(RED_BASE, RED_HOV, RED_PR, Color.WHITE, 14, true));
        b.setBorder(new EmptyBorder(12,18,12,18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
    }

    // --- métodos que abren/traen al frente y evitan duplicados ---
    private void abrirCRUDSucursales() {
        if (dlgSucursales != null && dlgSucursales.isDisplayable()) {
            dlgSucursales.toFront();
            dlgSucursales.requestFocus();
            return;
        }
        dlgSucursales = CRUDSucursales.createDialog(this, usuarioId, sucursalId);
        dlgSucursales.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed (WindowEvent e) { dlgSucursales = null; }
            @Override public void windowClosing(WindowEvent e) { dlgSucursales = null; }
        });
        dlgSucursales.setVisible(true);
    }

    private void abrirCRUDEmpleados() {
        if (dlgEmpleados != null && dlgEmpleados.isDisplayable()) {
            dlgEmpleados.toFront();
            dlgEmpleados.requestFocus();
            return;
        }
        dlgEmpleados = CRUDEmpleados.createDialog(this, usuarioId, sucursalId);
        dlgEmpleados.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed (WindowEvent e) { dlgEmpleados = null; }
            @Override public void windowClosing(WindowEvent e) { dlgEmpleados = null; }
        });
        dlgEmpleados.setVisible(true);
    }

    private void abrirCRUDServicios() {
        if (dlgServicios != null && dlgServicios.isDisplayable()) {
            dlgServicios.toFront();
            dlgServicios.requestFocus();
            return;
        }
        Window owner = (getOwner() != null) ? getOwner() : this;
        dlgServicios = CRUDServicios.createDialog(owner, usuarioId, sucursalId);
        dlgServicios.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed (WindowEvent e) { dlgServicios = null; }
            @Override public void windowClosing(WindowEvent e) { dlgServicios = null; }
        });
        dlgServicios.setVisible(true);
    }

    // ===== Botón moderno (redondeado) =====
    private static class ModernButtonUI extends BasicButtonUI {
        private final Color base, hover, pressed, text;
        private final int radius;
        private final boolean filled;
        ModernButtonUI(Color base, Color hover, Color pressed, Color text, int radius, boolean filled) {
            this.base=base; this.hover=hover; this.pressed=pressed; this.text=text; this.radius=radius; this.filled=filled;
        }
        @Override public void installUI(JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setOpaque(false);
            b.setBorderPainted(false);
            b.setForeground(text);
        }
        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            ButtonModel m = b.getModel();
            Color fill = base;
            if (m.isPressed())      fill = pressed;
            else if (m.isRollover()) fill = hover;

            Shape rr = new RoundRectangle2D.Float(0, 0, b.getWidth(), b.getHeight(), radius*2f, radius*2f);
            if (filled) {
                g2.setColor(fill); g2.fill(rr);
                g2.setColor(new Color(0,0,0,25)); g2.draw(rr);
            } else {
                g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 35));
                g2.fill(rr);
                g2.setColor(text); g2.draw(rr);
            }

            g2.setColor(text);
            FontMetrics fm = g2.getFontMetrics();
            int tx = (b.getWidth() - fm.stringWidth(b.getText())) / 2;
            int ty = (b.getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(b.getText(), tx, ty);
            g2.dispose();
        }
    }
}
