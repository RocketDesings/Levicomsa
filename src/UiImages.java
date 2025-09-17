import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class UiImages {
    private UiImages() {}

    // cache por (ruta + tamaño lógico + escala del monitor)
    private static final Map<String, SoftReference<ImageIcon>> CACHE = new ConcurrentHashMap<>();

    /** Pone un icono escalado HQ en el label. El tamaño es "lógico" (independiente del HiDPI). */
    public static void setIcon(JLabel label, String classpathImage, int logicalSize) {
        ImageIcon icon = loadIcon(label, classpathImage, logicalSize);
        if (icon != null) label.setIcon(icon);
    }

    /** Versión circular con borde opcional (borderPx=0 para sin borde). */
    public static void setIconCircular(JLabel label, String classpathImage, int logicalSize,
                                       int borderPx, Color borderColor) {
        ImageIcon icon = loadIcon(label, classpathImage, logicalSize);
        if (icon == null) return;

        int w = icon.getIconWidth(), h = icon.getIconHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        enableHQ(g);

        Shape clip = new Ellipse2D.Float(0, 0, w, h);
        g.setClip(clip);
        g.drawImage(((ImageIcon)icon).getImage(), 0, 0, null);
        g.setClip(null);

        if (borderPx > 0) {
            g.setColor(borderColor != null ? borderColor : new Color(0,0,0,40));
            g.setStroke(new BasicStroke(borderPx));
            g.draw(clip);
        }
        g.dispose();

        label.setIcon(new ImageIcon(dst));
    }

    /** Carga y escala un icono HQ. */
    public static ImageIcon loadIcon(Component target, String classpathImage, int logicalSize) {
        double scale = getScale(target);
        int px = (int)Math.round(logicalSize * scale);
        String key = classpathImage + "@" + logicalSize + "x" + scale;

        ImageIcon cached = getCached(key);
        if (cached != null) return cached;

        BufferedImage src = readClasspathImage(classpathImage);
        if (src == null) {
            System.err.println("[UiImages] No se encontró " + classpathImage + " en el classpath.");
            return null;
        }
        BufferedImage scaled = scaleHQ(src, px, px);
        ImageIcon icon = new ImageIcon(scaled);
        CACHE.put(key, new SoftReference<>(icon));
        return icon;
    }

    // ---------- internos ----------
    private static ImageIcon getCached(String key) {
        SoftReference<ImageIcon> ref = CACHE.get(key);
        return ref != null ? ref.get() : null;
    }

    private static BufferedImage readClasspathImage(String path) {
        try {
            URL url = UiImages.class.getResource(path);
            if (url == null) return null;
            return ImageIO.read(url);
        } catch (Exception e) { return null; }
    }

    private static BufferedImage scaleHQ(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dst.createGraphics();
        enableHQ(g2);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return dst;
    }

    private static void enableHQ(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private static double getScale(Component c) {
        try {
            GraphicsConfiguration gc = (c != null) ? c.getGraphicsConfiguration() :
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
            return (gc != null) ? gc.getDefaultTransform().getScaleX() : 1.0;
        } catch (Exception e) { return 1.0; }
    }
}
