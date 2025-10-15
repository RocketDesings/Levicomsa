import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.text.Normalizer;
import java.util.Objects;

public class AgregarUsuario {
    private JPanel panelDatos;
    private JTextField txtNombre;       // <-- AQUÍ ES "Nombre de Usuario"
    private JTextField txtContrasena;
    private JComboBox<String> cmbActivo;
    private JComboBox<TrabItem> cmbTrabajador;
    private JPanel panelBotones;
    private JButton btnAgregar;
    private JButton btnSalir;
    private JPanel panelInfo;
    private JPanel panelMain;

    // contexto
    private final Window owner;
    private final int usuarioId;
    private final int sucursalId;
    private Runnable onSaved;

    // selección actual
    private Integer selTrabajadorId = null;
    private Integer selRolId = null;
    private String  selPuesto = null;
    private String  nombreEmpleado = null;

    // para saber si el texto fue sugerido por el sistema
    private String ultimoSugerido = null;

    public AgregarUsuario(Window owner, int usuarioId, int sucursalId) {
        this.owner = owner;
        this.usuarioId = usuarioId;
        this.sucursalId = sucursalId;

        if (cmbActivo != null && cmbActivo.getItemCount() == 0) {
            cmbActivo.setModel(new DefaultComboBoxModel<>(new String[]{"Sí", "No"}));
            cmbActivo.setSelectedIndex(0);
        }
        cargarTrabajadores();

        if (cmbTrabajador != null) cmbTrabajador.addActionListener(e -> onTrabajadorChange());
        if (btnAgregar != null) btnAgregar.addActionListener(e -> guardar());
        if (btnSalir   != null) btnSalir.addActionListener(e -> cerrar());
    }

    public void setOnSaved(Runnable r) { this.onSaved = r; }

    public static JDialog createDialog(Window owner, int usuarioId, int sucursalId, Runnable onSaved) {
        AgregarUsuario ui = new AgregarUsuario(owner, usuarioId, sucursalId);
        ui.setOnSaved(onSaved);
        JDialog d = new JDialog(owner, "Agregar usuario", Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(ui.panelMain);
        d.setMinimumSize(new Dimension(720, 460));
        d.pack();
        d.setLocationRelativeTo(owner);
        return d;
    }

    // ================= Lógica =================
    private void cargarTrabajadores() {
        if (cmbTrabajador == null) return;
        cmbTrabajador.removeAllItems();
        final String sql = "SELECT id, nombre, puesto FROM trabajadores WHERE activo=1 ORDER BY nombre";
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                cmbTrabajador.addItem(new TrabItem(
                        rs.getInt("id"),
                        nvl(rs.getString("nombre")),
                        nvl(rs.getString("puesto"))
                ));
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(panelMain, "Error al cargar trabajadores: " + e.getMessage());
            e.printStackTrace();
        }
        if (cmbTrabajador.getItemCount() > 0) {
            cmbTrabajador.setSelectedIndex(0);
            onTrabajadorChange();
        }
    }

    private void onTrabajadorChange() {
        TrabItem it = (TrabItem) (cmbTrabajador != null ? cmbTrabajador.getSelectedItem() : null);
        if (it == null) return;

        selTrabajadorId = it.id;
        selPuesto = it.puesto;
        nombreEmpleado = it.nombre;

        // resuelve rol_id (con tolerancia a roles compuestos)
        selRolId = buscarRolIdPorPuesto(it.puesto);
        if (selRolId == null) {
            selRolId = elegirRolInteractivo(
                    "No se encontró un rol que coincida con el puesto '" + it.puesto + "'. Selecciona uno:");
            if (selRolId == null) return;
        }

        // Sugerir un nombre de usuario único; sólo lo sobreescribimos si el usuario no ha escrito algo propio
        String actual = tx(txtNombre);
        if (actual.isBlank() || actual.equalsIgnoreCase(ultimoSugerido)) {
            String base = sugerirUsuarioBaseDesde(it.nombre, it.puesto);
            String disponible = sugerirUsuarioDisponible(base);
            ultimoSugerido = disponible;
            if (txtNombre != null) txtNombre.setText(disponible);
        }
    }

