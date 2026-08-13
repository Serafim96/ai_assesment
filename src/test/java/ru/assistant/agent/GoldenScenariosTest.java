package ru.assistant.agent;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import ru.assistant.llm.ChatResponse;
import ru.assistant.llm.MockLlmClient;
import ru.assistant.llm.ToolCall;
import ru.assistant.mail.MockMailChannel;
import ru.assistant.mail.Msg;
import ru.assistant.tools.AddReminderTool;
import ru.assistant.tools.CurrentDatetimeTool;
import ru.assistant.tools.FindItemsTool;
import ru.assistant.tools.ReminderStore;
import ru.assistant.tools.Tool;
import ru.assistant.tools.ToolRegistry;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers the four golden scenarios from the assignment (task.txt §10), end to end
 * through MailProcessingService: real tools + temp-file stores, MockLlmClient scripted
 * with the tool_call sequence a real LLM would choose, MockMailChannel as the channel.
 */
public class GoldenScenariosTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-13T09:00:00Z"), ZoneOffset.UTC);
    private static final String TOMORROW_10_UTC = "2026-08-14T10:00:00Z";

    @Test
    public void golden1_addReminder() throws Exception {
        ReminderStore reminderStore = new ReminderStore(reminderFile());
        ToolRegistry registry = new ToolRegistry(Arrays.<Tool>asList(new AddReminderTool(reminderStore)));
        MockLlmClient llm = new MockLlmClient(Arrays.asList(
                new ChatResponse(null, Arrays.asList(new ToolCall("c1", "add_reminder",
                        "{\"text\":\"Позвонить Ивану\",\"dueIso\":\"" + TOMORROW_10_UTC + "\"}")), "tool_calls"),
                new ChatResponse("Напоминание добавлено на " + TOMORROW_10_UTC + ".", null, "stop")
        ));
        AgentToolLoop loop = new AgentToolLoop(llm, registry, 5);
        Msg msg = new Msg("id-1", "user@example.com", "Напоминание",
                "Напомни завтра в 10 позвонить Ивану", "2026-08-13T09:00:00Z");
        MockMailChannel channel = new MockMailChannel(Arrays.asList(msg));
        MailProcessingService service = new MailProcessingService(channel, new SeenStore(seenFile()), loop);

        service.processCycle();

        assertTrue(reminderStore.find("Ивану").size() == 1);
        String reply = channel.getSentReplies().get(0).getBody();
        assertTrue(reply.contains(TOMORROW_10_UTC));
    }

    @Test
    public void golden2_findItems() throws Exception {
        ReminderStore reminderStore = new ReminderStore(reminderFile());
        reminderStore.add("Позвонить Ивану", TOMORROW_10_UTC);
        reminderStore.add("Купить молоко", "2026-08-15T18:00:00Z");
        ToolRegistry registry = new ToolRegistry(Arrays.<Tool>asList(new FindItemsTool(reminderStore)));
        MockLlmClient llm = new MockLlmClient(Arrays.asList(
                new ChatResponse(null, Arrays.asList(new ToolCall("c1", "find_items", "{\"query\":\"\"}")), "tool_calls"),
                new ChatResponse("У вас 2 запланированных дела: Позвонить Ивану, Купить молоко.", null, "stop")
        ));
        AgentToolLoop loop = new AgentToolLoop(llm, registry, 5);
        Msg msg = new Msg("id-2", "user@example.com", "Планы",
                "Что у меня запланировано?", "2026-08-13T09:00:00Z");
        MockMailChannel channel = new MockMailChannel(Arrays.asList(msg));
        MailProcessingService service = new MailProcessingService(channel, new SeenStore(seenFile()), loop);

        service.processCycle();

        String reply = channel.getSentReplies().get(0).getBody();
        assertTrue(reply.contains("Позвонить Ивану"));
        assertTrue(reply.contains("Купить молоко"));
    }

    @Test
    public void golden3_currentDatetime() throws Exception {
        ToolRegistry registry = new ToolRegistry(Arrays.<Tool>asList(new CurrentDatetimeTool(FIXED_CLOCK)));
        MockLlmClient llm = new MockLlmClient(Arrays.asList(
                new ChatResponse(null, Arrays.asList(new ToolCall("c1", "current_datetime", "{}")), "tool_calls"),
                new ChatResponse("Сегодня 2026-08-13T09:00:00Z.", null, "stop")
        ));
        AgentToolLoop loop = new AgentToolLoop(llm, registry, 5);
        Msg msg = new Msg("id-3", "user@example.com", "Дата",
                "Какое сегодня число?", "2026-08-13T09:00:00Z");
        MockMailChannel channel = new MockMailChannel(Arrays.asList(msg));
        MailProcessingService service = new MailProcessingService(channel, new SeenStore(seenFile()), loop);

        service.processCycle();

        String reply = channel.getSentReplies().get(0).getBody();
        assertTrue(reply.contains("2026-08-13"));
    }

    @Test
    public void golden4_emptyOrGarbageMessageIsHandledGracefully() throws Exception {
        ReminderStore reminderStore = new ReminderStore(reminderFile());
        ToolRegistry registry = new ToolRegistry(Arrays.<Tool>asList(
                new AddReminderTool(reminderStore), new FindItemsTool(reminderStore)));
        MockLlmClient llm = new MockLlmClient(Arrays.asList(
                new ChatResponse("Извините, я не понял ваш запрос.", null, "stop"),
                new ChatResponse("Извините, я не понял ваш запрос.", null, "stop")
        ));
        AgentToolLoop loop = new AgentToolLoop(llm, registry, 5);
        Msg empty = new Msg("id-4", "user@example.com", "", "", "2026-08-13T09:00:00Z");
        Msg garbage = new Msg("id-5", "user@example.com", "asdf", "asdkjfh 12930 !@#$ ыыы", "2026-08-13T09:00:00Z");
        MockMailChannel channel = new MockMailChannel(Arrays.asList(empty, garbage));
        MailProcessingService service = new MailProcessingService(channel, new SeenStore(seenFile()), loop);

        service.processCycle();

        assertTrue(channel.getSentReplies().size() == 2);
        assertTrue(reminderStore.find("").isEmpty());
        assertFalse(new java.io.File(auditFile().toString()).exists());
    }

    private Path seenFile() {
        return tmp.getRoot().toPath().resolve("seen.json");
    }

    private Path reminderFile() {
        return tmp.getRoot().toPath().resolve("reminders.json");
    }

    private Path auditFile() {
        return tmp.getRoot().toPath().resolve("audit.jsonl");
    }
}
