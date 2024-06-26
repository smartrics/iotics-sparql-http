package smartrics.iotics.sparqlhttp;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ConfigManager {

    private final Map<ConfigKey, String> configValues;

    private static final String DEFAULT_PORT = "8080";
    private static final String DEFAULT_SECURE_PORT = "8443";
    private static final String DEFAULT_TOKEN_DURATION = "3600";
    private static final String DEFAULT_ENABLE_ANON = "false";

    public enum ConfigKey {
        HOST_DNS("HOST_DNS"),
        AGENT_SEED("AGENT_SEED"),
        AGENT_KEY("AGENT_KEY"),
        USER_SEED("USER_SEED"),
        USER_KEY("USER_KEY"),
        ENABLE_ANON("ENABLE_ANON", DEFAULT_ENABLE_ANON),
        PORT("PORT", DEFAULT_PORT),
        SECURE_PORT("SECURE_PORT", DEFAULT_SECURE_PORT),
        TOKEN_DURATION("TOKEN_DURATION", DEFAULT_TOKEN_DURATION);

        private final String key;
        private final String defaultValue;

        ConfigKey(String key) {
            this(key, null);
        }

        ConfigKey(String key, String defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
        }

        public String getKey() {
            return key;
        }

        public String getDefaultValue() {
            return defaultValue;
        }
    }

    public ConfigManager() {
        this(null);
    }

    public ConfigManager(Map<ConfigKey, String> overrides) {
        configValues = new EnumMap<>(ConfigKey.class);
        setupEnv(overrides);
        validateConfig();
    }

    private void setupEnv(Map<ConfigKey, String> overrides) {
        for (ConfigKey configKey : ConfigKey.values()) {
            String value = load(configKey.getKey());
            configValues.put(configKey, value != null ? value : configKey.getDefaultValue());
        }

        if (overrides != null) {
            configValues.putAll(overrides);
        }
    }

    public Map<String, String> getPrintableConfig() {
        Map<String, String> printableConfig = new HashMap<>();
        for (ConfigKey configKey : ConfigKey.values()) {
            String value = configValues.get(configKey);
            if (configKey == ConfigKey.AGENT_SEED || configKey == ConfigKey.USER_SEED) {
                printableConfig.put(configKey.getKey(), value != null ? "<secret configured>" : "<not configured>");
            } else {
                printableConfig.put(configKey.getKey(), value != null ? value : "<not configured>");
            }
        }
        return printableConfig;
    }

    public static String load(String key) {
        String value = System.getenv(key);
        if (value != null) {
            return value;
        }
        return System.getProperty(key);
    }

    private void validateConfig() {
        for (ConfigKey configKey : ConfigKey.values()) {
            if (configValues.get(configKey) == null) {
                throw new IllegalArgumentException("Configuration value for " + configKey.getKey() + " is missing.");
            }
        }
    }

    public String getValue(ConfigKey configKey) {
        return configValues.get(configKey);
    }
}
