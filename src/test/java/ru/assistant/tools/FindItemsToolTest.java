package ru.assistant.tools;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FindItemsToolTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void nameIsFindItems() {
        FindItemsTool tool = new FindItemsTool(newStore());

        assertEquals("find_items", tool.name());
    }

    @Test
    public void executeReturnsNoRemindersFoundMessageWhenEmpty() throws ToolError {
        FindItemsTool tool = new FindItemsTool(newStore());
        Map<String, Object> args = new HashMap<>();
        args.put("query", "");

        String result = tool.execute(args);

        assertEquals("No reminders found.", result);
    }

    @Test
    public void executeFormatsMatchingRemindersAsLines() throws ToolError {
        ReminderStore store = newStore();
        store.add("call Ivan", "2026-08-14T09:00:00Z");
        store.add("buy milk", "2026-08-15T09:00:00Z");
        FindItemsTool tool = new FindItemsTool(store);
        Map<String, Object> args = new HashMap<>();
        args.put("query", "ivan");

        String result = tool.execute(args);

        assertTrue(result.contains("call Ivan"));
        assertTrue(result.contains("2026-08-14T09:00:00Z"));
        assertTrue(!result.contains("buy milk"));
    }

    @Test
    public void executeWithMissingQueryReturnsAllReminders() throws ToolError {
        ReminderStore store = newStore();
        store.add("call Ivan", "2026-08-14T09:00:00Z");
        store.add("buy milk", "2026-08-15T09:00:00Z");
        FindItemsTool tool = new FindItemsTool(store);

        String result = tool.execute(new HashMap<String, Object>());

        assertTrue(result.contains("call Ivan"));
        assertTrue(result.contains("buy milk"));
    }

    private ReminderStore newStore() {
        try {
            return new ReminderStore(tmp.getRoot().toPath().resolve("reminders.json"));
        } catch (RuntimeException e) {
            throw e;
        }
    }
}
