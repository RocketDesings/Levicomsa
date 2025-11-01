import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.RoundRectangle2D;

public class AlertaFinalizarCorte {
    private JPanel panelMain;
    private JButton btnConfirmar;
    private JButton btnSalir;

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

        dialog.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                dialog.setShape(new RoundRectangle2D.Double(0, 0, dialog.getWidth(), dialog.getHeight(), 16, 16));
            }
        });

        if (btnConfirmar != null) {
            btnConfirmar.addActionListener(e -> {
                if (onConfirm != null) onConfirm.run();  // <-- aquí se hace el cierre global
                dialog.dispose();
            });
        }
        if (btnSalir != null) {
            btnSalir.addActionListener(e -> dialog.dispose());
        }
    }
}
