package ru.assistant;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/**
 * Exercises the App composition root end to end with --mock --once, so a single
 * cycle runs without any live Outlook/LLM dependency and without an infinite poll
 * loop (which a unit test cannot otherwise observe finishing).
 */
public class AppSmokeTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void mockOnceRunCompletesWithoutThrowingAndProducesAuditTrail() throws Exception {
        Path storeDir = tmp.getRoot().toPath().resolve("data");
        Path configFile = tmp.newFile("app-smoke-config.yaml").toPath();
        Files.write(configFile, configYaml(storeDir).getBytes(StandardCharsets.UTF_8));

        App.run(new String[] {"--mock", "--once", "--config=" + configFile});

        assertTrue("expected an audit trail file to be written during the demo cycle",
                Files.exists(storeDir.resolve("audit.jsonl")));
    }

    private String configYaml(Path storeDir) {
        String storePath = storeDir.toString().replace("\\", "\\\\");
        return "llm:\n"
                + "  endpoint: \"http://localhost/unused\"\n"
                + "  model: \"unused\"\n"
                + "  apiKeyEnv: \"APP_SMOKE_TEST_UNUSED_KEY\"\n"
                + "  timeoutMs: 1000\n"
                + "agent:\n"
                + "  maxSteps: 3\n"
                + "store:\n"
                + "  path: \"" + storePath + "\"\n"
                + "mail:\n"
                + "  pollSeconds: 1\n"
                + "  profile: \"Outlook\"\n"
                + "  folder: \"Inbox\"\n";
    }
}
