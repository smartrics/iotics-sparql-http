package smartrics.iotics.sparqlhttp;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.jetbrains.annotations.NotNull;
import smartrics.iotics.identity.Identity;
import smartrics.iotics.identity.SimpleIdentityImpl;
import smartrics.iotics.identity.jna.JnaSdkApiInitialiser;
import smartrics.iotics.identity.jna.OsLibraryPathResolver;
import smartrics.iotics.identity.jna.SdkApi;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

public class Identities {

    private final Identity agentIdentity;
    private final Identity userIdentity;
    private final SimpleIdentityImpl simpleIdentity;
    private final LoadingCache<String, Identity> cache;

    public Identities(String host, String userKey, String userSeed, String agentKey, String agentSeed) {
        String resolver = ResolverFinder.findResolver(host);
        SdkApi api = new JnaSdkApiInitialiser(new OsLibraryPathResolver() {}).get();
        simpleIdentity = new SimpleIdentityImpl(api, resolver, agentSeed);
        agentIdentity = simpleIdentity.CreateAgentIdentity(agentKey, "#app1");
        userIdentity = simpleIdentity.CreateUserIdentity(agentKey, "#app1");
        simpleIdentity.UserDelegatesAuthenticationToAgent(agentIdentity, userIdentity, "#del1");

        CacheLoader<String, Identity> loader = newCacheLoader();
        cache = CacheBuilder.newBuilder().build(loader);
    }

    public Identity agentIdentity() {
        return agentIdentity;
    }

    private Identity makeUserIdentity(String userKeyId, String userSeed) {
        Identity ui = simpleIdentity.CreateUserIdentity(userKeyId, "#user1");
        simpleIdentity.UserDelegatesAuthenticationToAgent(agentIdentity, ui, "#del1");
        return ui;
    }

    public String newToken(Duration duration) {
        return simpleIdentity.CreateAgentAuthToken(agentIdentity, userIdentity.did(), duration);
    }

    public String newToken(String bearer, Duration duration) {
        try {
            Identity userIdentity = cache.get(bearer);
            return simpleIdentity.CreateAgentAuthToken(agentIdentity, userIdentity.did(), duration);
        } catch (ExecutionException e) {
            throw new IllegalArgumentException("invalid bearer or unable to get token", e);
        }
    }

    private @NotNull CacheLoader<String, Identity> newCacheLoader() {
        CacheLoader<String, Identity> loader;
        loader = new CacheLoader<>() {
            @NotNull
            @Override
            public Identity load(@NotNull String authString) {
                String[] parts = authString.split(":");
                if(parts.length != 2) {
                    throw new IllegalArgumentException("invalid authString");
                }
                String userKey = parts[0];
                String seed = parts[1];
                return makeUserIdentity(userKey, seed);
            }
        };
        return loader;
    }

}
