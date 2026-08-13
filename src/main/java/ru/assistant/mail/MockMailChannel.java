package ru.assistant.mail;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MockMailChannel implements MailChannel {

    private final List<Msg> allMessages;
    private final Set<String> readIds = new LinkedHashSet<>();
    private final List<SentReply> sentReplies = new ArrayList<>();

    private boolean throwOnFetch = false;
    private boolean throwOnReply = false;

    public MockMailChannel(List<Msg> allMessages) {
        this.allMessages = new ArrayList<>(allMessages);
    }

    @Override
    public List<Msg> fetchUnread() throws MailChannelException {
        if (throwOnFetch) {
            throw new MailChannelException("Mock configured to fail on fetchUnread");
        }
        List<Msg> unread = new ArrayList<>();
        for (Msg msg : allMessages) {
            if (!readIds.contains(msg.getId())) {
                unread.add(msg);
            }
        }
        return unread;
    }

    @Override
    public void reply(Msg original, String body) throws MailChannelException {
        if (throwOnReply) {
            throw new MailChannelException("Mock configured to fail on reply");
        }
        sentReplies.add(new SentReply(original, body));
        readIds.add(original.getId());
    }

    public List<SentReply> getSentReplies() {
        return sentReplies;
    }

    public void setThrowOnFetch(boolean throwOnFetch) {
        this.throwOnFetch = throwOnFetch;
    }

    public void setThrowOnReply(boolean throwOnReply) {
        this.throwOnReply = throwOnReply;
    }

    public static class SentReply {
        private final Msg original;
        private final String body;

        public SentReply(Msg original, String body) {
            this.original = original;
            this.body = body;
        }

        public Msg getOriginal() {
            return original;
        }

        public String getBody() {
            return body;
        }
    }
}
