import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class JDBC {
    // Cambia estos datos por los de tu base de datos
    private static final String URL = "jdbc:mysql://sql5.freesqldatabase.com:3306/sql5790899";
    private static final String USUARIO = "sql5790899";
    private static final String CONTRASEÑA = "lmKXGpcVx4"; // <-- cámbialo por tu contraseña real

    public static Connection obtenerConexion() {
        try {
            // Cargar el driver (opcional en versiones nuevas de Java, pero recomendable)
            Class.forName("com.mysql.cj.jdbc.Driver");
            // Establecer la conexión
            return DriverManager.getConnection(URL, USUARIO, CONTRASEÑA);
        } catch (ClassNotFoundException e) {
            System.out.println("Error: No se encontró el driver de MySQL.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("Error al conectar a la base de datos.");
            e.printStackTrace();
        }
        return null;
    }
}