    private Integer buscarRolIdPorPuesto(String puesto) {
        if (puesto == null) return null;
        String pNorm = norm(puesto);
        String[] tokens = pNorm.split("[/\\s]+");

        Integer bestId = null; int bestScore = -1; int bestLen = Integer.MAX_VALUE;
        final String sql = "SELECT id, nombre FROM roles";
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt(1);
                String rNorm = norm(nvl(rs.getString(2)));
                int score = scoreMatch(pNorm, tokens, rNorm);
                if (score > bestScore || (score == bestScore && rNorm.length() < bestLen)) {
                    bestScore = score; bestId = id; bestLen = rNorm.length();
                }
                if (score == 3) break;
            }
        } catch (SQLException ignore) {}
        return (bestScore <= 0) ? null : bestId;
    }
    private static int scoreMatch(String puestoNorm, String[] tokens, String rolNorm) {
        if (rolNorm.equals(puestoNorm)) return 3;
        if (rolNorm.contains(puestoNorm)) return 2;
        for (String t : tokens) if (!t.isBlank() && rolNorm.contains(t)) return 1;
        if (puestoNorm.contains("admin") && rolNorm.contains("admin")) return 1;
        return 0;
    }
    private static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase().trim();
        return n.replaceAll("[\\s]+", " ");
    }

    // ====== generación de usuario y verificación anti-duplicate ======
    private static String sugerirUsuarioBaseDesde(String nombre, String puesto) {
        String p = norm(puesto);
        if (p.contains("admin"))    return "admin";
        if (p.contains("cajero"))   return "cajero";
        if (p.contains("asesor"))   return "asesor";
        if (p.contains("contador")) return "contador";
        // base por nombre: primera palabra + apellido/segunda palabra
        String base = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}","")
                .replaceAll("[^A-Za-z0-9 ]","")
                .trim().replaceAll("\\s+"," ");
        String[] parts = base.split(" ");
        if (parts.length == 0) return "usuario";
        if (parts.length == 1) return parts[0].toLowerCase();
        return (parts[0] + parts[1]).toLowerCase();
    }

    /** Devuelve un username disponible. Si 'base' existe, prueba base2, base3, ... */
    private String sugerirUsuarioDisponible(String base) {
        String u = base;
        int n = 1;
        while (existeUsuario(u)) {
            n++;
            u = base + n;
        }
        return u;
    }

    /** true si ya existe en BD (respeta tu UNIQUE uq_usuario). */
    private boolean existeUsuario(String usuario) {
        final String sql = "SELECT 1 FROM Usuarios WHERE usuario = ? LIMIT 1";
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            // Si falla la consulta, por seguridad consideramos que "existe" para no chocar con UNIQUE
            return true;
        }
    }

    // ================= Guardar =================
    private void guardar() {
        if (selTrabajadorId == null) {
            JOptionPane.showMessageDialog(panelMain, "Selecciona un trabajador."); return;
        }
        if (selRolId == null) {
            JOptionPane.showMessageDialog(panelMain, "Selecciona un rol válido."); return;
        }
        String usuarioLogin = tx(txtNombre); // NOMBRE DE USUARIO desde el formulario
        if (usuarioLogin.isBlank()) {
            JOptionPane.showMessageDialog(panelMain, "El nombre de usuario no puede estar vacío."); return;
        }
        // si está tomado, sugerimos el siguiente disponible y no insertamos hasta que el usuario confirme/edite
        if (existeUsuario(usuarioLogin)) {
            String sugerencia = sugerirUsuarioDisponible(usuarioLogin);
            ultimoSugerido = sugerencia;
            if (txtNombre != null) txtNombre.setText(sugerencia);
            JOptionPane.showMessageDialog(panelMain,
                    "Ese usuario ya existe. Te sugerí uno disponible: " + sugerencia + "\n" +
                            "Si estás de acuerdo, vuelve a presionar Agregar.");
            return;
        }

        String contrasena = tx(txtContrasena);
        if (contrasena.isBlank()) {
            JOptionPane.showMessageDialog(panelMain, "La contraseña no puede estar vacía."); return;
        }
        int activo = parseActivo(cmbActivo);

        // INSERT exacto con tus campos; usamos backticks por la columna `contraseña`
        final String sql = "INSERT INTO Usuarios " +
                "(usuario, nombre, `contraseña`, rol_id, trabajador_id, activo, creado_en) " +
                "VALUES (?, ?, ?, ?, ?, ?, NOW())";

        try (Connection con = DB.get()) {
            if (usuarioId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_user_id=?")) { p.setInt(1, usuarioId); p.executeUpdate(); }
            if (sucursalId > 0) try (PreparedStatement p = con.prepareStatement("SET @app_sucursal_id=?")) { p.setInt(1, sucursalId); p.executeUpdate(); }

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, usuarioLogin);
                ps.setString(2, nvl(nombreEmpleado));   // nombre del empleado (desde trabajador seleccionado)
                ps.setString(3, contrasena);
                ps.setInt  (4, selRolId);
                ps.setInt  (5, selTrabajadorId);
                ps.setInt  (6, activo);

                int n = ps.executeUpdate();
                if (n > 0) {
                    JOptionPane.showMessageDialog(panelMain, "Usuario agregado correctamente.");
                    if (onSaved != null) onSaved.run();
                    cerrar();
                } else {
                    JOptionPane.showMessageDialog(panelMain, "No se pudo agregar el usuario.");
                }
            }
        } catch (SQLIntegrityConstraintViolationException dup) {
            JOptionPane.showMessageDialog(panelMain,
                    "No se pudo agregar: restricción/duplicado.\n" + dup.getMessage());
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(panelMain, "Error BD: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void cerrar() {
        Window w = SwingUtilities.getWindowAncestor(panelMain);
        if (w instanceof JDialog d) d.dispose();
    }

    // ===== helpers =====
    private static String tx(JTextField t) { return (t != null && t.getText() != null) ? t.getText().trim() : ""; }
    private static int parseActivo(JComboBox<String> c) {
        if (c == null || c.getSelectedItem() == null) return 1;
        String s = Objects.toString(c.getSelectedItem(), "").trim().toLowerCase();
        return (s.startsWith("s") || s.equals("1") || s.equals("true")) ? 1 : 0;
    }
    private static String nvl(String s) { return s != null ? s : ""; }

    // ===== DTOs =====
    static class TrabItem {
        final int id; final String nombre; final String puesto;
        TrabItem(int id, String nombre, String puesto){ this.id=id; this.nombre=nombre; this.puesto=puesto; }
        @Override public String toString(){ return nombre + " — " + puesto; }
    }

    /** Muestra un combo con todos los roles y devuelve el id elegido; null si cancelan. */
    private Integer elegirRolInteractivo(String mensaje) {
        DefaultComboBoxModel<RoleItem> model = new DefaultComboBoxModel<>();
        try (Connection con = DB.get();
             PreparedStatement ps = con.prepareStatement("SELECT id, nombre FROM roles ORDER BY nombre");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) model.addElement(new RoleItem(rs.getInt(1), rs.getString(2)));
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(panelMain, "Error cargando roles: " + e.getMessage());
            return null;
        }

        JComboBox<RoleItem> cb = new JComboBox<>(model);
        int ok = JOptionPane.showConfirmDialog(
                panelMain,
                new Object[]{mensaje, cb},
                "Elegir rol",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        return (ok == JOptionPane.OK_OPTION && cb.getSelectedItem() != null)
                ? ((RoleItem) cb.getSelectedItem()).id
                : null;
    }

    /** Item para el combo de roles. */
    static class RoleItem {
        final int id;
        final String nombre;
        RoleItem(int id, String nombre) { this.id = id; this.nombre = nombre; }
        @Override public String toString() { return nombre + " (id " + id + ")"; }
    }

}
