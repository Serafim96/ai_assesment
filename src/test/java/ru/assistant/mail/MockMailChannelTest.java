package ru.assistant.mail;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MockMailChannelTest {

    private final Msg msg1 = new Msg("id-1", "alice@example.com", "Subj 1", "Body 1", "2026-08-13T10:00:00Z");
    private final Msg msg2 = new Msg("id-2", "bob@example.com", "Subj 2", "Body 2", "2026-08-13T11:00:00Z");

    @Test
    public void fetchUnreadReturnsConfiguredMessages() throws MailChannelException {
        MockMailChannel channel = new MockMailChannel(Arrays.asList(msg1, msg2));

        List<Msg> unread = channel.fetchUnread();

        assertEquals(Arrays.asList(msg1, msg2), unread);
    }

    @Test
    public void replyRecordsSentReplyAndMarksMessageRead() throws MailChannelException {
        MockMailChannel channel = new MockMailChannel(Arrays.asList(msg1, msg2));

        channel.reply(msg1, "Reply body");

        assertEquals(1, channel.getSentReplies().size());
        assertEquals(msg1, channel.getSentReplies().get(0).getOriginal());
        assertEquals("Reply body", channel.getSentReplies().get(0).getBody());

        List<Msg> unreadAfterReply = channel.fetchUnread();
        assertEquals(Collections.singletonList(msg2), unreadAfterReply);
    }

    @Test
    public void messageWithoutReplyStaysInUnreadQueue() throws MailChannelException {
        MockMailChannel channel = new MockMailChannel(Arrays.asList(msg1, msg2));

        channel.fetchUnread();
        List<Msg> stillUnread = channel.fetchUnread();

        assertEquals(Arrays.asList(msg1, msg2), stillUnread);
    }

    @Test
    public void throwOnFetchCausesFetchUnreadToThrow() {
        MockMailChannel channel = new MockMailChannel(Arrays.asList(msg1));
        channel.setThrowOnFetch(true);

        try {
            channel.fetchUnread();
            fail("expected MailChannelException");
        } catch (MailChannelException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }

    @Test
    public void throwOnReplyCausesReplyToThrow() {
        MockMailChannel channel = new MockMailChannel(Arrays.asList(msg1));
        channel.setThrowOnReply(true);

        try {
            channel.reply(msg1, "text");
            fail("expected MailChannelException");
        } catch (MailChannelException expected) {
            assertTrue(expected.getMessage() != null);
        }
        assertTrue(channel.getSentReplies().isEmpty());
    }
}
