import javax.swing.*;

public class AutoActualizarTabla {
    private final Runnable tareaActualizar;
    private final int intervaloMilisegundos;
    private Thread hilo;

    public AutoActualizarTabla(Runnable tareaActualizar, int intervaloMilisegundos) {
        this.tareaActualizar = tareaActualizar;
        this.intervaloMilisegundos = intervaloMilisegundos;
    }

    public void iniciar() {
        if (hilo != null && hilo.isAlive()) return;

        hilo = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    SwingUtilities.invokeLater(tareaActualizar);
                    Thread.sleep(intervaloMilisegundos);
                } catch (InterruptedException e) {
                    System.out.println("Autoactualización interrumpida.");
                    break;
                }
            }
        });

        hilo.setDaemon(true);
        hilo.start();
    }

    public void detener() {
        if (hilo != null && hilo.isAlive()) {
            hilo.interrupt();
        }
    }
}
