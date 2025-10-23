import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class AlertaCerrarSesion extends JFrame {
    private JPanel panelMain;
    private JFrame pantallaAsesor;
    private JButton btnConfirmar;
    private JButton regresarButton;
    private JLabel lblTexto;
    private JLabel lblIcono;
    private JPanel panelBotones;
    private JPanel panelTitulo;
    private JPanel panelMensaje;

    // Paleta
    private static final Color BG_CANVAS  = new Color(0xF3F4F6);
    private static final Color TEXT_PRI   = new Color(0x111827);
    private static final Color TEXT_MUTED = new Color(0x6B7280);

    private static final Color GREEN_BASE = new Color(0x16A34A);
    private static final Color GREEN_HOV  = new Color(0x22C55E);
    private static final Color GREEN_PR   = new Color(0x0A6B2A);

    private static final Color GRAY_BASE  = new Color(0xE5E7EB);
    private static final Color GRAY_HOV   = new Color(0xD1D5DB);
    private static final Color GRAY_PR    = new Color(0x9CA3AF);

    public AlertaCerrarSesion(JFrame pantallaAsesor) {
        this.pantallaAsesor = pantallaAsesor;

        JFrame alerta = new JFrame("Alerta Cerrar Sesión");
        alerta.setUndecorated(true);
        alerta.setContentPane(panelMain);

        applyTheme(); // <-- solo estilo

        alerta.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        alerta.pack();
        alerta.setLocationRelativeTo(null);
        alerta.setVisible(true);

        btnConfirmar.addActionListener(e -> {
            pantallaAsesor.dispose();
            alerta.dispose();
            new Login();
        });

        regresarButton.addActionListener(e -> alerta.dispose());
    }

    private void applyTheme() {
        // Fondo limpio
        if (panelMain != null) {
            panelMain.setBackground(BG_CANVAS);
            panelMain.setBorder(new EmptyBorder(16, 16, 16, 16));
        }
        if (panelMensaje != null) {
            panelMensaje.setBackground(BG_CANVAS);
            panelMensaje.setBorder(new EmptyBorder(16, 16, 16, 16));
        }
        if (panelTitulo != null) {
            panelTitulo.setBackground(BG_CANVAS);
            panelTitulo.setBorder(new EmptyBorder(16, 16, 16, 16));
        }
        if (panelBotones != null) panelBotones.setOpaque(false);
        if (lblTexto != null) {
            lblTexto.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            lblTexto.setOpaque(false);
        }

        // Botones sin bordes raros ni focus ring del LAF
        stylePrimary(btnConfirmar);    // verde
        styleSecondary(regresarButton);// gris
    }

    private void stylePrimary(JButton b) {
        if (b == null) return;
        b.setUI(new FlatButtonUI(GREEN_BASE, GREEN_HOV, GREEN_PR, Color.WHITE, 14));
        b.setBorder(new EmptyBorder(10, 18, 10, 18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
    }

    private void styleSecondary(JButton b) {
        if (b == null) return;
        b.setUI(new FlatButtonUI(GRAY_BASE, GRAY_HOV, GRAY_PR, TEXT_PRI, 14));
        b.setBorder(new EmptyBorder(10, 18, 10, 18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
    }

    /** Botón plano: sin línea exterior, sin relleno del LAF, con esquinas redondeadas. */
    private static class FlatButtonUI extends BasicButtonUI {
        private final Color base, hover, pressed, text;
        private final int radius;

        FlatButtonUI(Color base, Color hover, Color pressed, Color text, int radius) {
            this.base = base; this.hover = hover; this.pressed = pressed; this.text = text; this.radius = radius;
        }

        @Override public void installUI(JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setOpaque(false);            // que no pinte fondo del LAF
            b.setBorderPainted(false);
            b.setContentAreaFilled(false);
            b.setForeground(text);
        }

        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Estado
            ButtonModel m = b.getModel();
            Color fill = base;
            if (m.isPressed()) fill = pressed;
            else if (m.isRollover()) fill = hover;

            // Botón redondeado SIN trazo exterior (adiós borde raro)
            Shape rr = new RoundRectangle2D.Float(0, 0, b.getWidth(), b.getHeight(), radius * 2f, radius * 2f);
            g2.setColor(fill);
            g2.fill(rr);

            // Texto centrado
            String txt = b.getText() != null ? b.getText() : "";
            g2.setFont(b.getFont() != null ? b.getFont() : new Font("Segoe UI", Font.BOLD, 14));
            FontMetrics fm = g2.getFontMetrics();
            int tx = (b.getWidth() - fm.stringWidth(txt)) / 2;
            int ty = (b.getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.setColor(text);
            g2.drawString(txt, tx, ty);

            g2.dispose();
        }
    }
}
