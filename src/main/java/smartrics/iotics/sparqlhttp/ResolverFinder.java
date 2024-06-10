package smartrics.iotics.sparqlhttp;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Map;

public class ResolverFinder {

    public static String findResolver(String host) {
        try {
            String resolver;
            URL url = URI.create("https://" + host + "/index.json").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) { // Success
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();
                Gson gson = new Gson();
                Map map = gson.fromJson(content.toString(), Map.class);
                resolver = map.get("resolver").toString();
            } else {
                throw new IllegalArgumentException("Unable to access host " + host + ". Http response code: " + responseCode);
            }

            // Disconnect the connection
            conn.disconnect();
            return resolver;
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to get resolver", e);
        }
    }
}
