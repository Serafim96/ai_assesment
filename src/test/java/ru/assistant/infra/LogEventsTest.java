package ru.assistant.infra;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LogEventsTest {

    @Test
    public void safePreviewOfNullReturnsPlaceholder() {
        assertEquals("<null>", LogEvents.safePreview(null));
    }

    @Test
    public void safePreviewNeverContainsOriginalText() {
        String body = "secret meeting details about Ivan Ivanov, phone +7 900 123 45 67";

        String preview = LogEvents.safePreview(body);

        assertFalse(preview.contains(body));
        assertFalse(preview.contains("Ivan"));
        assertFalse(preview.contains("+7 900"));
    }

    @Test
    public void safePreviewContainsLengthAndShaPrefix() {
        String body = "hello world";

        String preview = LogEvents.safePreview(body);

        assertTrue(preview.contains("len=" + body.length()));
        assertTrue(preview.contains("sha256="));
    }

    @Test
    public void safePreviewIsDeterministicForSameInput() {
        String body = "same text every time";

        assertEquals(LogEvents.safePreview(body), LogEvents.safePreview(body));
    }

    @Test
    public void eventKeyConstantsAreDefined() {
        assertEquals("agent_mail_seen", LogEvents.AGENT_MAIL_SEEN);
        assertEquals("agent_tool_call", LogEvents.AGENT_TOOL_CALL);
        assertEquals("agent_reply_sent", LogEvents.AGENT_REPLY_SENT);
        assertEquals("llm_failed", LogEvents.LLM_FAILED);
        assertEquals("mail_channel_error", LogEvents.MAIL_CHANNEL_ERROR);
        assertEquals("agent_maxsteps_exceeded", LogEvents.AGENT_MAXSTEPS_EXCEEDED);
    }

    @Test
    public void jsonEscapeEscapesQuotesAndBackslashes() {
        assertEquals("say \\\"hi\\\" \\\\ ok", LogEvents.jsonEscape("say \"hi\" \\ ok"));
    }

    @Test
    public void jsonEscapeOfNullReturnsEmptyString() {
        assertEquals("", LogEvents.jsonEscape(null));
    }
}
