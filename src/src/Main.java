public class Main {
    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { DB.close(); } catch (Throwable ignore) {} }, "DB-ShutdownHook"));
        javax.swing.SwingUtilities.invokeLater(Login::new);
    }
}