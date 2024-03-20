package smartrics.iotics.sparqlhttp;

import com.iotics.api.MetaAPIGrpc;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

public class CallHandler  {

    private final AtomicReference<MetaAPIGrpc.MetaAPIStub> metaAPIRef = new AtomicReference<>();

    public CallHandler(Callable<MetaAPIGrpc.MetaAPIStub> ioticsApiInitialiser) throws Exception {
        metaAPIRef.set(ioticsApiInitialiser.call());
    }



}
