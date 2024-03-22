package smartrics.iotics.sparqlhttp;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

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
        this.token = identityAPI.token(Duration.ofSeconds(5));
    }

    @Test
    public void testSimpleLocalSPARQL() throws UnsupportedEncodingException, InterruptedException {
        String sparqlQuery = "SELECT ?subject ?predicate ?object WHERE {?subject ?predicate ?object} LIMIT 10";

        String encodedQuery = URLEncoder.encode(sparqlQuery, StandardCharsets.UTF_8);

        Response response = RestAssured
                .given()
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "text/turtle")
                    .queryParam("query", encodedQuery)
                .when()
                    .get("/local/")
                .then()
                    .extract()
                    .response();

        String responseBody = response.getBody().asString();
        System.out.println("Response Body: " + responseBody);

        // Get the status of the response
        int statusCode = response.getStatusCode();
        System.out.println("Status Code: " + statusCode);

    }

}
