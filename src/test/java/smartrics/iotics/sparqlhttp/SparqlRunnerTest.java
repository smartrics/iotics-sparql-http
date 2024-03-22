package smartrics.iotics.sparqlhttp;

import com.google.protobuf.ByteString;
import com.iotics.api.*;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SparqlRunnerTest {
    @Mock
    private MetaAPIGrpc.MetaAPIStub metaAPIStub;

    @Mock
    private StreamObserver<String> outputStream;

    @InjectMocks
    private SparqlRunner sparqlRunner;

    @Captor
    private ArgumentCaptor<SparqlQueryRequest> queryRequestCaptor;

    @Captor
    private ArgumentCaptor<Throwable> throwableArgumentCaptor;

    @BeforeEach
    void setUp() {
        sparqlRunner = SparqlRunner.SparqlRunnerBuilder.newBuilder()
                .withMetaAPIStub(metaAPIStub)
                .withAgentId("agentId")
                .withScope(Scope.LOCAL)
                .withSparqlResultType(SparqlResultType.SPARQL_CSV)
                .withOutputStream(outputStream)
                .build();
    }

    @Test
    public void delegatesSinglePayloadResponse() {
        sendPayloads(0);

        verify(metaAPIStub).sparqlQuery(queryRequestCaptor.capture(), any());
        SparqlQueryRequest capturedRequest = queryRequestCaptor.getValue();
        Assertions.assertNotNull(capturedRequest, "should match the actual request");

        verify(outputStream).onNext(eq("Result0"));
        verify(outputStream).onCompleted();
    }

    @Test
    public void delegatesManyPayloadResponse() {
        sendPayloads(0, 1, 2);

        verify(metaAPIStub).sparqlQuery(queryRequestCaptor.capture(), any());
        SparqlQueryRequest capturedRequest = queryRequestCaptor.getValue();
        Assertions.assertNotNull(capturedRequest, "should match the actual request");

        InOrder inOrderVerifier = inOrder(outputStream);
        inOrderVerifier.verify(outputStream).onNext(eq("Result0"));
        inOrderVerifier.verify(outputStream).onNext(eq("Result1"));
        inOrderVerifier.verify(outputStream).onNext(eq("Result2"));
        verify(outputStream).onCompleted();
    }

    @Test
    public void delegatesOutOfOrderPayloadResponse() throws Exception {
        try (ExecutorService ex = Executors.newSingleThreadScheduledExecutor()) {
            Future<?> future = ex.submit(() -> sendPayloads(2, 1, 0));
            future.get();
            verify(metaAPIStub).sparqlQuery(queryRequestCaptor.capture(), any());
            SparqlQueryRequest capturedRequest = queryRequestCaptor.getValue();
            Assertions.assertNotNull(capturedRequest, "should match the actual request");

            InOrder inOrderVerifier = inOrder(outputStream);
            inOrderVerifier.verify(outputStream).onNext(eq("Result0"));
            inOrderVerifier.verify(outputStream).onNext(eq("Result1"));
            inOrderVerifier.verify(outputStream).onNext(eq("Result2"));
            verify(outputStream).onCompleted();

        }

    }

    @Test
    public void delegatesOnError() {
        Throwable expectedException = new RuntimeException("Test exception");
        generateError(expectedException);

        // Then
        verify(outputStream).onError(throwableArgumentCaptor.capture());

        Throwable capturedError = throwableArgumentCaptor.getValue();

        assertEquals(expectedException, capturedError, "The onError method was not called with the expected exception.");

    }

    private void sendPayloads(Integer... sequences) {
        Integer max = Collections.max(Arrays.asList(sequences));
        CountDownLatch l = new CountDownLatch(sequences.length);
        doAnswer(invocation -> {
            StreamObserver<SparqlQueryResponse> responseObserver = invocation.getArgument(1);
            // Simulate server response
            for (int seqNum : sequences) {
                responseObserver.onNext(SparqlQueryResponse.newBuilder()
                        .setPayload(SparqlQueryResponse.Payload.newBuilder()
                                .setResultChunk(ByteString.copyFromUtf8("Result" + seqNum))
                                .setSeqNum(seqNum)
                                .setLast(seqNum == max)
                                .build())
                        .build());
                l.countDown();
            }
            l.await();
            responseObserver.onCompleted();
            return null;
        }).when(metaAPIStub).sparqlQuery(any(SparqlQueryRequest.class), any());
        sparqlRunner.run("SELECT * WHERE { ?s ?p ?o }");
    }

    private void generateError(Throwable t) {
        doAnswer(invocation -> {
            StreamObserver<SparqlQueryResponse> responseObserver = invocation.getArgument(1);
            // Simulate server response
            responseObserver.onError(t);
            return null;
        }).when(metaAPIStub).sparqlQuery(any(SparqlQueryRequest.class), any());
        sparqlRunner.run("query with error");
    }


}

