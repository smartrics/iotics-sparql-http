package smartrics.iotics.sparqlhttp.integration;

import io.vertx.core.Vertx;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class IntegrationTest {

    private static IdentityData identityAPI;
    private String token;

    @BeforeAll
    public static void setUpClass() {
        try {
            EnvFileLoader.loadEnvFile(".env");
        } catch (IOException e) {
            throw new RuntimeException("unable to find the .env file", e);
        }
        System.out.println(IdentityData.make().token(Duration.ofDays(365)));
        identityAPI = IdentityData.make();
    }

    @BeforeEach
    public void setup(Vertx vertx, VertxTestContext testContext) {
        this.token = identityAPI.token(Duration.ofSeconds(60));
        vertx.deployVerticle(new SparqlEndpoint(), testContext.succeedingThenComplete());
    }

    @Test
    public void testSimpleLocalSPARQL(Vertx vertx, VertxTestContext testContext) throws IOException {
        String sparqlQuery = """
                SELECT ?subject ?predicate ?object
                WHERE {?subject ?predicate ?object}
                LIMIT 10
                """;
        String encodedQuery = URLEncoder.encode(sparqlQuery, StandardCharsets.UTF_8);

        vertx.createHttpClient()
                .request(HttpMethod.GET, 8080, "localhost", "/sparql/local?query=" + encodedQuery)
                .compose(request -> {
                    request.putHeader("Authorization", "Bearer " + token);
                    request.putHeader("Accept", "application/sparql-results+json");
                    request.putHeader("X-IOTICS-HOST", "demo.iotics.space");
                    return request.send();
                })
                .onSuccess(response -> testContext.verify(() -> {
                    System.out.println(response.statusMessage());
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
                })).onFailure(testContext::failNow);
        testContext.succeedingThenComplete();
    }

}
