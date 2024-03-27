package smartrics.iotics.sparqlhttp;

import com.google.gson.Gson;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.contains;

public class IntegrationTest {
    private static IdentityData identityAPI;
    private String token;

    @BeforeAll
    public static void setUpClass() {
        identityAPI = IdentityData.make();
        SparqlProxyApplication.initRoutes(System.getenv("HOST_DNS"));
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 4567;
        RestAssured.basePath = "/sparql";
    }

    @AfterAll
    public static void tearDownClass() {
        // Stop Spark after tests
        spark.Spark.stop();
    }

    @BeforeEach
    public void setup() {
        this.token = identityAPI.token(Duration.ofSeconds(60));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testSimpleLocalSPARQL() {
        String sparqlQuery = """
            SELECT ?subject ?predicate ?object
            WHERE {?subject ?predicate ?object}
            LIMIT 10
            """;
        String encodedQuery = URLEncoder.encode(sparqlQuery, StandardCharsets.UTF_8);

        Response response = RestAssured
                .given()
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/sparql-results+json")
                    .queryParam("query", encodedQuery)
                .when()
                    .get("/local/")
                .then()
                    .extract()
                    .response();

        String responseBody = response.getBody().asString();
        Gson g = new Gson();
        Map map = g.fromJson(responseBody, Map.class);
        Map head = (Map)map.get("head");
        List<String> vars = (List<String>)head.get("vars");
        assertThat(vars, containsInRelativeOrder("subject", "predicate", "object"));

        // Get the status of the response
        int statusCode = response.getStatusCode();
        assertEquals(200,statusCode, "mismatching result, should have been OK");

    }

}
