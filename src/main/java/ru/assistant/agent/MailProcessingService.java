package ru.assistant.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.assistant.infra.AuditLog;
import ru.assistant.infra.LogEvents;
import ru.assistant.llm.LlmException;
import ru.assistant.mail.MailChannel;
import ru.assistant.mail.MailChannelException;
import ru.assistant.mail.Msg;

import java.util.List;

public class MailProcessingService {

    private static final Logger log = LoggerFactory.getLogger(MailProcessingService.class);

    private final MailChannel mailChannel;
    private final SeenStore seenStore;
    private final AgentToolLoop toolLoop;
    private final AuditLog auditLog;

    public MailProcessingService(MailChannel mailChannel, SeenStore seenStore, AgentToolLoop toolLoop) {
        this(mailChannel, seenStore, toolLoop, null);
    }

    public MailProcessingService(MailChannel mailChannel, SeenStore seenStore, AgentToolLoop toolLoop, AuditLog auditLog) {
        this.mailChannel = mailChannel;
        this.seenStore = seenStore;
        this.toolLoop = toolLoop;
        this.auditLog = auditLog;
    }

    public void processCycle() {
        List<Msg> unread;
        try {
            unread = mailChannel.fetchUnread();
        } catch (MailChannelException e) {
            log.warn("mail_channel_error: failed to fetch unread messages", e);
            appendAudit(LogEvents.MAIL_CHANNEL_ERROR, "{\"stage\":\"fetchUnread\"}");
            return;
        }

        for (Msg msg : unread) {
            if (!seenStore.markIfNew(msg.getId())) {
                continue;
            }
            appendAudit(LogEvents.AGENT_MAIL_SEEN, "{\"messageId\":\"" + LogEvents.jsonEscape(msg.getId()) + "\"}");

            String reply;
            try {
                reply = toolLoop.run(msg.getBody());
            } catch (LlmException e) {
                seenStore.unmark(msg.getId());
                log.warn("llm_failed: could not obtain a reply from the LLM, will retry on next cycle", e);
                appendAudit(LogEvents.LLM_FAILED, "{\"messageId\":\"" + LogEvents.jsonEscape(msg.getId()) + "\"}");
                continue;
            }

            try {
                mailChannel.reply(msg, reply);
            } catch (MailChannelException e) {
                seenStore.unmark(msg.getId());
                log.warn("mail_channel_error: failed to send reply, will retry on next cycle", e);
                appendAudit(LogEvents.MAIL_CHANNEL_ERROR, "{\"stage\":\"reply\",\"messageId\":\""
                        + LogEvents.jsonEscape(msg.getId()) + "\"}");
                continue;
            }
            appendAudit(LogEvents.AGENT_REPLY_SENT, "{\"messageId\":\"" + LogEvents.jsonEscape(msg.getId()) + "\"}");
        }
    }

    private void appendAudit(String eventKey, String detailsJson) {
        if (auditLog != null) {
            auditLog.append(eventKey, detailsJson);
        }
    }
}
