package smartrics.iotics.sparqlhttp;

import com.iotics.api.SparqlResultType;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import smartrics.iotics.sparqlhttp.integration.EnvFileLoader;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(VertxExtension.class)
class SparqlEndpointTest {
    private final String bearer = "user:seed";
    private final String token = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJodHRwczovL2RpZC5zdGcuaW90aWNzLmNvbSIsImV4cCI6MjAyNjMzNzc2NCwiaWF0IjoxNzEwOTc3NzM0LCJpc3MiOiJkaWQ6aW90aWNzOmlvdFlzcWpnUUV5emRBZm8zQU5OV1YyeHh6VURGdVR2eWRDViNhZ2VudDEiLCJzdWIiOiJkaWQ6aW90aWNzOmlvdExVbXdIREZ0cGZMRVdUZUdBUXd5cDRZNUZvU1R0NGpiZyJ9.CnZHkMoKnmr7z2xAHMiHfQiqfrzLmNpsk1Mt_CAH3h98o_mhH1HkB-8E5ieTIXP2jmzmE0U0mCcBNmWMSMaRtQ";

    static Stream<String> authorizationHeaderProvider() {
        return Stream.of(null, "something some", "Bearer invalidToken");
    }

    static Stream<String> acceptedTypes() {
        return Stream.of(null, "*/*");
    }

    @BeforeAll
    public static void setUpClass() {
        try {
            EnvFileLoader.loadEnvFile(".env");
        } catch (IOException e) {
            throw new RuntimeException("unable to find the .env file", e);
        }
    }


    @ParameterizedTest
    @MethodSource("authorizationHeaderProvider")
    void testInvalidToken(String value, VertxTestContext testContext) {
        // Mock the RoutingContext
        RoutingContext routingContext = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
        HttpServerResponse response = mock(HttpServerResponse.class);
        when(routingContext.response()).thenReturn(response);
        when(routingContext.request().getHeader("Authorization")).thenReturn(value);

        // Mock other necessary parts of routingContext as needed
        when(response.setStatusCode(anyInt())).thenReturn(response);
        when(response.setStatusMessage(anyString())).thenReturn(response);

        // Instantiate your verticle or the part of it responsible for handling the request
        SparqlEndpoint endpoint = new SparqlEndpoint(Map.of(SparqlEndpoint.KEY_ENABLE_ANON, "false"));

        // Since your logic might be inside handlers, you need to simulate calling the specific handler
        // For example, let's assume you're testing the handlePost method for a global scope
        SparqlEndpoint.ValidationException thrown = assertThrows(SparqlEndpoint.ValidationException.class, () -> {
            endpoint.validateRequest(routingContext);
        });

        // Verify that the response is set to 401
        assertThat("should have code 401", thrown.getCode(), equalTo(401));
        assertThat("should say Access Denied", thrown.getMessage(), containsString("Access Denied"));

        testContext.completeNow();
    }

    @Test
    void testMakeValidTokenIfAuthNullAndAnonymousEnabled(VertxTestContext testContext) {
        RoutingContext routingContext = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
        HttpServerResponse response = mock(HttpServerResponse.class);
        when(routingContext.response()).thenReturn(response);
        when(routingContext.request().getHeader("Authorization")).thenReturn(null);
        when(routingContext.request().getHeader("Accept")).thenReturn("*/*");

        SparqlEndpoint endpoint = new SparqlEndpoint(Map.of(SparqlEndpoint.KEY_ENABLE_ANON, "true"));

        endpoint.validateRequest(routingContext);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        verify(routingContext).put(eq("token"), captor.capture());
        String tokenString = captor.getValue();
        SimpleToken st = SimpleToken.parse(tokenString);
        verify(routingContext).put("userDID", st.userDID());
        verify(routingContext).put("agentDID", st.agentDID());
        verify(routingContext).put("agentId", st.agentId());
        verify(routingContext).put("acceptedResponseType", SparqlResultType.SPARQL_JSON);

        testContext.completeNow();
    }

    @ParameterizedTest
    @CsvSource({
            "text/unknown, Unsupported response mime type"
    })
    void testInvalidAcceptedResponseTypes(String acceptValue, String err, VertxTestContext testContext) {
        RoutingContext routingContext = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
        HttpServerResponse response = mock(HttpServerResponse.class);
        when(routingContext.response()).thenReturn(response);
        when(routingContext.request().getHeader("Authorization")).thenReturn("Bearer " + bearer);
        when(routingContext.request().getHeader("Accept")).thenReturn(acceptValue);


        when(response.setStatusCode(anyInt())).thenReturn(response);
        when(response.setStatusMessage(anyString())).thenReturn(response);

        SparqlEndpoint endpoint = new SparqlEndpoint();

        SparqlEndpoint.ValidationException thrown = assertThrows(SparqlEndpoint.ValidationException.class, () -> {
            endpoint.validateRequest(routingContext);
        });

        assertThat("should have code 400", thrown.getCode(), equalTo(400));
        assertThat("should say Unsupported", thrown.getMessage(), containsString(err));

        testContext.completeNow();

    }

