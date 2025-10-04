import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class RegistrarSalida {
    private JPanel panelMain;
    private JPanel panelInfo;
    private JPanel panelBotones;
    private JButton btnConfirmar;
    private JButton btnCancelar;
    private JTextField txtMonto;
    private JTextField txtMotivoSalida;

    private static volatile boolean ABIERTO = false;   // ← candado global
    public static boolean isAbierto() { return ABIERTO; }

    private final int sucursalId;
    private final int usuarioId;
    private JFrame frame;

    public static void mostrar(int sucursalId, int usuarioId) {
        if (ABIERTO) { JOptionPane.showMessageDialog(null, "Ya está abierto 'Registrar SALIDA'."); return; }
        new RegistrarSalida(sucursalId, usuarioId);
    }

    public RegistrarSalida(int sucursalId, int usuarioId) {
        this.sucursalId = sucursalId;
        this.usuarioId  = usuarioId;

        ABIERTO = true;

        frame = new JFrame("Registrar SALIDA");
        frame.setContentPane(panelMain);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);

        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e)  { ABIERTO = false; }
            @Override public void windowClosing(WindowEvent e) { ABIERTO = false; }
        });

        btnCancelar.addActionListener(e -> frame.dispose());
        btnConfirmar.addActionListener(e -> onConfirmar(frame));

        frame.setVisible(true);
    }

    private void onConfirmar(JFrame owner) {
        String rawMonto = txtMonto.getText() != null ? txtMonto.getText().trim() : "";
        if (rawMonto.isEmpty()) { JOptionPane.showMessageDialog(panelMain, "Ingresa un monto."); return; }

        BigDecimal monto;
        try {
            rawMonto = rawMonto.replace(",", ".");
            monto = new BigDecimal(rawMonto).setScale(2, RoundingMode.HALF_UP);
            if (monto.compareTo(BigDecimal.ZERO) <= 0) { JOptionPane.showMessageDialog(panelMain, "El monto debe ser mayor a 0."); return; }
        } catch (Exception ex) { JOptionPane.showMessageDialog(panelMain, "Monto inválido."); return; }

        String motivo = txtMotivoSalida.getText() != null ? txtMotivoSalida.getText().trim() : "";
        if (motivo.isEmpty()) { JOptionPane.showMessageDialog(panelMain, "Indica el motivo de la salida."); return; }

        btnConfirmar.setEnabled(false);

        Connection con = null;
        try {
            con = DB.get();
            con.setAutoCommit(false);

            try (PreparedStatement ps = con.prepareStatement("SET @app_user_id = ?")) {
                ps.setInt(1, usuarioId);
                ps.executeUpdate();
            }

            final String sql = "INSERT INTO caja_movimientos " +
                    "(sucursal_id, usuario_id, tipo, monto, descripcion, cobro_id) " +
                    "VALUES (?, ?, 'SALIDA', ?, ?, NULL)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, sucursalId);
                ps.setInt(2, usuarioId);
                ps.setBigDecimal(3, monto);
                ps.setString(4, motivo);
                ps.executeUpdate();
            }

            con.commit();
            JOptionPane.showMessageDialog(panelMain, "Salida registrada: $" + monto.toPlainString() + "\nMotivo: " + motivo);
            owner.dispose();

        } catch (Exception ex) {
            try { if (con != null) con.rollback(); } catch (Exception ignore) {}
            ex.printStackTrace();
            JOptionPane.showMessageDialog(panelMain, "No se pudo registrar la salida:\n" + ex.getMessage());
            btnConfirmar.setEnabled(true);
        } finally {
            try { if (con != null) con.setAutoCommit(true); } catch (Exception ignore) {}
            try { if (con != null) con.close(); } catch (Exception ignore) {}
        }
    }
}
