package smartrics.iotics.sparqlhttp;

import com.iotics.api.MetaAPIGrpc;
import com.iotics.api.SparqlResultType;
import spark.Request;
import spark.Response;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static spark.Spark.*;

public class SparqlProxyApplication {


    public static void main(String[] args) throws Exception {
        String hostDNS = findHostDNS(args);
        Callable<MetaAPIGrpc.MetaAPIStub> ioticsAPI = new IOTICSInitialiser(hostDNS);
        CallHandler callHandler = new CallHandler(ioticsAPI);

        before("/*", SparqlProxyApplication::validateRequest);
        path("/sparql", () -> {
//            get("/", SparqlProxyApplication::sparqlGet);
            post("/", SparqlProxyApplication::sparqlPost);
        });
    }

    private static Object sparqlPost(Request request, Response response) {
        return null;
    }

    public static void validateRequest(Request request, Response response) {
        String authHeader = request.headers("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            halt(401, ErrorMessage.toJson("Access Denied: no Bearer token provided"));
        }
        SimpleToken simpleToken = null;
        String token = authHeader.substring("Bearer ".length());
        try {
            simpleToken = SimpleToken.parse(token);
            String message = tokenValidMessage(simpleToken);
            if (message != null) {
                halt(401, ErrorMessage.toJson("Access Denied: " + message));
            }

        } catch (IllegalArgumentException e) {
            halt(401, ErrorMessage.toJson("Access Denied: " + e.getMessage()));
        }

        String contentType = request.contentType();
        Optional<SparqlResultType> mappedContentType = ContentTypesMap.get(contentType);
        mappedContentType.ifPresent(sparqlResultType -> {
            if(sparqlResultType.equals(SparqlResultType.UNRECOGNIZED)) {
                halt(400, ErrorMessage.toJson("Unrecognised content type: " + contentType));
            }
            request.attribute("sparqlResultType", sparqlResultType);
            // we'll omit the content type if not specified and use the default
        });


        request.attribute("agentDID", simpleToken.agentDID());
        request.attribute("agentId", simpleToken.agentId());
        request.attribute("userDID", simpleToken.userDID());
    }

    public static String tokenValidMessage(SimpleToken token) {

        try {
            if (token.isValid()) {
                return null;
            }
            return "invalid token: missing issuer, subject or already expired";
        } catch (Exception e) {
            return "invalid token: " + e.getMessage();
        }
    }

    private static String findHostDNS(String[] args) {
        String value = System.getenv("hostDNS");
        if (value != null) {
            return value;
        }

        String argKey = "hostDNS=";
        for (String arg : args) {
            if (arg.startsWith(argKey)) {
                return arg.substring(argKey.length());
            }
        }

        String sp = System.getProperty(SparqlProxyApplication.class.getPackageName() + ".hostDNS");
        if (sp != null) {
            return sp;
        }
        throw new IllegalStateException("missing hostDNS");
    }
}