    @ParameterizedTest
    @MethodSource("acceptedTypes")
    public void testDefaultAcceptedResponseTypes(String value, VertxTestContext testContext) {
        RoutingContext routingContext = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
        HttpServerResponse response = mock(HttpServerResponse.class);
        when(routingContext.response()).thenReturn(response);
        when(routingContext.request().getHeader("Authorization")).thenReturn("Bearer " + bearer);
        when(routingContext.request().getHeader("Accept")).thenReturn(value);

        SparqlEndpoint endpoint = new SparqlEndpoint();
        endpoint.validateRequest(routingContext);

        verify(routingContext).put("acceptedResponseType", SparqlResultType.SPARQL_JSON);

        testContext.completeNow();
    }

    @ParameterizedTest
    @ValueSource(strings = {"default-graph-uri", "named-graph-uri"})
    public void testInvalidQueryParamsRDFDataset(String value, VertxTestContext testContext) {
        RoutingContext routingContext = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
        HttpServerResponse response = mock(HttpServerResponse.class);
        when(routingContext.response()).thenReturn(response);
        when(routingContext.request().getHeader("Authorization")).thenReturn("Bearer " + bearer);
        when(routingContext.queryParam(value)).thenReturn(List.of("someValue"));

        when(response.setStatusCode(anyInt())).thenReturn(response);
        when(response.setStatusMessage(anyString())).thenReturn(response);

        SparqlEndpoint endpoint = new SparqlEndpoint();

        SparqlEndpoint.ValidationException thrown = assertThrows(SparqlEndpoint.ValidationException.class, () -> {
            endpoint.validateRequest(routingContext);
        });

        assertThat("should have code 400", thrown.getCode(), equalTo(400));
        assertThat("should say Unsupported", thrown.getMessage(), containsString("RDF datasets not allowed"));

        testContext.completeNow();

    }

    @Test
    void testMissingQueryInGETRequest(VertxTestContext testContext) {
        RoutingContext routingContext = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
        HttpServerResponse response = mock(HttpServerResponse.class);
        when(routingContext.response()).thenReturn(response);
        when(routingContext.request().getHeader("Authorization")).thenReturn("Bearer " + bearer);
        HttpMethod method = mock(HttpMethod.class);
        when(routingContext.request().method()).thenReturn(method);
        when(method.name()).thenReturn("GET");

        SparqlEndpoint endpoint = new SparqlEndpoint();
        SparqlEndpoint.ValidationException thrown = assertThrows(SparqlEndpoint.ValidationException.class, () -> {
            endpoint.validateRequest(routingContext);
        });

        assertThat("should have code 400", thrown.getCode(), equalTo(400));
        assertThat("should say Unsupported", thrown.getMessage(), containsString("missing query"));

        testContext.completeNow();
    }

    @Test
    void testSettingOfAttributesWithBearer(VertxTestContext testContext) {
        RoutingContext routingContext = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
        HttpServerResponse response = mock(HttpServerResponse.class);
        when(routingContext.response()).thenReturn(response);
        when(routingContext.request().getHeader("Authorization")).thenReturn("Bearer " + bearer);
        when(routingContext.request().getHeader("Accept")).thenReturn("*/*");

        SparqlEndpoint endpoint = new SparqlEndpoint();
        endpoint.validateRequest(routingContext);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        verify(routingContext).put(eq("token"), captor.capture());
        String tokenString = captor.getValue();
        SimpleToken st = SimpleToken.parse(tokenString);
        verify(routingContext).put("userDID", st.userDID());
        verify(routingContext).put("agentDID", st.agentDID());
        verify(routingContext).put("agentId", st.agentId());
        verify(routingContext).put("acceptedResponseType", SparqlResultType.SPARQL_JSON);

        testContext.completeNow();
    }


    @Test
    void testSettingOfAttributesWithToken(VertxTestContext testContext) {
        RoutingContext routingContext = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
        HttpServerResponse response = mock(HttpServerResponse.class);
        when(routingContext.response()).thenReturn(response);
        when(routingContext.request().getHeader("Authorization")).thenReturn("Bearer " + token);
        when(routingContext.request().getHeader("Accept")).thenReturn("*/*");

        SparqlEndpoint endpoint = new SparqlEndpoint();
        endpoint.validateRequest(routingContext);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        verify(routingContext).put(eq("token"), captor.capture());
        verify(routingContext).put("token", token);

        testContext.completeNow();
    }

}