package smartrics.iotics.sparqlhttp;

import com.iotics.api.MetaAPIGrpc;
import com.iotics.api.Scope;
import com.iotics.api.SparqlResultType;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Launcher;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static smartrics.iotics.sparqlhttp.ContentTypesMap.mimeFor;

public class SparqlEndpoint extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(SparqlEndpoint.class);

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
        LOGGER.info("Configuration: ");
        printableConfig.forEach((key, value) -> LOGGER.info("  " + key + ": " + value));
    }


    public static void main(String[] args) {
        Launcher.executeCommand("run", SparqlEndpoint.class.getName());
    }

    private static String generateShortUUID() {
        UUID uuid = UUID.randomUUID();
        ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[16]);
        byteBuffer.putLong(uuid.getMostSignificantBits());
        byteBuffer.putLong(uuid.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(byteBuffer.array());
    }

    private static void getValidQuery(HttpServerRequest request) {
        String query = request.getParam("query");
        if ("get".equalsIgnoreCase(request.method().name())) {
            if (query == null) {
                // service description
                return;
            } else if (query.isEmpty()) {
                throw new ValidationException(400, ErrorMessage.toJson("missing query"));
            }
        }

        if ("post".equalsIgnoreCase(request.method().name())) {
            // Clients must set the content type header of the HTTP request to application/sparql-query
            // spec par 2.1.3
            String ct = request.getHeader("Content-Type");
            if(Strings.isBlank(ct)) {
                throw new ValidationException(400, ErrorMessage.toJson("missing content type"));
            }
            String mime = ct.split(";")[0].trim();
            if (!"application/sparql-query".equals(mime) && !"application/x-www-form-urlencoded".equals(mime)) {
                throw new ValidationException(400, ErrorMessage.toJson("invalid content type"));
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

    public Router createRouter() {
        Router router = Router.router(vertx);

        router.route().handler(this::logRequestAndResponse);

        // Handle /health route separately
        router.get("/*").handler(StaticHandler.create("webroot"));
        router.get("/health").handler(this::handleHealth);

        // Apply the BodyHandler and validateRequest handler to the /sparql routes
        router.route("/sparql*").handler(BodyHandler.create()).handler(this::validateRequest);

        // Define the /sparql routes
        router.get("/sparql/local").handler(ctx -> this.handleGet(ctx, Scope.LOCAL));
        router.get("/sparql").handler(ctx -> this.handleGet(ctx, Scope.GLOBAL));
        router.post("/sparql/local").handler(ctx -> this.handlePost(ctx, Scope.LOCAL));
        router.post("/sparql").handler(ctx -> this.handlePost(ctx, Scope.GLOBAL));
        return router;
    }

    private void logRequestAndResponse(RoutingContext ctx) {
        String remoteAddress = ctx.request().remoteAddress().toString();
        String uri = ctx.request().uri();
        String absoluteUri = ctx.request().absoluteURI();
        String method = ctx.request().method().toString();
        String headers = ctx.request().headers().entries().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(", "));
        String id = generateShortUUID();

        LOGGER.debug("Request [id=" + id + "][URI=" + uri + "][method=" + method + "][from=" + remoteAddress + "][absoluteURI=" + absoluteUri + "][headers=" + headers + "]");
        ctx.addBodyEndHandler(v -> {
            int statusCode = ctx.response().getStatusCode();
            String statusMessage = ctx.response().getStatusMessage();
            String respHeaders = ctx.response().headers().entries().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(", "));
            LOGGER.debug("Response [id=" + id + "][statusCode=" + statusCode + "][statusMessage=" + statusMessage + "][headers=" + respHeaders + "]");
        });
        ctx.next();
    }

    public void start() {
        String port = Optional.ofNullable(System.getenv("PORT")).orElse("8080");
        Router router = createRouter();
        LOGGER.info("Starting on port " + port);
        vertx.createHttpServer().requestHandler(router).listen(Integer.parseInt(port));
    }

    private void handleHealth(RoutingContext ctx) {
        ctx.response().setStatusCode(200);
        ctx.response().end("{ \"status\" : \"OK\" }");
    }

    private void handleGet(RoutingContext ctx, Scope scope) {
        try {
            String encodedQuery = ctx.request().getParam("query");
            if (encodedQuery == null) {
                // service description
                ctx.response().setStatusCode(200);
                ctx.response().send(serviceDescription(scope));
            } else {
                String query = URLDecoder.decode(encodedQuery, StandardCharsets.UTF_8);
                String token = ctx.get("token");
                handle(scope, ctx, token, query);
            }
        } catch (ValidationException e) {
            sendError(e.getCode(), e.getMessage(), ctx.response());
        }
    }

    private String serviceDescription(Scope scope) {
        String endpoint = "/sparql";
        if (scope.equals(Scope.LOCAL)) {
            endpoint += "/local";
        }
        return """
                {
                  "@context": {
                    "sd": "http://www.w3.org/ns/sparql-service-description#",
                    "void": "http://www.w3.org/ns/void#"
                  },
                  "@type": "sd:Service",
                  "sd:endpoint": { "@id": "http://localhost/@@@@" },
                  "void:sparqlEndpoint": { "@id": "http://localhost/@@@@" },
                  "sd:name": "IOTICS SPARQL Endpoint",
                  "sd:description": "This is a SPARQL endpoint for accessing IOTICSpace via HTTP",
                  "sd:supportsQueryLanguage": { "@id": "sd:SPARQL11Query" },
                  "sd:resultFormat": [
                    { "sd:contentType": "application/sparql-results+xml" },
                    { "sd:contentType": "application/sparql-results+json" },
                    { "sd:contentType": "text/csv" },
                    { "sd:contentType": "application/rdf+xml" },
                    { "sd:contentType": "text/turtle" },
                    { "sd:contentType": "application/x-turtle" },
                    { "sd:contentType": "application/n-triples" }
                  ],
                  "void:triplestore": "IOTICSpace"
                }
                """.replaceAll("@@@@", endpoint);
    }

    private void handlePost(RoutingContext ctx, Scope scope) {
        try {
            String query = ctx.body().asString();
            String token = ctx.get("token");
            String ct = ctx.request().getHeader("Content-Type");
            ct = ct.split(";")[0].trim();
            if ("application/x-www-form-urlencoded".equals(ct)) {
                MultiMap formAttributes = ctx.request().formAttributes();
                // TODO: we should check the charset in the content type if available
                query = URLDecoder.decode(query, StandardCharsets.UTF_8);
            }
            if (query != null) {
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
        ManagedChannel channel = null;
        try {
            String host = configManager.getValue(ConfigManager.ConfigKey.HOST_DNS);
            IOTICSConnection connection = new IOTICSConnection(host);
            channel = connection.newChannel(token);
            MetaAPIGrpc.MetaAPIStub api = MetaAPIGrpc.newStub(channel);
            SparqlResultType type = ctx.get("acceptedResponseType");
            String mime = mimeFor(type);
            if (mime != null) {
                ctx.response().headers().set("Content-Type", mime);
            }
            ctx.response().headers().add("Access-Control-Allow-Origin", "*");
            //StreamObserverToStringAdapter outputStream = new StreamObserverToStringAdapter();

            QueryRunner runner = SparqlRunner.SparqlRunnerBuilder.newBuilder()
                    .withScope(scope)
                    .withSparqlResultType(type)
                    .withMetaAPIStub(api)
                    .withOutputStream(new StreamObserver<String>() {

                        @Override
                        public void onNext(String s) {
                            ctx.response().setStatusCode(200);
                            ctx.response().send(s);
                        }

                        @Override
                        public void onError(Throwable e) {
                            sendError(400, ErrorMessage.toJson(e.getMessage()), ctx.response());
                        }

                        @Override
                        public void onCompleted() {

                        }
                    })
                    .withAgentIdentity(identities.agentIdentity())
                    .build();
            runner.run(query);
        } catch (Exception e) {
            LOGGER.warn("exception when handling request", e);
            String message = e.getMessage();
            if (e.getCause() != null) {
                message = message + ": " + e.getCause().getMessage();
            }
            sendError(500, ErrorMessage.toJson(message), ctx.response());
        } finally {
            if(channel != null) {
                channel.shutdown();
            }
        }
    }

    private void sendError(int statusCode, String message, HttpServerResponse response) {
        response.setStatusCode(statusCode).setStatusMessage(message).end();
    }

    boolean validateRequest(RoutingContext ctx) throws ValidationException {
        try {
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
        } catch (ValidationException e) {
            sendError(e.getCode(), e.getMessage(), ctx.response());
        }
        return true;
    }

    private @NotNull TokenPair makeOrGetValidToken(HttpServerRequest request) {
        String authHeader = request.getHeader("Authorization");
        String token;
        if (authHeader == null && enableAnonymous) {
            token = identities.newToken(defaultTokenDuration);
        } else {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new ValidationException(401, ErrorMessage.toJson("Access Denied: no Bearer token provided"));
            }
            String bearer = authHeader.substring("Bearer ".length());
            if (bearer.indexOf(":") > 0) {
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

    record TokenPair(String tokenString, SimpleToken simpleToken) {
    }
}
