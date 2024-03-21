package smartrics.iotics.sparqlhttp;

import com.iotics.api.MetaAPIGrpc;
import com.iotics.api.SparqlResultType;
import spark.Filter;
import spark.Request;
import spark.Response;
import spark.RouteGroup;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static smartrics.iotics.sparqlhttp.ContentTypesMap.isSPARQLResultType;
import static spark.Spark.*;

public class SparqlProxyApplication {


    public static void main(String[] args) throws Exception {
        String hostDNS = findHostDNS(args);
        Callable<MetaAPIGrpc.MetaAPIStub> ioticsAPI = new IOTICSInitialiser(hostDNS);
        CallHandler callHandler = new CallHandler(ioticsAPI);

        before("/*", SparqlProxyApplication::validateRequest);
        path("/sparql", () -> {
            get("/", SparqlProxyApplication::sparqlGet);
//            post("/", SparqlProxyApplication::sparqlPost);
        });
    }

    private static String sparqlGet(Request request, Response response) {
        String query = request.queryParams("query");
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

        // TODO: support multiple Accept with quality flag
        String accepted = request.headers("Accept");
        SparqlResultType mappedAccepted = ContentTypesMap.get(accepted, SparqlResultType.SPARQL_JSON);

        if(mappedAccepted.equals(SparqlResultType.UNRECOGNIZED)) {
            halt(400, ErrorMessage.toJson("Unsupported mime type: " + accepted));
        }

        if(!isSPARQLResultType(mappedAccepted)) {
            halt(400, ErrorMessage.toJson("Invalid mime type: " + accepted));
        }

        // SPARQL-1.1 Par 2.1.4
        String def = request.queryParams("default-graph-uri");
        String query = request.queryParams("named-graph-uri");
        if(def!=null  || query !=null) {
            halt(400, ErrorMessage.toJson("RDF datasets not allowed"));
        }

        request.attribute("sparqlResultType", mappedAccepted);
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