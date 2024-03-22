package smartrics.iotics.sparqlhttp;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.iotics.api.MetaAPIGrpc;
import io.grpc.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class IOTICSConnection {

    private final ManagedChannelBuilder<?> builder;

    public IOTICSConnection(String hostDNS) {
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(hostDNS);
        // TODO: see if this is enough or we need to externalise this config
        builder.executor(Executors.newCachedThreadPool((new ThreadFactoryBuilder()).setNameFormat("iot-grpc-%d").build()));
        builder.enableRetry();
        builder.keepAliveWithoutCalls(true);
        this.builder = builder;
    }

    // This is meant to be called multiple times, to recreate a connection to IOTICS
    public MetaAPIGrpc.MetaAPIStub newMetaAPIStub(String token) {
        TokenInjector tokenInjector = new TokenInjector(token);
        List<ClientInterceptor> interceptorList = new ArrayList<>();
        interceptorList.add(tokenInjector);
        builder.intercept(interceptorList);
        ManagedChannel channel = builder.build();
        return MetaAPIGrpc.newStub(channel);
    }

    public record TokenInjector(String token) implements ClientInterceptor {
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
            return new TokenInjector.HeaderAttachingClientCall<>(next.newCall(method, callOptions));
        }

        private final class HeaderAttachingClientCall<ReqT, RespT> extends ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT> {
            HeaderAttachingClientCall(ClientCall<ReqT, RespT> call) {
                super(call);
            }

            public void start(ClientCall.Listener<RespT> responseListener, Metadata headers) {
                Metadata.Key<String> AUTHORIZATION_KEY = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
                Metadata metadata = new Metadata();
                metadata.put(AUTHORIZATION_KEY, "bearer " + token);
                headers.merge(metadata);
                super.start(responseListener, headers);
            }
        }
    }

}
