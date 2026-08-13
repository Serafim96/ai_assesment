package ru.assistant.agent;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import ru.assistant.llm.ChatResponse;
import ru.assistant.llm.MockLlmClient;
import ru.assistant.mail.MailChannel;
import ru.assistant.mail.MailChannelException;
import ru.assistant.mail.MockMailChannel;
import ru.assistant.mail.Msg;
import ru.assistant.tools.Tool;
import ru.assistant.tools.ToolRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class MailProcessingServiceTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final Msg msg1 = new Msg("id-1", "alice@example.com", "Subj", "Hello", "2026-08-13T10:00:00Z");

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

    private Path seenFile() {
        return tmp.getRoot().toPath().resolve("seen.json");
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
}
