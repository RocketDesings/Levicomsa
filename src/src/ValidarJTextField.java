import javax.swing.JTextField;
import java.util.regex.Pattern;

public class ValidarJTextField {

    public static boolean validarNoVacio(JTextField campo) {
        return campo.getText() != null && !campo.getText().trim().isEmpty();
    }

    public static boolean validarSoloNumeros(JTextField campo) {
        return campo.getText().matches("\\d+");
    }

    public static boolean validarSoloLetras(JTextField campo) {
        return campo.getText().matches("[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+");
    }

    // Nuevo: valida que NO contenga ningún número
    public static boolean validarNoContieneNumeros(JTextField campo) {
        return !campo.getText().matches(".*\\d.*");
    }

    public static boolean validarLongitudExacta(JTextField campo, int longitud) {
        return campo.getText().length() == longitud;
    }

    public static boolean validarLongitudMinima(JTextField campo, int min) {
        return campo.getText().length() >= min;
    }

    public static boolean validarLongitudMaxima(JTextField campo, int max) {
        return campo.getText().length() <= max;
    }

    public static boolean validarEmail(JTextField campo) {
        String emailRegex = "^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$";
        return Pattern.matches(emailRegex, campo.getText());
    }

    public static boolean validarAlfanumerico(JTextField campo) {
        return campo.getText().matches("[a-zA-Z0-9áéíóúÁÉÍÓÚñÑ\\s]+");
    }

    public static boolean validarConRegex(JTextField campo, String regex) {
        return campo.getText().matches(regex);
    }

}
