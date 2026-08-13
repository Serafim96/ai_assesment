package ru.assistant.config;

import org.junit.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

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
}
