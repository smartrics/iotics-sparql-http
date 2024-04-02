package smartrics.iotics.sparqlhttp.integration;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import smartrics.iotics.sparqlhttp.SparqlEndpoint;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class SparqlEndpointIT {

    private static IdentityData identityAPI;
    private String token;
    private String hostDNS;
    private int hostPort;

    @BeforeAll
    public static void setUpClass() {
        try {
            EnvFileLoader.loadEnvFile(".env");
        } catch (IOException e) {
            throw new RuntimeException("unable to find the .env file", e);
        }
        identityAPI = IdentityData.make();
    }

    private static void verifyValidResponse(VertxTestContext testContext, HttpClientResponse response) {
        assertEquals(200, response.statusCode());
        response.bodyHandler(body -> {
            JsonObject jsonObject = body.toJsonObject(); // Convert the body to a String
            testContext.verify(() -> {
                JsonArray vars = jsonObject.getJsonObject("head").getJsonArray("vars");
                assertTrue(vars.contains("subject"), "should contain subject");
                assertTrue(vars.contains("predicate"), "should contain predicate");
                assertTrue(vars.contains("object"), "should contain object");
                testContext.completeNow(); // Mark the test as passed
            });
        });
    }

    @BeforeEach
    public void setup(Vertx vertx, VertxTestContext testContext) {
        this.token = identityAPI.token(Duration.ofSeconds(60));
        this.hostDNS = System.getProperty("HOST_DNS");
        this.hostPort = Integer.parseInt(System.getProperty("PORT"));
        vertx.deployVerticle(new SparqlEndpoint(), testContext.succeedingThenComplete());
    }

    @Test
    public void testInvalidSPARQL(Vertx vertx, VertxTestContext testContext) {
        String sparqlQuery = """
                SELECT  }
                LIMIT 10
                """;
        vertx.createHttpClient()
                .request(HttpMethod.POST, this.hostPort, "localhost", "/sparql/local")
                .compose(request -> {
                    addPostHeaders(request);
                    return request.send(sparqlQuery);
                }).compose(response -> {
                    // Check for HTTP 500 status code
                    assertThat("should have code 500", response.statusCode(), equalTo(500));
                    assertThat("should contain invalid query", response.statusMessage(), containsString("error executing the query: query invalid"));
                    testContext.completeNow(); // Test passes
                    return Future.succeededFuture();
                }).onFailure(throwable -> {
                    // Handle request failure (e.g., connection error)
                    System.out.println(throwable.getMessage());
                    testContext.failNow(throwable);
                });

    }

    @Test
    public void testSimpleLocalGetSPARQL(Vertx vertx, VertxTestContext testContext) {
        String sparqlQuery = """
                SELECT ?subject ?predicate ?object
                WHERE {?subject ?predicate ?object}
                LIMIT 10
                """;
        String encodedQuery = URLEncoder.encode(sparqlQuery, StandardCharsets.UTF_8);

        vertx.createHttpClient()
                .request(HttpMethod.GET, this.hostPort, "localhost", "/sparql/local?query=" + encodedQuery)
                .compose(request -> {
                    addCommonHeaders(request);
                    return request.send();
                })
                .onSuccess(response -> testContext.verify(() ->
                        verifyValidResponse(testContext, response))).onFailure(testContext::failNow);
        testContext.succeedingThenComplete();
    }

    @Test
    public void testSimpleLocalPostSPARQL(Vertx vertx, VertxTestContext testContext) {
        String sparqlQuery = """
                SELECT ?subject ?predicate ?object
                WHERE {?subject ?predicate ?object}
                LIMIT 10
                """;
        vertx.createHttpClient()
                .request(HttpMethod.POST, this.hostPort, "localhost", "/sparql/local")
                .compose(request -> {
                    addPostHeaders(request);
                    return request.send(sparqlQuery);
                })
                .onSuccess(response -> testContext.verify(() ->
                        verifyValidResponse(testContext, response))).onFailure(testContext::failNow);
        testContext.succeedingThenComplete();
    }

    private void addCommonHeaders(HttpClientRequest request) {
        request.putHeader("Authorization", "Bearer " + token);
        request.putHeader("Accept", "application/sparql-results+json");
        request.putHeader("X-IOTICS-HOST", this.hostDNS);
    }

    private void addPostHeaders(HttpClientRequest request) {
        addCommonHeaders(request);
        request.putHeader("Content-Type", "application/sparql-query");
    }

}
