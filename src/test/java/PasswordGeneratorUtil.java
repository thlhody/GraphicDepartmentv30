
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordGeneratorUtil {
    public static void main(String[] args) {
        // The password you want to encode
        String rawPassword = "admin";

        // Create BCryptPasswordEncoder
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        // Encode the password
        String encodedPassword = encoder.encode(rawPassword);

        // Print both passwords for comparison
        System.out.println("Raw Password: " + rawPassword);
        System.out.println("Encoded Password: " + encodedPassword);

        // Verify the password matches (demonstration purposes)
        boolean matches = encoder.matches(rawPassword, encodedPassword);
        System.out.println("Password Matches: " + matches);

        // Print a sample JSON user object
        System.out.println("\nSample user JSON object:");
        System.out.println("{\n" +
                "  \"userId\": 1,\n" +
                "  \"name\": \"Administrator\",\n" +
                "  \"employeeId\": 1,\n" +
                "  \"schedule\": 1,\n" +
                "  \"username\": \"admin\",\n" +
                "  \"password\": \"" + encodedPassword + "\",\n" +
                "  \"role\": \"ADMIN\"\n" +
                "}");
    }
}