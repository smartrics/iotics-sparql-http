package smartrics.iotics.sparqlhttp;

import smartrics.iotics.identity.Identity;
import smartrics.iotics.identity.SimpleIdentity;
import smartrics.iotics.identity.jna.JnaSdkApiInitialiser;
import smartrics.iotics.identity.jna.SdkApi;
import smartrics.iotics.identity.resolver.HttpResolverClient;
import smartrics.iotics.identity.resolver.ResolverClient;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;

record IdentityData(Identity userIdentity, Identity agentIdentity, SimpleIdentity idSdk) {
    public static IdentityData make() {
        String os = System.getProperty("os.name").toLowerCase();
        String libPath = Paths.get(os.contains("win") ? "lib/lib-iotics-id-sdk.dll" : "lib/lib-iotics-id-sdk.so")
                .toAbsolutePath()
                .toString();
        SdkApi api = new JnaSdkApiInitialiser(libPath).get();
        String resolver = System.getenv("RESOLVER_URL");
        String seed = System.getenv("SEED");
        SimpleIdentity si = new SimpleIdentity(api, resolver, seed);
        Identity ui = si.CreateUserIdentity("uKey1", "#user1");
        Identity ai = si.CreateAgentIdentity("aKey1", "#app1");
        si.UserDelegatesAuthenticationToAgent(ai, ui, "#del1");
        return new IdentityData(ui, ai, si);
    }

    public String token(Duration duration) {
        return idSdk.CreateAgentAuthToken(agentIdentity, userIdentity.did(), duration);
    }
}
