package ru.assistant;

import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.assistant.agent.AgentToolLoop;
import ru.assistant.agent.MailProcessingService;
import ru.assistant.agent.SeenStore;
import ru.assistant.config.AppConfig;
import ru.assistant.config.ConfigLoader;
import ru.assistant.config.EnvSecretResolver;
import ru.assistant.infra.AuditLog;
import ru.assistant.llm.ChatResponse;
import ru.assistant.llm.HttpLlmClient;
import ru.assistant.llm.LlmClient;
import ru.assistant.llm.MockLlmClient;
import ru.assistant.llm.ToolCall;
import ru.assistant.mail.MailChannel;
import ru.assistant.mail.MockMailChannel;
import ru.assistant.mail.Msg;
import ru.assistant.mail.OutlookMailChannel;
import ru.assistant.tools.AddReminderTool;
import ru.assistant.tools.CurrentDatetimeTool;
import ru.assistant.tools.FindItemsTool;
import ru.assistant.tools.ReminderStore;
import ru.assistant.tools.Tool;
import ru.assistant.tools.ToolRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Composition root: wires config, mail channel, LLM client, tools and stores
 * into a {@link MailProcessingService}, then either runs one cycle ({@code --once})
 * or polls forever. {@code --mock} swaps in {@link MockMailChannel}/{@link MockLlmClient}
 * (with a scripted demo message) so the app can be exercised without a live
 * Outlook install or an LLM API key.
 */
public final class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private static final String DEFAULT_CONFIG_LOCAL = "config/config.local.yaml";
    private static final String DEFAULT_CONFIG_EXAMPLE = "config/config.example.yaml";

    public static void main(String[] args) {
        try {
            run(args);
        } catch (RuntimeException e) {
            log.error("fatal_startup_error: {}", e.getMessage());
            System.exit(1);
        }
    }

    public static void run(String[] args) {
        Args parsed = Args.parse(args);
        AppConfig config = ConfigLoader.load(parsed.configPath());

        Path storeDir = Paths.get(config.getStore().getPath());
        ReminderStore reminderStore = new ReminderStore(storeDir.resolve("reminders.json"));
        SeenStore seenStore = new SeenStore(storeDir.resolve("seen.json"));
        AuditLog auditLog = new AuditLog(storeDir.resolve("audit.jsonl"));

        MailChannel mailChannel = buildMailChannel(config, parsed.mock);
        LlmClient llmClient = buildLlmClient(config, parsed.mock);

        ToolRegistry toolRegistry = new ToolRegistry(Arrays.<Tool>asList(
                new CurrentDatetimeTool(Clock.systemUTC()),
                new AddReminderTool(reminderStore),
                new FindItemsTool(reminderStore)));

        AgentToolLoop toolLoop = new AgentToolLoop(llmClient, toolRegistry, config.getAgent().getMaxSteps(), auditLog);
        MailProcessingService service = new MailProcessingService(mailChannel, seenStore, toolLoop, auditLog);

        if (parsed.once) {
            service.processCycle();
            return;
        }
        runForever(service, config.getMail().getPollSeconds());
    }

    private static void runForever(MailProcessingService service, int pollSeconds) {
        while (true) {
            service.processCycle();
            try {
                Thread.sleep(pollSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static MailChannel buildMailChannel(AppConfig config, boolean mock) {
        if (mock) {
            return new MockMailChannel(demoMessages());
        }
        return new OutlookMailChannel(config.getMail().getFolder());
    }

    private static LlmClient buildLlmClient(AppConfig config, boolean mock) {
        if (mock) {
            return new MockLlmClient(demoLlmResponses());
        }
        AppConfig.LlmConfig llmConfig = config.getLlm();
        String apiKey = new EnvSecretResolver().resolve(llmConfig.getApiKeyEnv()).orElse("");
        return new HttpLlmClient(new OkHttpClient(), llmConfig.getEndpoint(), llmConfig.getModel(),
                apiKey, llmConfig.getTimeoutMs());
    }

    private static List<Msg> demoMessages() {
        return Collections.singletonList(new Msg("demo-1", "demo@example.com", "Demo",
                "Какое сегодня число?", Instant.now().toString()));
    }

    private static List<ChatResponse> demoLlmResponses() {
        return Arrays.asList(
                new ChatResponse(null, Collections.singletonList(
                        new ToolCall("demo-call-1", "current_datetime", "{}")), "tool_calls"),
                new ChatResponse("Demo cycle complete: current date retrieved via current_datetime.", null, "stop"));
    }

    static final class Args {
        final boolean mock;
        final boolean once;
        final String configPathOverride;

        private Args(boolean mock, boolean once, String configPathOverride) {
            this.mock = mock;
            this.once = once;
            this.configPathOverride = configPathOverride;
        }

        static Args parse(String[] args) {
            boolean mock = false;
            boolean once = false;
            String configPathOverride = null;
            for (String arg : args) {
                if ("--mock".equals(arg)) {
                    mock = true;
                } else if ("--once".equals(arg)) {
                    once = true;
                } else if (arg.startsWith("--config=")) {
                    configPathOverride = arg.substring("--config=".length());
                }
            }
            return new Args(mock, once, configPathOverride);
        }

        Path configPath() {
            if (configPathOverride != null) {
                return Paths.get(configPathOverride);
            }
            Path local = Paths.get(DEFAULT_CONFIG_LOCAL);
            if (Files.exists(local)) {
                return local;
            }
            return Paths.get(DEFAULT_CONFIG_EXAMPLE);
        }
    }

    private App() {
    }
}
