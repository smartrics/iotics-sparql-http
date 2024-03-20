package smartrics.iotics.sparqlhttp;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.ByteString;
import com.iotics.api.MetaAPIGrpc;
import com.iotics.api.Scope;
import com.iotics.api.SparqlQueryRequest;
import com.iotics.api.SparqlQueryResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;
import smartrics.iotics.space.Builders;
import spark.Request;
import spark.Response;
import spark.Route;

import static spark.Spark.*;

public class SparqlProxyApplication {


    private static AtomicReference<MetaAPIGrpc.MetaAPIStub> metaAPIStubRef;

    public static MetaAPIGrpc.MetaAPIStub getMetaApi() {
        return metaAPIStubRef.get();
    }

    public static void main(String[] args) {
        metaAPIStubRef = new AtomicReference<>();
        configureIotics();
        startHttp();
    }

    private static void configureIotics() {
        ManagedChannelBuilder<?> builder = newManagedChannelBuilder();
        ManagedChannel channel = builder.build();
        metaAPIStubRef.set(MetaAPIGrpc.newStub(channel));
    }

    @NotNull
    private static ManagedChannelBuilder<?> newManagedChannelBuilder() {
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget("demo.iotics.com");
        builder.executor(Executors.newCachedThreadPool((new ThreadFactoryBuilder()).setNameFormat("iot-grpc-%d").build()));
        return builder;
    }

    private static void startHttp() {
        before("/*", SparqlProxyApplication::validateRequest);
        path("/sparql", () -> {
            get("/", SparqlProxyApplication::sparqlGet);
            post("/", SparqlProxyApplication::sparqlPost);
        });
    }

    private static String sparqlPost(Request request, Response response) {
        return null;
    }

    private static void validateRequest(Request request, Response response) {
        // check there's a token
    }

    private static String sparqlGet(Request request, Response response) {
        return null;
    }

    // this needs to terminate at some point - put it in a timer


}