package smartrics.iotics.sparqlhttp;

import com.google.protobuf.ByteString;
import com.iotics.api.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import org.jetbrains.annotations.NotNull;
import smartrics.iotics.host.Builders;
import smartrics.iotics.identity.Identity;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SparqlRunner implements QueryRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(SparqlRunner.class);

    private final MetaAPIGrpc.MetaAPIStub metaAPIStub;
    private final Identity agentIdentity;
    private final Scope scope;
    private final StreamObserver<String> outputStream;
    private final SparqlResultType resultContentType;

    private SparqlRunner(MetaAPIGrpc.MetaAPIStub apiStub, Identity agentIdentity, Scope scope, SparqlResultType resultContentType, StreamObserver<String> output) {
        this.metaAPIStub = apiStub;
        this.agentIdentity = agentIdentity;
        this.scope = scope;
        this.outputStream = output;
        this.resultContentType = resultContentType;
    }

    public void run(String query) {
        StreamObserver<SparqlQueryResponse> responseObserver = newResponseObserver();
        ByteString value = ByteString.copyFromUtf8(query);
        SparqlQueryRequest sparqlQueryRequest = SparqlQueryRequest.newBuilder()
                .setHeaders(Builders.newHeadersBuilder(agentIdentity))
                .setScope(scope)
                .setPayload(SparqlQueryRequest.Payload.newBuilder()
                        .setQuery(value)
                        .setResultContentType(resultContentType)
                        .build())
                .build();
        LOGGER.info("SPARQL query: " + sparqlQueryRequest);
        metaAPIStub.sparqlQuery(sparqlQueryRequest, responseObserver);
    }

    @NotNull
    private StreamObserver<SparqlQueryResponse> newResponseObserver() {
        PriorityBlockingQueue<SparqlQueryResponse.Payload> queue =
                new PriorityBlockingQueue<>(16, Comparator.comparingLong(SparqlQueryResponse.Payload::getSeqNum));
        AtomicInteger expectedSeqNum = new AtomicInteger(0);
        AtomicBoolean onCompletedCalled = new AtomicBoolean(false);
        return new StreamObserver<>() {
            public void onNext(SparqlQueryResponse sparqlQueryResponse) {
                SparqlQueryResponse.Payload payload = sparqlQueryResponse.getPayload();
                queue.put(payload); // Automatically ordered by sequence number
                // Attempt to process queue elements in sequence
                processQueue();
            }

            private void processQueue() {
                while (!Thread.currentThread().isInterrupted()) {
                    SparqlQueryResponse.Payload head;
                    try {
                        // Example of a non-blocking peek operation
                        head = queue.peek(); // Check the head without removing
                        if (head != null && head.getSeqNum() == expectedSeqNum.get()) {
                            queue.take(); // Safe to remove
                            if (head.getStatus().getCode() == Status.Code.OK.value()) {
                                String chunk = head.getResultChunk().toStringUtf8();
                                outputStream.onNext(chunk);
                                expectedSeqNum.incrementAndGet();
                            } else {
                                onError(new RuntimeException(head.getStatus().getMessage()));
                                break;
                            }
                            if (head.getLast()) {
                                callOnCompletedOnce();
                                break;
                            }
                        } else {
                            break; // Head is not what we expect next, or queue is empty
                        }
                    } catch (Exception e) {
                        if (e instanceof InterruptedException) {
                            // Restore the interrupted status
                            Thread.currentThread().interrupt();
                        }
                        outputStream.onError(e);
                        // TODO: should I call onCompleted? or will the gRPC call the #onCompleted api?
                        //delegateStream.onCompleted();
                        break;
                    }
                }
            }

            private void callOnCompletedOnce() {
                boolean canCall = onCompletedCalled.compareAndSet(false, true);
                if (canCall) {
                    outputStream.onCompleted();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                outputStream.onError(throwable);
            }

            @Override
            public void onCompleted() {
                callOnCompletedOnce();
            }
        };
    }

    public static final class SparqlRunnerBuilder {
        private MetaAPIGrpc.MetaAPIStub metaAPIStub;
        private Identity agentIdentity;
        private SparqlResultType resultContentType;
        private Scope scope;
        private StreamObserver<String> outputStream;

        private SparqlRunnerBuilder() {
        }

        public static SparqlRunnerBuilder newBuilder() {
            return new SparqlRunnerBuilder();
        }

        public SparqlRunnerBuilder withMetaAPIStub(MetaAPIGrpc.MetaAPIStub metaAPIStub) {
            this.metaAPIStub = metaAPIStub;
            return this;
        }

        public SparqlRunnerBuilder withAgentIdentity(Identity agentIdentity) {
            this.agentIdentity = agentIdentity;
            return this;
        }

        public SparqlRunnerBuilder withSparqlResultType(SparqlResultType resultContentType) {
            this.resultContentType = resultContentType;
            return this;
        }

        public SparqlRunnerBuilder withScope(Scope scope) {
            this.scope = scope;
            return this;
        }

        public SparqlRunnerBuilder withOutputStream(StreamObserver<String> outputStream) {
            this.outputStream = outputStream;
            return this;
        }

        public SparqlRunner build() {
            return new SparqlRunner(metaAPIStub, agentIdentity, scope, resultContentType, outputStream);
        }
    }
}
