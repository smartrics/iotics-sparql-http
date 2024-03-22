package smartrics.iotics.sparqlhttp;

import com.iotics.api.MetaAPIGrpc;
import com.iotics.api.Scope;
import com.iotics.api.SparqlResultType;
import org.jetbrains.annotations.NotNull;
import spark.Request;
import spark.Response;
import spark.Route;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import static smartrics.iotics.sparqlhttp.ContentTypesMap.isRDFResultType;
import static smartrics.iotics.sparqlhttp.ContentTypesMap.mimeFor;
import static spark.Spark.*;

public class SparqlProxyApplication {

    public static void main(String[] args) throws Exception {
        String hostDNS = findHostDNS(args);
        initRoutes(hostDNS);
    }

    public static void initRoutes(String hostDNS) {
        String port = System.getenv("PORT");
        if (port != null) {
            port(Integer.parseInt(port));
        }
        IOTICSConnection ioticsConnection = new IOTICSConnection(hostDNS);

        before("/*", SparqlProxyApplication::validateRequest);
        path("/sparql/local", () -> {
            get("/", getHandler(Scope.LOCAL, ioticsConnection));
//            post("/", SparqlProxyApplication::sparqlLocalPost);
        });
        path("/sparql", () -> {
            get("/", getHandler(Scope.GLOBAL, ioticsConnection));
//            post("/", SparqlProxyApplication::sparqlPost);
        });
    }

    @NotNull
    private static Route getHandler(Scope scope, IOTICSConnection connection) {
        return (request, response) -> {
            try {
                String query = request.queryParams("query");
                String token = request.attribute("token");
                MetaAPIGrpc.MetaAPIStub api = connection.newMetaAPIStub(token);
                return runQuery(scope, request, response, api, query);
            } catch (Exception e) {
                String message = e.getMessage();
                if(e.getCause() != null) {
                    message = message + ": " + e.getCause().getMessage();
                }
                halt(500, ErrorMessage.toJson(message));

            }
            return null;
        };
    }

    private static String runQuery(Scope scope, Request request, Response response, MetaAPIGrpc.MetaAPIStub api, String query) {
        SparqlResultType type = request.attribute("acceptedResponseType");
        String mime = mimeFor(type);
        if (mime != null) {
            response.type(mime);
        }

        SyncStreamObserverToStringAdapter outputStream = new SyncStreamObserverToStringAdapter();
        String agentDID = request.attribute("agentDID");
        QueryRunner runner = SparqlRunner.SparqlRunnerBuilder.newBuilder()
                .withScope(scope)
                .withSparqlResultType(type)
                .withMetaAPIStub(api)
                .withOutputStream(outputStream)
                .withAgentId(agentDID)
                .build();
        runner.run(query);
        return outputStream.getString();
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
        SparqlResultType mappedAccepted = SparqlResultType.RDF_TURTLE;
        if (accepted != null && !accepted.equals("*/*")) {
            mappedAccepted = ContentTypesMap.get(accepted, SparqlResultType.UNRECOGNIZED);
        }

        if (mappedAccepted.equals(SparqlResultType.UNRECOGNIZED)) {
            halt(400, ErrorMessage.toJson("Unsupported response mime type: " + accepted));
        }

        if (!isRDFResultType(mappedAccepted)) {
            halt(400, ErrorMessage.toJson("Invalid response mime type: " + accepted));
        }

        // SPARQL-1.1 Par 2.1.4
        String def = request.queryParams("default-graph-uri");
        String named = request.queryParams("named-graph-uri");
        if (def != null || named != null) {
            halt(400, ErrorMessage.toJson("RDF datasets not allowed"));
        }

        String query = request.queryParams("query");
        if (query == null && "get".equalsIgnoreCase(request.requestMethod())) {
            halt(400, ErrorMessage.toJson("missing query"));
        }

        request.attribute("token", token);
        request.attribute("acceptedResponseType", mappedAccepted);
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