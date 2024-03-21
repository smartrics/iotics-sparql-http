package smartrics.iotics.sparqlhttp;

import com.google.gson.Gson;
import com.iotics.api.SparqlResultType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import spark.HaltException;
import spark.Request;
import spark.Response;

import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class SparqlProxyApplicationTest {

    private static String messageFromJson(String json) {
        Gson g = new Gson();
        ErrorMessage em = g.fromJson(json, ErrorMessage.class);
        return em.message();
    }

    static Stream<String> authorizationHeaderProvider() {
        return Stream.of(null, "something some", "Bearer invalidToken");
    }

    @ParameterizedTest
    @MethodSource("authorizationHeaderProvider")
    void testInvalidToken(String value) {
        Request mockRequest = mock(Request.class);
        Response mockResponse = mock(Response.class);

        when(mockRequest.headers("Authorization")).thenReturn(value);

        HaltException thrown = assertThrows(HaltException.class, () -> {
            SparqlProxyApplication.validateRequest(mockRequest, mockResponse);
        });

        assertThat(thrown.statusCode(), equalTo(401));
        assertThat(messageFromJson(thrown.body()), containsString("Access Denied"));
    }

    @ParameterizedTest
    @CsvSource({
            "text/unknown, Unsupported mime type",
            "application/rdf+xml, Invalid mime type"
    })
    void testInvalidContentType(String acceptValue, String err) {
        Request mockRequest = mock(Request.class);
        Response mockResponse = mock(Response.class);

        when(mockRequest.headers("Authorization")).thenReturn("Bearer eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJodHRwczovL2RpZC5zdGcuaW90aWNzLmNvbSIsImV4cCI6MjAyNjMzNzc2NCwiaWF0IjoxNzEwOTc3NzM0LCJpc3MiOiJkaWQ6aW90aWNzOmlvdFlzcWpnUUV5emRBZm8zQU5OV1YyeHh6VURGdVR2eWRDViNhZ2VudDEiLCJzdWIiOiJkaWQ6aW90aWNzOmlvdExVbXdIREZ0cGZMRVdUZUdBUXd5cDRZNUZvU1R0NGpiZyJ9.CnZHkMoKnmr7z2xAHMiHfQiqfrzLmNpsk1Mt_CAH3h98o_mhH1HkB-8E5ieTIXP2jmzmE0U0mCcBNmWMSMaRtQ");
        when(mockRequest.headers("Accept")).thenReturn(acceptValue);

        HaltException thrown = assertThrows(HaltException.class, () -> {
            SparqlProxyApplication.validateRequest(mockRequest, mockResponse);
        });

        assertThat(thrown.statusCode(), equalTo(400));
        assertThat(messageFromJson(thrown.body()), containsString(err));

    }

    @ParameterizedTest
    @ValueSource(strings ={"default-graph-uri", "named-graph-uri"})
    void testInvalidQueryParams(String value) {
        Request mockRequest = mock(Request.class);
        Response mockResponse = mock(Response.class);

        when(mockRequest.headers("Authorization")).thenReturn("Bearer eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJodHRwczovL2RpZC5zdGcuaW90aWNzLmNvbSIsImV4cCI6MjAyNjMzNzc2NCwiaWF0IjoxNzEwOTc3NzM0LCJpc3MiOiJkaWQ6aW90aWNzOmlvdFlzcWpnUUV5emRBZm8zQU5OV1YyeHh6VURGdVR2eWRDViNhZ2VudDEiLCJzdWIiOiJkaWQ6aW90aWNzOmlvdExVbXdIREZ0cGZMRVdUZUdBUXd5cDRZNUZvU1R0NGpiZyJ9.CnZHkMoKnmr7z2xAHMiHfQiqfrzLmNpsk1Mt_CAH3h98o_mhH1HkB-8E5ieTIXP2jmzmE0U0mCcBNmWMSMaRtQ");
        when(mockRequest.queryParams(value)).thenReturn("something");

        HaltException thrown = assertThrows(HaltException.class, () -> {
            SparqlProxyApplication.validateRequest(mockRequest, mockResponse);
        });

        assertThat(thrown.statusCode(), equalTo(400));
        assertThat(messageFromJson(thrown.body()), containsString("RDF datasets not allowed"));

    }

    @Test
    void testSettingOfAttributes() {
        Request mockRequest = mock(Request.class);
        Response mockResponse = mock(Response.class);

        when(mockRequest.headers("Authorization")).thenReturn("Bearer eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJodHRwczovL2RpZC5zdGcuaW90aWNzLmNvbSIsImV4cCI6MjAyNjMzNzc2NCwiaWF0IjoxNzEwOTc3NzM0LCJpc3MiOiJkaWQ6aW90aWNzOmlvdFlzcWpnUUV5emRBZm8zQU5OV1YyeHh6VURGdVR2eWRDViNhZ2VudDEiLCJzdWIiOiJkaWQ6aW90aWNzOmlvdExVbXdIREZ0cGZMRVdUZUdBUXd5cDRZNUZvU1R0NGpiZyJ9.CnZHkMoKnmr7z2xAHMiHfQiqfrzLmNpsk1Mt_CAH3h98o_mhH1HkB-8E5ieTIXP2jmzmE0U0mCcBNmWMSMaRtQ");
        when(mockRequest.headers("Accept")).thenReturn("text/csv");
        SparqlProxyApplication.validateRequest(mockRequest, mockResponse);

        verify(mockRequest).attribute("userDID", "did:iotics:iotLUmwHDFtpfLEWTeGAQwyp4Y5FoSTt4jbg");
        verify(mockRequest).attribute("agentDID", "did:iotics:iotYsqjgQEyzdAfo3ANNWV2xxzUDFuTvydCV");
        verify(mockRequest).attribute("agentId", "agent1");
        verify(mockRequest).attribute("sparqlResultType", SparqlResultType.SPARQL_CSV);

    }

}