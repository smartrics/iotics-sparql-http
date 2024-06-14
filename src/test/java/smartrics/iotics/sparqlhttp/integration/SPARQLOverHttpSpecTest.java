package smartrics.iotics.sparqlhttp.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class SPARQLOverHttpSpecTest {

    private OkHttpClient client;
    private String endpointUrl;
    private String bearer;

    @BeforeAll
    public static void setUpClass() {
        try {
            EnvFileLoader.loadEnvFile(".env");
        } catch (IOException e) {
            throw new RuntimeException("unable to find the .env file", e);
        }
    }

    @BeforeEach
    public void setup() {
        client = new OkHttpClient();

        this.bearer = System.getProperty("USER_KEY") + ":" + System.getProperty("USER_SEED");
        int hostPort = Integer.parseInt(System.getProperty("PORT"));
        this.endpointUrl = "http://localhost:" + hostPort + "/sparql/local";
    }

    @Test
    @DisplayName("Test SPARQL SELECT Query via GET")
    public void testSelectQueryViaGet() throws IOException {
        String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10";
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = endpointUrl + "?query=" + encodedQuery;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + bearer)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            assertEquals(200, response.code());
            String responseBody = response.body().string();
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            assertThat(json.get("results"), notNullValue());
        }
    }

    @Test
    @DisplayName("Test SPARQL ASK Query via GET")
    public void testAskQueryViaGet() throws IOException {
        String query = "ASK WHERE { ?s ?p ?o }";
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = endpointUrl + "?query=" + encodedQuery;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + bearer)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            assertEquals(200, response.code());
            String responseBody = response.body().string();
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            assertThat(json.get("boolean"), notNullValue());
        }
    }

    @Test
    @DisplayName("Test SPARQL DESCRIBE Query via GET")
    public void testDescribeQueryViaGet() throws IOException {
        String resourceUri = findResourceUri();
        String query = "DESCRIBE <" + resourceUri + ">";
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = endpointUrl + "?query=" + encodedQuery;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + bearer)
                .addHeader("Accept", "application/rdf+xml")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if(response.code() != 200) {
                fail(response.message());
            }
            assertEquals(200, response.code());
            String responseBody = response.body() != null ? response.body().string() : null;
            assertThat(responseBody, notNullValue());
            // Assuming you want to check for RDF content, you might need to parse it differently
            assertThat(responseBody, containsString("rdf:RDF"));
        }
    }

    private String findResourceUri() throws IOException {
        String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 1";
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = endpointUrl + "?query=" + encodedQuery;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + bearer)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            assertEquals(200, response.code());
            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            return json.get("results").getAsJsonObject()
                    .get("bindings").getAsJsonArray()
                    .get(0).getAsJsonObject()
                    .get("s").getAsJsonObject()
                    .get("value").getAsString();
        }
    }

    @Test
    @DisplayName("Test SPARQL CONSTRUCT Query via GET")
    public void testConstructQueryViaGet() throws IOException {
        String query = "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 10";
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = endpointUrl + "?query=" + encodedQuery;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + bearer)
                .addHeader("Accept", "text/turtle")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if(response.code() != 200) {
                fail(response.message());
            }
            assertEquals(200, response.code());
            assert response.body() != null;
            String responseBody = response.body().string();
            assertThat(responseBody, notNullValue());
        }
    }

    @Test
    @DisplayName("Test SPARQL Service Description")
    public void testServiceDescription() throws IOException {
        String url = endpointUrl;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + bearer)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if(response.code() != 200) {
                fail(response.message());
            }
            assertEquals(200, response.code());
            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            assertThat(json.get("@context"), notNullValue());
        }
    }

    @Test
    @DisplayName("Test SPARQL Query via POST with application/x-www-form-urlencoded")
    public void testQueryViaPostForm() throws IOException {
        String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10";
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = endpointUrl;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + bearer)
                .post(okhttp3.RequestBody.create(encodedQuery, okhttp3.MediaType.parse("application/x-www-form-urlencoded")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if(response.code() != 200) {
                fail(response.message());
            }
            assertEquals(200, response.code());
            String responseBody = response.body().string();
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            assertThat(json.get("results"), notNullValue());
        }
    }

    @Test
    @DisplayName("Test SPARQL Query via POST with application/sparql-query")
    public void testQueryViaPostSparql() throws IOException {
        String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10";
        String url = endpointUrl;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + bearer)
                .addHeader("Accept", "*/*")
                .post(okhttp3.RequestBody.create(query, okhttp3.MediaType.parse("application/sparql-query")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if(response.code() != 200) {
                fail(response.message());
            }
            assertEquals(200, response.code());
            String responseBody = response.body().string();
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            assertThat(json.get("results"), notNullValue());
        }
    }

    @Test
    @DisplayName("Test SPARQL Query with default graph URI")
    public void testQueryWithDefaultGraphURI() throws IOException {
        String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10";
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String defaultGraphUri = "http://example.org/default-graph";
        String url = endpointUrl + "?query=" + encodedQuery + "&default-graph-uri=" + URLEncoder.encode(defaultGraphUri, StandardCharsets.UTF_8);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + bearer)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            assertEquals(400, response.code());
            JsonObject json = JsonParser.parseString(response.message()).getAsJsonObject();
            assertEquals("RDF datasets not allowed", json.get("message").getAsString());
        }
    }

    @Test
    @DisplayName("Test SPARQL Query with named graph URI")
    public void testQueryWithNamedGraphURI() throws IOException {
        String query = "SELECT * WHERE { GRAPH <http://example.org/named-graph> { ?s ?p ?o } } LIMIT 10";
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = endpointUrl + "?query=" + encodedQuery;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + bearer)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if(response.code() != 200) {
                fail(response.message());
            }
            assertEquals(200, response.code());
            String responseBody = response.body().string();
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            assertThat(json.get("results"), notNullValue());
        }
    }


}
