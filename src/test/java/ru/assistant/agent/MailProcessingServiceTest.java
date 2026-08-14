package ru.assistant.agent;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import ru.assistant.infra.AuditLog;
import ru.assistant.infra.LogEvents;
import ru.assistant.llm.ChatResponse;
import ru.assistant.llm.LlmException;
import ru.assistant.llm.MockLlmClient;
import ru.assistant.mail.MailChannel;
import ru.assistant.mail.MailChannelException;
import ru.assistant.mail.MockMailChannel;
import ru.assistant.mail.Msg;
import ru.assistant.tools.Tool;
import ru.assistant.tools.ToolRegistry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MailProcessingServiceTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final Msg msg1 = new Msg("id-1", "alice@example.com", "Subj", "Hello", "2026-08-13T10:00:00Z");
    private final Msg msg2 = new Msg("id-2", "bob@example.com", "Subj 2", "Hi", "2026-08-13T11:00:00Z");

    @Test
    public void singleMessageWithFinalAnswerIsRepliedToAndMarkedSeen() throws Exception {
        MockMailChannel channel = new MockMailChannel(Arrays.asList(msg1));
        SeenStore seenStore = new SeenStore(seenFile());
        AgentToolLoop loop = new AgentToolLoop(
                new MockLlmClient(Arrays.asList(new ChatResponse("Hi there!", null, "stop"))),
                new ToolRegistry(Collections.<Tool>emptyList()), 5);
        MailProcessingService service = new MailProcessingService(channel, seenStore, loop);

        service.processCycle();

        assertEquals(1, channel.getSentReplies().size());
        assertEquals(msg1, channel.getSentReplies().get(0).getOriginal());
        assertEquals("Hi there!", channel.getSentReplies().get(0).getBody());
    }

    @Test
    public void repeatedDeliveryOfSameMessageIsRepliedToOnlyOnce() throws Exception {
        RepeatingMailChannel channel = new RepeatingMailChannel(msg1);
        SeenStore seenStore = new SeenStore(seenFile());
        AgentToolLoop loop = new AgentToolLoop(
                new MockLlmClient(Arrays.asList(new ChatResponse("Hi there!", null, "stop"))),
                new ToolRegistry(Collections.<Tool>emptyList()), 5);
        MailProcessingService service = new MailProcessingService(channel, seenStore, loop);

        service.processCycle();
        service.processCycle();

        assertEquals(1, channel.replies.size());
    }

    @Test
    public void newInstanceAfterRestartDoesNotResendReplyForAlreadySeenMessage() throws Exception {
        Path file = seenFile();
        RepeatingMailChannel channel = new RepeatingMailChannel(msg1);
        AgentToolLoop loop = new AgentToolLoop(
                new MockLlmClient(Arrays.asList(new ChatResponse("Hi there!", null, "stop"))),
                new ToolRegistry(Collections.<Tool>emptyList()), 5);
        MailProcessingService first = new MailProcessingService(channel, new SeenStore(file), loop);
        first.processCycle();

        MailProcessingService afterRestart = new MailProcessingService(channel, new SeenStore(file), loop);
        afterRestart.processCycle();

        assertEquals(1, channel.replies.size());
    }

    @Test
    public void llmFailureDoesNotSendReplyAndUnmarksMessageForRetry() throws Exception {
        MockMailChannel channel = new MockMailChannel(Arrays.asList(msg1));
        SeenStore seenStore = new SeenStore(seenFile());
        MockLlmClient llm = new MockLlmClient(Collections.<ChatResponse>emptyList());
        llm.throwOnNextCall(new LlmException("simulated LLM outage"));
        AgentToolLoop loop = new AgentToolLoop(llm, new ToolRegistry(Collections.<Tool>emptyList()), 5);
        MailProcessingService service = new MailProcessingService(channel, seenStore, loop);

        service.processCycle();

        assertTrue(channel.getSentReplies().isEmpty());
        assertTrue(seenStore.markIfNew(msg1.getId()));
    }

    @Test
    public void mailChannelFetchFailureDoesNotThrowAndNextCycleWorksNormally() throws Exception {
        MockMailChannel channel = new MockMailChannel(Arrays.asList(msg1));
        channel.setThrowOnFetch(true);
        SeenStore seenStore = new SeenStore(seenFile());
        AgentToolLoop loop = new AgentToolLoop(
                new MockLlmClient(Arrays.asList(new ChatResponse("Hi there!", null, "stop"))),
                new ToolRegistry(Collections.<Tool>emptyList()), 5);
        MailProcessingService service = new MailProcessingService(channel, seenStore, loop);

        service.processCycle();
        assertTrue(channel.getSentReplies().isEmpty());

        channel.setThrowOnFetch(false);
        service.processCycle();

        assertEquals(1, channel.getSentReplies().size());
    }

    @Test
    public void replyFailureForOneMessageDoesNotStopProcessingOfOthers() throws Exception {
        FailOnSecondReplyMailChannel channel = new FailOnSecondReplyMailChannel(msg1, msg2);
        SeenStore seenStore = new SeenStore(seenFile());
        AgentToolLoop loop = new AgentToolLoop(
                new MockLlmClient(Arrays.asList(
                        new ChatResponse("Reply 1", null, "stop"),
                        new ChatResponse("Reply 2", null, "stop"))),
                new ToolRegistry(Collections.<Tool>emptyList()), 5);
        MailProcessingService service = new MailProcessingService(channel, seenStore, loop);

        service.processCycle();

        assertEquals(1, channel.replies.size());
        assertEquals(msg1, channel.replies.get(0).getOriginal());
        assertTrue(seenStore.markIfNew(msg2.getId()));
    }

    @Test
    public void keyEventsAreRecordedInAuditLogWhenAuditLogProvided() throws Exception {
        MockMailChannel channel = new MockMailChannel(Arrays.asList(msg1));
        SeenStore seenStore = new SeenStore(seenFile());
        AgentToolLoop loop = new AgentToolLoop(
                new MockLlmClient(Arrays.asList(new ChatResponse("Hi there!", null, "stop"))),
                new ToolRegistry(Collections.<Tool>emptyList()), 5);
        AuditLog auditLog = new AuditLog(auditFile());
        MailProcessingService service = new MailProcessingService(channel, seenStore, loop, auditLog);

        service.processCycle();

        assertTrue(auditLog.verify());
        List<String> lines = Files.readAllLines(auditFile(), StandardCharsets.UTF_8);
        assertTrue(containsEventKey(lines, LogEvents.AGENT_MAIL_SEEN));
        assertTrue(containsEventKey(lines, LogEvents.AGENT_REPLY_SENT));
    }

    private Path seenFile() {
        return tmp.getRoot().toPath().resolve("seen.json");
    }

    private Path auditFile() {
        return tmp.getRoot().toPath().resolve("audit.jsonl");
    }

    private boolean containsEventKey(List<String> lines, String eventKey) {
        for (String line : lines) {
            if (line.contains("\"eventKey\":\"" + eventKey + "\"")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Test-only stub simulating an Outlook sync glitch: always reports the same
     * message as unread, regardless of prior reply() calls.
     */
    private static class RepeatingMailChannel implements MailChannel {
        private final Msg fixedMessage;
        private final List<MockMailChannel.SentReply> replies = new ArrayList<>();

        RepeatingMailChannel(Msg fixedMessage) {
            this.fixedMessage = fixedMessage;
        }

        @Override
        public List<Msg> fetchUnread() {
            return Arrays.asList(fixedMessage);
        }

        @Override
        public void reply(Msg original, String body) throws MailChannelException {
            replies.add(new MockMailChannel.SentReply(original, body));
        }
    }

    /**
     * Test-only stub: fetches both messages but fails reply() for the second one only,
     * to verify one failing message doesn't stop processing of the rest of the batch.
     */
    private static class FailOnSecondReplyMailChannel implements MailChannel {
        private final Msg first;
        private final Msg second;
        private final List<MockMailChannel.SentReply> replies = new ArrayList<>();

        FailOnSecondReplyMailChannel(Msg first, Msg second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public List<Msg> fetchUnread() {
            return Arrays.asList(first, second);
        }

        @Override
        public void reply(Msg original, String body) throws MailChannelException {
            if (original.equals(second)) {
                throw new MailChannelException("simulated reply failure for second message");
            }
            replies.add(new MockMailChannel.SentReply(original, body));
        }
    }
}
