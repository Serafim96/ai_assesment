package ru.assistant.tools;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.Instant;
import java.util.Map;

public class AddReminderTool implements Tool {

    private final ReminderStore store;

    public AddReminderTool(ReminderStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "add_reminder";
    }

    @Override
    public String description() {
        return "Adds a reminder with the given text and due date/time (ISO-8601).";
    }

    @Override
    public String jsonSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"text\":{\"type\":\"string\"},"
                + "\"dueIso\":{\"type\":\"string\"}"
                + "},\"required\":[\"text\",\"dueIso\"]}";
    }

    @Override
    public String execute(Map<String, Object> args) throws ToolError {
        String text = stringArg(args, "text");
        if (text == null || text.trim().isEmpty()) {
            throw new ToolError("text is required and must not be blank");
        }
        String dueIso = stringArg(args, "dueIso");
        validateDueIso(dueIso);

        ReminderStore.ReminderEntry entry = store.add(text.trim(), dueIso);
        return "Reminder added: \"" + entry.getText() + "\", due " + entry.getDueIso();
    }

    private void validateDueIso(String dueIso) throws ToolError {
        if (dueIso == null || dueIso.trim().isEmpty()) {
            throw new ToolError("dueIso is required and must be a valid ISO-8601 date-time");
        }
        try {
            Instant.parse(dueIso);
            return;
        } catch (DateTimeParseException e) {
            // fall through to try OffsetDateTime
        }
        try {
            OffsetDateTime.parse(dueIso);
        } catch (DateTimeParseException e) {
            throw new ToolError("dueIso must be a valid ISO-8601 date-time");
        }
    }

    private String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : value.toString();
    }
}
