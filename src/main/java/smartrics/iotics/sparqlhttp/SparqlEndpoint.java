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

import static smartrics.iotics.sparqlhttp.ContentTypesMap.mimeFor;

public class SparqlEndpoint extends AbstractVerticle {
    private final Identities identities;
    private final Duration defaultTokenDuration;
    private final Boolean enableAnonymous;
    private final ConfigManager configManager;

    public SparqlEndpoint() {
        this(HashMap.newHashMap(0));
    }

    public SparqlEndpoint(Map<ConfigManager.ConfigKey, String> configOverrides) {
        configManager = new ConfigManager(configOverrides);
        identities = new Identities(configManager.getValue(ConfigManager.ConfigKey.HOST_DNS),
                configManager.getValue(ConfigManager.ConfigKey.USER_KEY), configManager.getValue(ConfigManager.ConfigKey.USER_SEED),
                configManager.getValue(ConfigManager.ConfigKey.AGENT_KEY), configManager.getValue(ConfigManager.ConfigKey.AGENT_SEED));
        defaultTokenDuration = Duration.parse(configManager.getValue(ConfigManager.ConfigKey.TOKEN_DURATION));
        enableAnonymous = Boolean.valueOf(configManager.getValue(ConfigManager.ConfigKey.ENABLE_ANON));

        Map<String, String> printableConfig = configManager.getPrintableConfig();
        System.out.println("Configuration: ");
        printableConfig.forEach((key, value) -> System.out.println("  " + key + ": " + value));
    }


    public static void main(String[] args) {
        Launcher.executeCommand("run", SparqlEndpoint.class.getName());
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
            String host = configManager.getValue(ConfigManager.ConfigKey.HOST_DNS);
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
