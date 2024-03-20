package smartrics.iotics.sparqlhttp;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.iotics.api.MetaAPIGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

public class IOTICSInitialiser implements Callable<MetaAPIGrpc.MetaAPIStub> {

    private final ManagedChannelBuilder<?> builder;

    public IOTICSInitialiser(String hostDNS) {
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(hostDNS);
        // TODO: see if this is enough or we need to externalise this config
        builder.executor(Executors.newCachedThreadPool((new ThreadFactoryBuilder()).setNameFormat("iot-grpc-%d").build()));
        builder.enableRetry();
        builder.keepAliveWithoutCalls(true);
        this.builder = builder;
    }

    @Override
    // This is meant to be called multiple times, to recreate a connection to IOTICS
    public MetaAPIGrpc.MetaAPIStub call()  {
        ManagedChannel channel = builder.build();
        return MetaAPIGrpc.newStub(channel);
    }
}
