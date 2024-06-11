package smartrics.iotics.sparqlhttp;

import com.iotics.api.MetaAPIGrpc;
import com.iotics.api.Scope;
import com.iotics.api.SparqlResultType;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Launcher;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.jetbrains.annotations.NotNull;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static smartrics.iotics.sparqlhttp.ContentTypesMap.mimeFor;

public class SparqlEndpoint extends AbstractVerticle {

    public static final String KEY_HOST_DNS = "HOST_DNS";
    public static final String KEY_AGENT_SEED = "AGENT_SEED";
    public static final String KEY_AGENT_KEY = "AGENT_KEY";
    public static final String KEY_USER_SEED = "USER_SEED";
    public static final String KEY_USER_KEY = "USER_KEY";
    public static final String KEY_ENABLE_ANON = "ENABLE_ANON";
    public static final String KEY_PORT = "PORT";
    public static final String KEY_TOKEN_DURATION = "TOKEN_DURATION";
    private static final String DEFAULT_PORT = "8080";
    private static final String DEFAULT_TOKEN_DURATION = "PT60S";
    private static final String DEFAULT_ENABLE_ANON = "false";

    private static final Map<String, String> ENV = new ConcurrentHashMap<>();
    private final Identities identities;
    private final Duration defaultTokenDuration;
    private final Boolean enableAnonymous;

    public SparqlEndpoint() {
        this(HashMap.newHashMap(0));
    }

    public SparqlEndpoint(Map<String, String> overrides) {
        setupEnv();
        ENV.putAll(overrides);
        identities = new Identities(ENV.get(KEY_HOST_DNS),
                ENV.get(KEY_USER_KEY), ENV.get(KEY_USER_SEED),
                ENV.get(KEY_AGENT_KEY), ENV.get(KEY_AGENT_SEED));
        defaultTokenDuration = Duration.parse(Optional.ofNullable(ENV.get(KEY_TOKEN_DURATION)).orElse(DEFAULT_TOKEN_DURATION));
        enableAnonymous = Boolean.valueOf(Optional.ofNullable(ENV.get(KEY_ENABLE_ANON)).orElse(DEFAULT_ENABLE_ANON));
    }


    public static void main(String[] args) {
        Launcher.executeCommand("run", SparqlEndpoint.class.getName());
    }

    private static void setupEnv() {
        ENV.put(KEY_HOST_DNS, load(KEY_HOST_DNS));
        ENV.put(KEY_AGENT_SEED, load(KEY_AGENT_SEED));
        ENV.put(KEY_AGENT_KEY, load(KEY_AGENT_KEY));
        ENV.put(KEY_USER_SEED, load(KEY_USER_SEED));
        ENV.put(KEY_USER_KEY, load(KEY_USER_KEY));
        ENV.put(KEY_PORT, load(KEY_PORT));
        ENV.put(KEY_TOKEN_DURATION, load(KEY_TOKEN_DURATION));
        ENV.putIfAbsent(KEY_PORT, DEFAULT_PORT);

        System.out.println("Configuration: ");
        System.out.println(" host: " + Optional.ofNullable(ENV.get(KEY_HOST_DNS)).orElse("<not configured>"));
        String value = ENV.get(KEY_AGENT_SEED);
        if(value != null) {
            value = "<secret configured>";
        }
        System.out.println(" agent seed: " + Optional.ofNullable(value).orElse("<not configured>"));
        System.out.println(" agent key: " + Optional.ofNullable(ENV.get(KEY_AGENT_KEY)).orElse("<not configured>"));

        value = ENV.get(KEY_USER_SEED);
        if(value != null) {
            value = "<secret configured>";
        }
        System.out.println(" user seed: " + Optional.ofNullable(value).orElse("<not configured>"));
        System.out.println(" user key: " + Optional.ofNullable(ENV.get(KEY_USER_KEY)).orElse("<not configured>"));
        System.out.println(" port: " + Optional.ofNullable(ENV.get(KEY_PORT)).orElse("<not configured>"));
    }

    public static String load(String key) {
        String value = System.getenv(key);
        if (value != null) {
            return value;
        }
        return System.getProperty(key);
    }

    public void start() {
        String port = Optional.ofNullable(System.getenv("PORT")).orElse("8080");

        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create()).handler(this::validateRequest);
        router.get("/health").handler(this::handleHealth);
        router.get("/sparql/local").handler(ctx -> this.handleGet(ctx, Scope.LOCAL));
        router.get("/sparql").handler(ctx -> this.handleGet(ctx, Scope.GLOBAL));
        router.post("/sparql/local").handler(ctx -> this.handlePost(ctx, Scope.LOCAL));
        router.post("/sparql").handler(ctx -> this.handlePost(ctx, Scope.GLOBAL));

