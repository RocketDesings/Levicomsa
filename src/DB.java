import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public final class DB {
    private static volatile HikariDataSource DS;   // lazy
    private static final Object LOCK = new Object();

    private DB() {}

    /** Obtiene una conexión (inicializa el pool si aún no existe). */
    public static Connection get() throws SQLException {
        if (DS == null || DS.isClosed()) {
            synchronized (LOCK) {
                if (DS == null || DS.isClosed()) init();
            }
        }
        return DS.getConnection();
    }

    /** Cierra el pool; la siguiente llamada a get() lo recrea. */
    public static void reload() { close(); }

    public static void close() {
        synchronized (LOCK) {
            if (DS != null) {
                try { DS.close(); } finally { DS = null; }
            }
        }
    }

    // -------------------- Init --------------------
    private static void init() {
        Properties p = loadProps(); // lanza si no existe
        try {
            // Credenciales (env → props)
            String url  = firstNonEmpty(System.getenv("DB_URL"),  p.getProperty("url"));
            String user = firstNonEmpty(System.getenv("DB_USER"), p.getProperty("user"));
            String pass = firstNonEmpty(System.getenv("DB_PASS"), p.getProperty("pass"));
            if (isBlank(url) || isBlank(user) || pass == null) {
                throw new IllegalStateException("Config incompleta: url/user/pass");
            }

            // Tuning (env → props → default)
            int  maxPool     = getInt ("DB_MAX_POOL_SIZE", p, "pool.maxPoolSize",         10, 1, 200);
            int  minIdle     = getInt ("DB_MIN_IDLE",      p, "pool.minIdle",              2,  0, maxPool);
            long idleMs      = getLong("DB_IDLE_TIMEOUT",  p, "pool.idleTimeoutMs",   60_000L, 10_000L, 3_600_000L);
            long connMs      = getLong("DB_CONN_TIMEOUT",  p, "pool.connectionTimeoutMs",10_000L, 1_000L, 60_000L);
            long lifeMs      = getLong("DB_MAX_LIFETIME",  p, "pool.maxLifetimeMs", 1_800_000L, 30_000L, 3_600_000L); // 30 min
            long keepAliveMs = getLong("DB_KEEPALIVE",     p, "pool.keepaliveMs",     300_000L, 30_000L, 900_000L);   // 5 min
            long validMs     = getLong("DB_VALIDATION",    p, "pool.validationTimeoutMs", 5_000L, 1_000L, 30_000L);
            long leakMs      = getLong("DB_LEAK_DETECT",   p, "pool.leakDetectionMs",      0L,   0L,      3_600_000L);
            String poolName  = firstNonEmpty(System.getenv("DB_POOL_NAME"), p.getProperty("pool.name"), "CRM-Pool");



            HikariConfig cfg = new HikariConfig();
            cfg.setPoolName(poolName);
            cfg.setJdbcUrl(url);
            cfg.setUsername(user);
            cfg.setPassword(pass);
            cfg.setConnectionInitSql("SET time_zone = 'America/Mazatlan'");
            cfg.setMaximumPoolSize(maxPool);
            cfg.setMinimumIdle(minIdle);
            cfg.setIdleTimeout(idleMs);
            cfg.setConnectionTimeout(connMs);
            cfg.setMaxLifetime(lifeMs);
            cfg.setKeepaliveTime(keepAliveMs);
            cfg.setValidationTimeout(validMs);
            if (leakMs > 0) cfg.setLeakDetectionThreshold(leakMs); // sólo si lo pides

            // Props útiles para MySQL si no van en la URL
            maybeSetMySqlProps(cfg);

            DS = new HikariDataSource(cfg);

            // Prueba inicial
            try (Connection c = DS.getConnection()) { /* ok */ }
        } catch (Exception e) {
            throw new RuntimeException("Error inicializando pool de conexiones", e);
        }
    }

    private static void maybeSetMySqlProps(HikariConfig cfg) {
        setIfAbsent(cfg, "useUnicode", "true");
        setIfAbsent(cfg, "characterEncoding", "utf8");
        setIfAbsent(cfg, "cachePrepStmts", "true");
        setIfAbsent(cfg, "prepStmtCacheSize", "256");
        setIfAbsent(cfg, "prepStmtCacheSqlLimit", "2048");
        setIfAbsent(cfg, "useServerPrepStmts", "true");
        setIfAbsent(cfg, "rewriteBatchedStatements", "true");
        setIfAbsent(cfg, "serverTimezone", "America/Mazatlan");
    }

    private static void setIfAbsent(HikariConfig cfg, String key, String value) {
        if (cfg.getDataSourceProperties().getProperty(key) == null) {
            cfg.addDataSourceProperty(key, value);
        }
    }

    // -------------------- Helpers --------------------
    private static String firstNonEmpty(String... values) {
        if (values == null) return null;
        for (String v : values) if (!isBlank(v)) return v;
        return null;
    }
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private static int getInt(String env, Properties p, String key, int def, int min, int max) {
        String v = firstNonEmpty(System.getenv(env), p.getProperty(key));
        try { int x = (v == null) ? def : Integer.parseInt(v.trim()); return Math.max(min, Math.min(max, x)); }
        catch (Exception ignore) { return def; }
    }
    private static long getLong(String env, Properties p, String key, long def, long min, long max) {
        String v = firstNonEmpty(System.getenv(env), p.getProperty(key));
        try { long x = (v == null) ? def : Long.parseLong(v.trim()); return Math.max(min, Math.min(max, x)); }
        catch (Exception ignore) { return def; }
    }

    // -------------------- Carga de propiedades --------------------
    private static Properties loadProps() {
        Properties p = new Properties();
        InputStream tmp = Thread.currentThread().getContextClassLoader().getResourceAsStream("db.properties");
        if (tmp == null) tmp = DB.class.getResourceAsStream("/db.properties");

        if (tmp == null) {
            String[] fallbacks = {"db.properties", "resources/db.properties", "src/main/resources/db.properties", "src/resources/db.properties"};
            for (String f : fallbacks) {
                try {
                    Path path = Path.of(f);
                    if (Files.exists(path)) {
                        tmp = Files.newInputStream(path);
                        System.err.println("[DB] Cargando propiedades desde fallback: " + path.toAbsolutePath());
                        break;
                    }
                } catch (IOException ignore) {}
            }
        }

        if (tmp == null) {
            String wd = System.getProperty("user.dir");
            throw new IllegalStateException(
                    "No se encontró db.properties en el classpath.\n" +
                            "Working dir: " + wd + "\n" +
                            "Ponlo en 'resources/db.properties' y verifica que se copie a out/production/<modulo>/"
            );
        }

        try (InputStream is = tmp) {
            p.load(is);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer db.properties", e);
        }
        return p;
    }
}
