package smartrics.iotics.sparqlhttp;

import com.iotics.api.MetaAPIGrpc;
import com.iotics.api.Scope;
import com.iotics.api.SparqlResultType;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Launcher;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static smartrics.iotics.sparqlhttp.ContentTypesMap.mimeFor;

public class SparqlEndpoint extends AbstractVerticle {

    private static final Map<String, IOTICSConnection> connections = new ConcurrentHashMap<>(16);

    private static final String KEY_HOST_DNS = "HOST_DNS";
    private static final String KEY_TOKEN = "TOKEN";
    private static final String KEY_PORT = "PORT";
    private static final String DEFAULT_PORT = "8080";

    private static final String HOST_HEADER = "X-IOTICS-HOST";


    private static final Map<String, String> ENV = new ConcurrentHashMap<>();


    public static void main(String[] args) {
        setupEnv(args);
        Launcher.executeCommand("run", SparqlEndpoint.class.getName());
    }

    private static void setupEnv(String[] args) {
        ENV.put(KEY_HOST_DNS, load(args, KEY_HOST_DNS));
        ENV.put(KEY_TOKEN, load(args, KEY_TOKEN));
        ENV.put(KEY_PORT, load(args, KEY_PORT));
        ENV.putIfAbsent(KEY_PORT, DEFAULT_PORT);
    }

    public static String load(String[] args, String key) {
        String value = System.getenv(key);
        if (value != null) {
            return value;
        }

        String argKey = key + "=";
        for (String arg : args) {
            if (arg.startsWith(argKey)) {
                return arg.substring(argKey.length());
            }
        }
        return System.getProperty(key);
    }

    private static String findHost(RoutingContext ctx) {
        return Optional.ofNullable(ctx.request().getHeader(HOST_HEADER)).orElse(ENV.get(KEY_HOST_DNS));
    }

    public void start() {
        String port = Optional.ofNullable(System.getenv("PORT")).orElse("8080");

        Router router = Router.router(vertx);

        router.route().handler(this::validateRequest);
        router.get("/sparql/local").handler(ctx -> this.handleGet(ctx, Scope.LOCAL));
        router.get("/sparql").handler(ctx -> this.handleGet(ctx, Scope.GLOBAL));
        router.post("/sparql/local").handler(ctx -> this.handlePost(ctx, Scope.LOCAL));
        router.post("/sparql").handler(ctx -> this.handlePost(ctx, Scope.GLOBAL));

        vertx.createHttpServer().requestHandler(router).listen(Integer.parseInt(port));
    }

    private void handleGet(RoutingContext ctx, Scope scope) {
        try {
            String encodedQuery = ctx.request().getParam("query");
            String query = URLDecoder.decode(encodedQuery, StandardCharsets.UTF_8);
            String token = ctx.get("token");
            handle(scope, ctx, token, query);
        } catch (ValidationException e) {
            sendError(e.getCode(), e.getMessage(), ctx.response());
        }
    }

    private void handlePost(RoutingContext ctx, Scope scope) {
        try {
            String token = ctx.get("token");
            String query = ctx.body().asString();
            handle(scope, ctx, token, query);
        } catch (ValidationException e) {
            sendError(e.getCode(), e.getMessage(), ctx.response());
        }
    }

    private void handle(Scope scope, RoutingContext ctx, String token, String query) {
        try {
            String host = findHost(ctx);
            IOTICSConnection connection = connections.computeIfAbsent(host, IOTICSConnection::new);
            MetaAPIGrpc.MetaAPIStub api = connection.newMetaAPIStub(token);
            SparqlResultType type = ctx.get("acceptedResponseType");
            String mime = mimeFor(type);
            if (mime != null) {
                ctx.response().headers().set("Content-Type", mime);
            }
            ctx.response().headers().add("Access-Control-Allow-Origin", "*");
            StreamObserverToStringAdapter outputStream = new StreamObserverToStringAdapter();

            String agentDID = ctx.get("agentDID");
            QueryRunner runner = SparqlRunner.SparqlRunnerBuilder.newBuilder()
                    .withScope(scope)
                    .withSparqlResultType(type)
                    .withMetaAPIStub(api)
                    .withOutputStream(outputStream)
                    .withAgentId(agentDID)
                    .build();
            runner.run(query);

            String string = outputStream.getString();
            ctx.response().setStatusCode(200);
            ctx.response().send(string);
        } catch (Exception e) {
            String message = e.getMessage();
            if (e.getCause() != null) {
                message = message + ": " + e.getCause().getMessage();
            }
            sendError(500, ErrorMessage.toJson(message), ctx.response());
        }
    }

