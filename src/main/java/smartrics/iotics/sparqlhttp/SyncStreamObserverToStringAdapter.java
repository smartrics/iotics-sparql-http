package smartrics.iotics.sparqlhttp;

import io.grpc.stub.StreamObserver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class SyncStreamObserverToStringAdapter implements StreamObserver<String> {

    private final StringBuilder b = new StringBuilder();
    private final CountDownLatch latch = new CountDownLatch(1);

    private final AtomicReference<Throwable> err = new AtomicReference<>();


    @Override
    public void onNext(String s) {
        b.append(s);
    }

    public String getString() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("unable to terminate query");
        }
        if (err.get() != null) {
            throw new IllegalStateException("error executing the query", err.get());
        }
        return b.toString();
    }

    @Override
    public void onError(Throwable throwable) {
        System.out.println("on error count latch");
        err.set(throwable);
        latch.countDown();
    }

    @Override
    public void onCompleted() {
        latch.countDown();
    }
}