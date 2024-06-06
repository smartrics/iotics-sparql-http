package smartrics.iotics.sparqlhttp.integration;

import smartrics.iotics.identity.Identity;
import smartrics.iotics.identity.SimpleIdentity;
import smartrics.iotics.identity.SimpleIdentityImpl;
import smartrics.iotics.identity.jna.JnaSdkApiInitialiser;
import smartrics.iotics.identity.jna.OsLibraryPathResolver;
import smartrics.iotics.identity.jna.SdkApi;

import java.nio.file.Paths;
import java.time.Duration;

record IdentityData(Identity userIdentity, Identity agentIdentity, SimpleIdentity idSdk) {
    public static IdentityData make() {
        SdkApi api = new JnaSdkApiInitialiser(new OsLibraryPathResolver() {}).get();
        String resolver = System.getProperty("RESOLVER_URL");
        String seed = System.getProperty("SEED");
        if(resolver == null || seed == null) {
            throw new IllegalStateException("missing resolver or seed from env");
        }
        SimpleIdentity si = new SimpleIdentityImpl(api, resolver, seed);
        Identity ui = si.CreateUserIdentity("uKey1", "#user1");
        Identity ai = si.CreateAgentIdentity("aKey1", "#app1");
        si.UserDelegatesAuthenticationToAgent(ai, ui, "#del1");
        return new IdentityData(ui, ai, si);
    }

    public String token(Duration duration) {
        return idSdk.CreateAgentAuthToken(agentIdentity, userIdentity.did(), duration);
    }
}
