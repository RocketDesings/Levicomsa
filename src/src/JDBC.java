import java.sql.Connection;
import java.sql.SQLException;

public final class JDBC {
    private JDBC() {}
    public static Connection obtenerConexion() throws SQLException {
        return DB.get();
    }
}
