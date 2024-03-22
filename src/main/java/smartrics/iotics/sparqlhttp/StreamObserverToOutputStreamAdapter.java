package smartrics.iotics.sparqlhttp;

import io.grpc.stub.StreamObserver;
import spark.Response;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class StreamObserverToOutputStreamAdapter implements StreamObserver<String> {

    private final OutputStream outputStream;
    private final Response response;
    private boolean hasErrored = false;

    public StreamObserverToOutputStreamAdapter(OutputStream outputStream, Response response) {
        this.outputStream = outputStream;
        this.response = response;
    }

    @Override
    public void onNext(String s) {
        if (!hasErrored) { // Only write if no previous errors occurred
            try {
                outputStream.write(s.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            } catch (IOException e) {
                onError(e); // Delegate to onError
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
        hasErrored = true; // Prevent further writes
        // It might not be effective to set the status here if headers are already sent
        // Consider logging the error or handling it differently
        System.err.println("Error streaming response: " + throwable.getMessage());
    }

    @Override
    public void onCompleted() {
        // If you need to do something on completion, consider that response might have started streaming
        // Ensure any actions here do not conflict with the HTTP protocol (like setting status codes)
    }
}