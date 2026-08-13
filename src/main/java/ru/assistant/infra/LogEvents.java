package ru.assistant.infra;

public class LogEvents {

    public static final String AGENT_MAIL_SEEN = "agent_mail_seen";
    public static final String AGENT_TOOL_CALL = "agent_tool_call";
    public static final String AGENT_REPLY_SENT = "agent_reply_sent";
    public static final String LLM_FAILED = "llm_failed";
    public static final String MAIL_CHANNEL_ERROR = "mail_channel_error";
    public static final String AGENT_MAXSTEPS_EXCEEDED = "agent_maxsteps_exceeded";

    private LogEvents() {
    }

    public static String safePreview(String text) {
        if (text == null) {
            return "<null>";
        }
        String hash = Sha256.hex(text);
        return "len=" + text.length() + " sha256=" + hash.substring(0, 12);
    }
}
