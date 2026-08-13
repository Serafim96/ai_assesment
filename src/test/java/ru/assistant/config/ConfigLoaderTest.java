package ru.assistant.config;

import org.junit.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConfigLoaderTest {

    private Path fixture(String name) throws URISyntaxException {
        return Paths.get(getClass().getClassLoader().getResource(name).toURI());
    }

    @Test
    public void loadsAllFieldsFromYaml() throws Exception {
        AppConfig config = ConfigLoader.load(fixture("test-config.yaml"));

        assertEquals("https://api.example.com/v1/chat/completions", config.getLlm().getEndpoint());
        assertEquals("test-model", config.getLlm().getModel());
        assertEquals("TEST_LLM_API_KEY", config.getLlm().getApiKeyEnv());
        assertEquals(5000, config.getLlm().getTimeoutMs());

        assertEquals(4, config.getAgent().getMaxSteps());

        assertEquals("./test-data", config.getStore().getPath());

        assertEquals(15, config.getMail().getPollSeconds());
        assertEquals("TestProfile", config.getMail().getProfile());
        assertEquals("TestInbox", config.getMail().getFolder());
    }

    @Test
    public void brokenYamlThrowsConfigExceptionWithoutLeakingRawContent() throws Exception {
        try {
            ConfigLoader.load(fixture("broken-config.yaml"));
            fail("Expected ConfigException for malformed YAML");
        } catch (ConfigException e) {
            assertTrue(e.getMessage().contains("broken-config.yaml"));
            assertFalse(e.getMessage().contains("not valid yaml"));
        }
    }

    @Test
    public void incompleteConfigThrowsConfigExceptionNamingMissingField() throws Exception {
        try {
            ConfigLoader.load(fixture("incomplete-config.yaml"));
            fail("Expected ConfigException for missing required fields");
        } catch (ConfigException e) {
            assertTrue(e.getMessage().contains("agent"));
        }
    }

    @Test(expected = ConfigException.class)
    public void missingFileThrowsConfigException() {
        ConfigLoader.load(Paths.get("does-not-exist.yaml"));
    }
}
