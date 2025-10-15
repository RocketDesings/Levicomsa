import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

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


    public HerramientasAdmin(Window owner, int usuarioId, int sucursalId) {
        super(owner, "Herramientas de administración", ModalityType.MODELESS);
        this.usuarioId = usuarioId;
        this.sucursalId = sucursalId;

        setContentPane(panelMain);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(560, 380));
        pack();
        setLocationRelativeTo(owner);

        if (btnCancelar != null) btnCancelar.addActionListener(e -> dispose());

        // tal cual lo pediste: listeners simples que llaman a métodos
        if (btnGestionarSucursales != null) {
            btnGestionarSucursales.addActionListener(e -> abrirCRUDSucursales());
        }
        if (btnGestionarEmpleados != null) {
            btnGestionarEmpleados.addActionListener(e -> abrirCRUDEmpleados());
        }
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
        // En el constructor de HerramientasAdmin
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

        if (btnGestionarServicios != null) {
            btnGestionarServicios.addActionListener(e -> abrirCRUDServicios());
        }
    }

    // --- métodos simples que abren/traen al frente y evitan duplicados ---
    private void abrirCRUDSucursales() {
        if (dlgSucursales != null && dlgSucursales.isDisplayable()) {
            dlgSucursales.toFront();
            dlgSucursales.requestFocus();
            return;
        }
        dlgSucursales = CRUDSucursales.createDialog(this, usuarioId, sucursalId);
        dlgSucursales.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed (java.awt.event.WindowEvent e) { dlgSucursales = null; }
            @Override public void windowClosing(java.awt.event.WindowEvent e) { dlgSucursales = null; }
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
        dlgEmpleados.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed (java.awt.event.WindowEvent e) { dlgEmpleados = null; }
            @Override public void windowClosing(java.awt.event.WindowEvent e) { dlgEmpleados = null; }
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
}
