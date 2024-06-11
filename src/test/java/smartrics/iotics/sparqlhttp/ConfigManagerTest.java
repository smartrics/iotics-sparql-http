package smartrics.iotics.sparqlhttp;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import smartrics.iotics.sparqlhttp.integration.EnvFileLoader;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConfigManagerTest {

    private ConfigManager configManager;
    private Map<ConfigManager.ConfigKey, String> overrides;

    @BeforeAll
    public static void setUpClass() {
        try {
            EnvFileLoader.loadEnvFile(".env");
        } catch (IOException e) {
            throw new RuntimeException("unable to find the .env file", e);
        }
    }

    @BeforeEach
    public void setUp() {
        overrides = new EnumMap<>(ConfigManager.ConfigKey.class);
        overrides.put(ConfigManager.ConfigKey.HOST_DNS, "custom_host_dns");
        overrides.put(ConfigManager.ConfigKey.AGENT_SEED, "custom_agent_seed");
    }

    @Test
    public void testOverrideValues() {
        configManager = new ConfigManager(overrides);
        assertThat(configManager.getValue(ConfigManager.ConfigKey.HOST_DNS), is(equalTo("custom_host_dns")));
        assertThat(configManager.getValue(ConfigManager.ConfigKey.AGENT_SEED), is(equalTo("custom_agent_seed")));
    }

    @Test
    public void testMissingValueThrowsException() {
        overrides.put(ConfigManager.ConfigKey.PORT, null);
        Executable executable = () -> new ConfigManager(overrides);
        assertThrows(IllegalArgumentException.class, executable);
    }

    @Test
    public void testLoadValuesFromEnvironment() {
        // Assume the environment or system properties are set.
        System.setProperty("HOST_DNS", "env_host_dns");
        System.setProperty("PORT", "9090");
        configManager = new ConfigManager();
        assertThat(configManager.getValue(ConfigManager.ConfigKey.HOST_DNS), is(equalTo("env_host_dns")));
        assertThat(configManager.getValue(ConfigManager.ConfigKey.PORT), is(equalTo("9090")));
    }
}
