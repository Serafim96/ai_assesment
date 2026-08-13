package ru.assistant.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.assistant.mail.MailChannel;
import ru.assistant.mail.MailChannelException;
import ru.assistant.mail.Msg;

import java.util.List;

public class MailProcessingService {

    private static final Logger log = LoggerFactory.getLogger(MailProcessingService.class);

    private final MailChannel mailChannel;
    private final SeenStore seenStore;
    private final AgentToolLoop toolLoop;

    public MailProcessingService(MailChannel mailChannel, SeenStore seenStore, AgentToolLoop toolLoop) {
        this.mailChannel = mailChannel;
        this.seenStore = seenStore;
        this.toolLoop = toolLoop;
    }

    public void processCycle() {
        List<Msg> unread;
        try {
            unread = mailChannel.fetchUnread();
        } catch (MailChannelException e) {
            log.warn("mail_channel_error: failed to fetch unread messages", e);
            return;
        }

        for (Msg msg : unread) {
            if (!seenStore.markIfNew(msg.getId())) {
                continue;
            }
            try {
                String reply = toolLoop.run(msg.getBody());
                mailChannel.reply(msg, reply);
            } catch (Exception e) {
                seenStore.unmark(msg.getId());
                log.warn("Failed to process message, will retry on next cycle", e);
            }
        }
    }
}
