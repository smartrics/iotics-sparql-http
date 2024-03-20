package smartrics.iotics.sparqlhttp;

import java.time.Duration;

public class IntegrationTest {

    public static void main(String[] args) {
        IdentityData identityAPI = IdentityData.make();
        String token = identityAPI.token(Duration.ofDays(365 * 10));
        System.out.println(token);
        SimpleToken parsed = SimpleToken.parse(token);
        System.out.println(parsed);
        SparqlProxyApplication.tokenValidMessage(parsed);
    }

}
