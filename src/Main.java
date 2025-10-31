public class Main {
    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { DB.close(); } catch (Throwable ignore) {} }, "DB-ShutdownHook"));
        System.out.println(System.getProperty("os.arch"));
        System.out.println(System.getProperty("sun.arch.data.model"));
        javax.swing.SwingUtilities.invokeLater(PantallaAdmin::new);
    }
}