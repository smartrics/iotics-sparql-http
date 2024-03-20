package smartrics.iotics.sparqlhttp;

import com.google.protobuf.ByteString;
import com.iotics.api.MetaAPIGrpc;
import com.iotics.api.Scope;
import com.iotics.api.SparqlQueryRequest;
import com.iotics.api.SparqlQueryResponse;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;
import smartrics.iotics.space.Builders;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class SparqlRunner implements QueryRunner {

    private final MetaAPIGrpc.MetaAPIStub metaAPIStub;
    private final String agentId;
    private final Scope scope;
    private final StreamObserver<String> outputStream;
    private final RunnerConcurrencyManager concurrencyManager;

    private SparqlRunner(MetaAPIGrpc.MetaAPIStub apiStub, String agentId, Scope scope, StreamObserver<String> output, RunnerConcurrencyManager concurrencyManager) {
        this.metaAPIStub = apiStub;
        this.agentId = agentId;
        this.scope = scope;
        this.outputStream = output;
        this.concurrencyManager = concurrencyManager;
    }


    public void run(String query) {
        this.concurrencyManager.resetTimeout();
        metaAPIStub.sparqlQuery(SparqlQueryRequest.newBuilder()
                .setHeaders(Builders.newHeadersBuilder(agentId))
                .setScope(scope)
                .setPayload(SparqlQueryRequest.Payload.newBuilder()
                        .setQuery(ByteString.copyFromUtf8(query))
                        .build())
                .build(), newResponseObserver(outputStream));
    }

    @NotNull
    private StreamObserver<SparqlQueryResponse> newResponseObserver(StreamObserver<String> delegateStream) {
        PriorityBlockingQueue<SparqlQueryResponse.Payload> queue =
                new PriorityBlockingQueue<>(16, Comparator.comparingLong(SparqlQueryResponse.Payload::getSeqNum));
        AtomicInteger expectedSeqNum = new AtomicInteger(0);
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
                            delegateStream.onNext(head.getResultChunk().toStringUtf8());
                            expectedSeqNum.incrementAndGet();
                            if (head.getLast()) {
                                SparqlRunner.this.concurrencyManager.shutdownNow();
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
                        delegateStream.onError(e);
                        // TODO: should I call onCompleted? or will the gRPC call the #onCompleted api?
                        //delegateStream.onCompleted();
                        break;
                    }
                }
            }

            @Override
            public void onError(Throwable throwable) {
                delegateStream.onError(throwable);
            }

            @Override
            public void onCompleted() {
                delegateStream.onCompleted();
            }
        };
    }

    public static final class SparqlRunnerBuilder {
        private MetaAPIGrpc.MetaAPIStub metaAPIStub;
        private String agentId;
        private String query;
        private Scope scope;
        private StreamObserver<String> outputStream;
        private RunnerConcurrencyManager concurrencyManager;

        private SparqlRunnerBuilder() {
        }

        public static SparqlRunnerBuilder newBuilder() {
            return new SparqlRunnerBuilder();
        }

        public SparqlRunnerBuilder withMetaAPIStub(MetaAPIGrpc.MetaAPIStub metaAPIStub) {
            this.metaAPIStub = metaAPIStub;
            return this;
        }

        public SparqlRunnerBuilder withAgentId(String agentId) {
            this.agentId = agentId;
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

        public SparqlRunnerBuilder withConcurrencyManager(RunnerConcurrencyManager concurrencyManager) {
            this.concurrencyManager = concurrencyManager;
            return this;
        }

        public SparqlRunner build() {
            return new SparqlRunner(metaAPIStub, agentId, scope, outputStream, concurrencyManager);
        }
    }
}