    private void sendError(int statusCode, String message, HttpServerResponse response) {
        response.setStatusCode(statusCode).setStatusMessage(message).end();
    }

    void validateRequest(RoutingContext ctx) throws ValidationException {
        HttpServerRequest request = ctx.request();
        String host = findHost(ctx);
        if (host == null) {
            throw new ValidationException(400, ErrorMessage.toJson("Invalid request: missing host dns from header " + HOST_HEADER));
        }
        String token;
        String authHeader = request.getHeader("Authorization");
        token = System.getenv("TOKEN");
        if (token == null) {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new ValidationException(401, ErrorMessage.toJson("Access Denied: no Bearer token provided"));
            }
            token = authHeader.substring("Bearer ".length());
        }
        SimpleToken simpleToken;
        try {
            simpleToken = SimpleToken.parse(token);
            String message = tokenValidMessage(simpleToken);
            if (message != null) {
                throw new ValidationException(401, ErrorMessage.toJson("Access Denied: " + message));
            }

        } catch (IllegalArgumentException e) {
            throw new ValidationException(401, ErrorMessage.toJson("Access Denied: " + e.getMessage()));
        }

        String accepted = request.getHeader("Accept");
        if (accepted != null) {
            // TODO: better support multiple Accept with quality flag
            accepted = accepted.split(",")[0].trim();
            accepted = accepted.split(";")[0];
        }

        SparqlResultType mappedAccepted = SparqlResultType.SPARQL_JSON;
        if (accepted != null && !accepted.equals("*/*")) {
            mappedAccepted = ContentTypesMap.get(accepted, SparqlResultType.UNRECOGNIZED);
        }

        if (mappedAccepted.equals(SparqlResultType.UNRECOGNIZED)) {
            throw new ValidationException(400, ErrorMessage.toJson("Unsupported response mime type: " + accepted));
        }

        // SPARQL-1.1 Par 2.1.4
        List<String> def = ctx.queryParam("default-graph-uri");
        List<String> named = ctx.queryParam("named-graph-uri");
        if (def.size() > 0 || named.size() > 0) {
            throw new ValidationException(400, ErrorMessage.toJson("RDF datasets not allowed"));
        }

        String query = request.getParam("query");
        if (query == null && "get".equalsIgnoreCase(request.method().name())) {
            throw new ValidationException(400, ErrorMessage.toJson("missing query"));
        }

        if ("post".equalsIgnoreCase(request.method().name())) {
            // Clients must set the content type header of the HTTP request to application/sparql-query
            // spec par 2.1.3
            String ct = request.getHeader("Content-Type");
            if (!"application/sparql-query".equals(ct)) {
                throw new ValidationException(400, ErrorMessage.toJson("missing or invalid content type"));
            }

        }

        ctx.put("token", token);
        ctx.put("acceptedResponseType", mappedAccepted);
        ctx.put("agentDID", simpleToken.agentDID());
        ctx.put("agentId", simpleToken.agentId());
        ctx.put("userDID", simpleToken.userDID());

        ctx.next();
    }

    public String tokenValidMessage(SimpleToken token) {

        try {
            if (token.isValid()) {
                return null;
            }
            return "invalid token: missing issuer, subject or already expired";
        } catch (Exception e) {
            return "invalid token: " + e.getMessage();
        }
    }

    public static class ValidationException extends RuntimeException {
        private final int code;

        public ValidationException(int code, String message) {
            super(message);
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

}
