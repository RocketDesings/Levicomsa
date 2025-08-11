import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class JDBC {
    // Cambia estos datos por los de tu base de datos
    private static final String URL = "jdbc:mysql://mysql-rocketdesigners.alwaysdata.net:3306/rocketdesigners_levicomsa"; // Ajusta el nombre de la BD
    private static final String USUARIO = "426036";
    private static final String CONTRASEÑA = "Hola1243"; // <-- cámbiala por tu contraseña real

    public static Connection obtenerConexion() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver"); // cargar driver
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
/*
    // Metodo para probar la conexión
    public static void main(String[] args) {
        Connection conn = obtenerConexion();
        if (conn != null) {
            System.out.println("¡Conexión exitosa a la base de datos!");
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Fallo la conexión.");
        }
    }
}
*/
