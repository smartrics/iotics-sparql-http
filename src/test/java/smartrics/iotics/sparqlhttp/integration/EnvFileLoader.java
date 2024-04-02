package smartrics.iotics.sparqlhttp.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class EnvFileLoader {
    public static void loadEnvFile(String filePath) throws IOException {
        Path absolutePath = Paths.get(filePath).toAbsolutePath();
        List<String> lines = Files.readAllLines(absolutePath);
        for (String line : lines) {
            // Skip comments and empty lines
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }

            // Split each line into key/value pairs and set them as system properties
            String[] parts = line.split("=", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                System.setProperty(key, value);
            }
        }
    }
}