        vertx.createHttpServer().requestHandler(router).listen(Integer.parseInt(port));
    }

    private void handleHealth(RoutingContext ctx) {
        ctx.response().setStatusCode(200);
        ctx.response().send("{ 'status': 'OK' }");
        ctx.response().end();
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
            String query;
            String token = ctx.get("token");
            String ct = ctx.request().getHeader("Content-Type");
            if("application/x-www-form-urlencoded".equals(ct)) {
                MultiMap formAttributes = ctx.request().formAttributes();
                // Example: Get a form attribute named "exampleField"
                query = formAttributes.get("query");
            } else {
                query = ctx.body().asString();
            }
            if(query != null) {
                handle(scope, ctx, token, query);
            } else {
                ctx.response().setStatusCode(200);
                ctx.response().send();
            }
        } catch (ValidationException e) {
            sendError(e.getCode(), e.getMessage(), ctx.response());
        }
    }

    private void handle(Scope scope, RoutingContext ctx, String token, String query) {
        try {
            String host = ENV.get(KEY_HOST_DNS);
            IOTICSConnection connection = new IOTICSConnection(host);
            MetaAPIGrpc.MetaAPIStub api = connection.newMetaAPIStub(token);
            SparqlResultType type = ctx.get("acceptedResponseType");
            String mime = mimeFor(type);
            if (mime != null) {
                ctx.response().headers().set("Content-Type", mime);
            }
            ctx.response().headers().add("Access-Control-Allow-Origin", "*");
            StreamObserverToStringAdapter outputStream = new StreamObserverToStringAdapter();

            QueryRunner runner = SparqlRunner.SparqlRunnerBuilder.newBuilder()
                    .withScope(scope)
                    .withSparqlResultType(type)
                    .withMetaAPIStub(api)
                    .withOutputStream(outputStream)
                    .withAgentIdentity(identities.agentIdentity())
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
        validateGraphName(ctx);
        getValidQuery(request);
        TokenPair tokenPair = makeOrGetValidToken(request);
        SparqlResultType mappedAccepted = getValidAcceptedResultType(request);

        ctx.put("token", tokenPair.tokenString);
        ctx.put("acceptedResponseType", mappedAccepted);
        ctx.put("agentDID", tokenPair.simpleToken.agentDID());
        ctx.put("agentId", tokenPair.simpleToken.agentId());
        ctx.put("userDID", tokenPair.simpleToken.userDID());

        ctx.next();
    }

    private static void getValidQuery(HttpServerRequest request) {
        String query = request.getParam("query");
        if (query == null && "get".equalsIgnoreCase(request.method().name())) {
            throw new ValidationException(400, ErrorMessage.toJson("missing query"));
        }

        if ("post".equalsIgnoreCase(request.method().name())) {
            // Clients must set the content type header of the HTTP request to application/sparql-query
            // spec par 2.1.3
            String ct = request.getHeader("Content-Type");
            if (!"application/sparql-query".equals(ct) && !"application/x-www-form-urlencoded".equals(ct)) {
                throw new ValidationException(400, ErrorMessage.toJson("missing or invalid content type"));
            }
        }
    }

    private static void validateGraphName(RoutingContext ctx) {
        // SPARQL-1.1 Par 2.1.4
        List<String> def = ctx.queryParam("default-graph-uri");
        List<String> named = ctx.queryParam("named-graph-uri");
        if (def.size() > 0 || named.size() > 0) {
            throw new ValidationException(400, ErrorMessage.toJson("RDF datasets not allowed"));
        }
    }

    private static @NotNull SparqlResultType getValidAcceptedResultType(HttpServerRequest request) {
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
        return mappedAccepted;
    }

    private @NotNull TokenPair makeOrGetValidToken(HttpServerRequest request) {
        String authHeader = request.getHeader("Authorization");
        String token;
        if(authHeader == null && enableAnonymous) {
            token = identities.newToken(defaultTokenDuration);
        } else {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new ValidationException(401, ErrorMessage.toJson("Access Denied: no Bearer token provided"));
            }
            String bearer = authHeader.substring("Bearer ".length());
            if(bearer.indexOf(":")> 0) {
                try {
                    token = identities.newToken(bearer, defaultTokenDuration);
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    throw new ValidationException(401, ErrorMessage.toJson("Access Denied: unable to process Bearer token provided"));
                }
            } else {
                token = bearer;
            }
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
        return new TokenPair(token, simpleToken);
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

    record TokenPair(String tokenString, SimpleToken simpleToken){}

}
