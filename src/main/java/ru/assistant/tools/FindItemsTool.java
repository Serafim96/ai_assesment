package ru.assistant.tools;

import java.util.List;
import java.util.Map;

public class FindItemsTool implements Tool {

    private final ReminderStore store;

    public FindItemsTool(ReminderStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "find_items";
    }

    @Override
    public String description() {
        return "Finds reminders whose text contains the given query (case-insensitive); empty query returns all.";
    }

    @Override
    public String jsonSchema() {
        return "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}";
    }

    @Override
    public String execute(Map<String, Object> args) throws ToolError {
        Object queryArg = args.get("query");
        String query = queryArg == null ? null : queryArg.toString();

        List<ReminderStore.ReminderEntry> found = store.find(query);
        if (found.isEmpty()) {
            return "No reminders found.";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < found.size(); i++) {
            ReminderStore.ReminderEntry entry = found.get(i);
            if (i > 0) {
                sb.append('\n');
            }
            sb.append("- ").append(entry.getText()).append(" (due ").append(entry.getDueIso()).append(")");
        }
        return sb.toString();
    }
}
