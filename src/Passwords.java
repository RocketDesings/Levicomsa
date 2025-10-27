// Passwords.java
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public final class Passwords {
    private static final int ITER = 310_000;   // robusto en 2025
    private static final int SALT_LEN = 16;    // 128 bits
    private static final int KEY_LEN = 256;    // bits

    private Passwords(){}

    public static String hash(char[] password) {
        byte[] salt = new byte[SALT_LEN];
        new SecureRandom().nextBytes(salt);
        byte[] dk = pbkdf2(password, salt, ITER, KEY_LEN);
        String out = "pbkdf2$" + ITER + "$" +
                Base64.getEncoder().encodeToString(salt) + "$" +
                Base64.getEncoder().encodeToString(dk);
        wipe(password);
        return out;
    }

    public static boolean verify(char[] password, String stored) {
        try {
            if (stored == null) return false;
            String[] p = stored.split("\\$");
            if (p.length != 4 || !"pbkdf2".equals(p[0])) return false;

            int iter = Integer.parseInt(p[1]);
            byte[] salt = Base64.getDecoder().decode(p[2]);
            byte[] hash = Base64.getDecoder().decode(p[3]);

            byte[] test = pbkdf2(password, salt, iter, hash.length * 8);
            return slowEquals(hash, test);
        } catch (Exception e) {
            return false;
        } finally {
            wipe(password);
        }
    }

    private static byte[] pbkdf2(char[] pwd, byte[] salt, int iter, int keyLenBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pwd, salt, iter, keyLenBits);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return skf.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("PBKDF2 error", e);
        }
    }

    private static boolean slowEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        int r = 0;
        for (int i = 0; i < a.length; i++) r |= a[i] ^ b[i];
        return r == 0;
    }

    private static void wipe(char[] a) {
        if (a != null) java.util.Arrays.fill(a, '\0');
    }
}
