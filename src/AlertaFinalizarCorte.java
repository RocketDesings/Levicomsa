import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.RoundRectangle2D;

public class AlertaFinalizarCorte {
    private JPanel panelMain;
    private JButton btnConfirmar;
    private JButton btnSalir;
    private JPanel panelBotones;
    private JPanel panelMensaje;
    private JPanel panelContenedor;
    private static final Color BORDER_SOFT  = new Color(0x535353);
    private static final Color CARD_BG      = new Color(255, 255, 255);
    private static final Color GREEN_DARK   = new Color(0x0A6B2A);
    private static final Color GREEN_SOFT   = new Color(0x22C55E);
    private final Font fText   = new Font("Segoe UI", Font.PLAIN, 16);
    private final Font fTitle  = new Font("Segoe UI", Font.BOLD, 22);
    private JDialog dialog;

    /** Uso: AlertaFinalizarCorte.mostrar(owner, onConfirm) */
    public static void mostrar(Window owner, Runnable onConfirm) {
        AlertaFinalizarCorte a = new AlertaFinalizarCorte(owner, onConfirm);
        a.dialog.setVisible(true);
    }

    public AlertaFinalizarCorte(Window owner, Runnable onConfirm) {
        dialog = new JDialog(owner, "Alerta Finalizar Corte de Caja", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(panelMain != null ? panelMain : new JPanel(new BorderLayout()));
        dialog.pack();
        dialog.setLocationRelativeTo(owner);

        if (btnConfirmar != null) {
            btnConfirmar.addActionListener(e -> {
                if (onConfirm != null) onConfirm.run();  // <-- aquí se hace el cierre global
                dialog.dispose();
            });
        }
        if (btnSalir != null) {
            btnSalir.addActionListener(e -> dialog.dispose());
        }
        decorateAsCard(panelMain);
        decorateAsCard(panelBotones);
        decorateAsCard(panelMensaje);
        decorateAsCard(panelContenedor);
        styleExitButton(btnSalir);
        stylePrimaryButton(btnConfirmar);
    }
    // Botón rojo consistente con tu estilo
    private void styleExitButton(JButton b) {
        Color ROJO_BASE    = new Color(0xDC2626);
        Color GRIS_HOVER   = new Color(0xD1D5DB);
        Color GRIS_PRESSED = new Color(0x9CA3AF);
        b.setUI(new Login.ModernButtonUI(ROJO_BASE, GRIS_HOVER, GRIS_PRESSED, Color.BLACK, 22, true));
        b.setBorder(new EmptyBorder(10,18,10,28));
        b.setForeground(Color.WHITE);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
    }

    private void decorateAsCard(JComponent c) {
        if (c == null) return;
        c.setOpaque(true);
        c.setBackground(CARD_BG);
        c.setBorder(new PantallaAdmin.CompoundRoundShadowBorder(14, BORDER_SOFT, new Color(0,0,0,28)));
    }
    private void stylePrimaryButton(JButton b) {
        // Igual que pantallaCajero: usa ModernButtonUI de PantallaAdmin
        b.setUI(new PantallaAdmin.ModernButtonUI(GREEN_DARK, GREEN_SOFT, GREEN_DARK, Color.WHITE, 15, true));
        b.setBorder(new EmptyBorder(10,18,10,28));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setForeground(Color.WHITE);
    }
}
