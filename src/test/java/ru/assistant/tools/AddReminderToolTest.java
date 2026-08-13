package ru.assistant.tools;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AddReminderToolTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void nameIsAddReminder() {
        AddReminderTool tool = new AddReminderTool(newStore());

        assertEquals("add_reminder", tool.name());
    }

    @Test
    public void executeAddsReminderAndReturnsConfirmationWithDueDate() throws ToolError, IOException {
        ReminderStore store = newStore();
        AddReminderTool tool = new AddReminderTool(store);
        Map<String, Object> args = new HashMap<>();
        args.put("text", "call Ivan");
        args.put("dueIso", "2026-08-14T09:00:00Z");

        String result = tool.execute(args);

        assertTrue(result.contains("2026-08-14T09:00:00Z"));
        List<ReminderStore.ReminderEntry> found = store.find("call Ivan");
        assertEquals(1, found.size());
    }

    @Test
    public void executeAcceptsOffsetDateTimeDueIso() throws ToolError, IOException {
        AddReminderTool tool = new AddReminderTool(newStore());
        Map<String, Object> args = new HashMap<>();
        args.put("text", "call Ivan");
        args.put("dueIso", "2026-08-14T09:00:00+03:00");

        String result = tool.execute(args);

        assertTrue(result.contains("2026-08-14T09:00:00+03:00"));
    }

    @Test
    public void executeRejectsBlankText() throws IOException {
        AddReminderTool tool = new AddReminderTool(newStore());
        Map<String, Object> args = new HashMap<>();
        args.put("text", "   ");
        args.put("dueIso", "2026-08-14T09:00:00Z");

        try {
            tool.execute(args);
            fail("expected ToolError");
        } catch (ToolError expected) {
            assertTrue(expected.getMessage() != null && !expected.getMessage().isEmpty());
        }
    }

    @Test
    public void executeRejectsUnparsableDueIso() throws IOException {
        AddReminderTool tool = new AddReminderTool(newStore());
        Map<String, Object> args = new HashMap<>();
        args.put("text", "call Ivan");
        args.put("dueIso", "not-a-date");

        try {
            tool.execute(args);
            fail("expected ToolError");
        } catch (ToolError expected) {
            assertTrue(expected.getMessage() != null && !expected.getMessage().isEmpty());
        }
    }

    private ReminderStore newStore() {
        try {
            return new ReminderStore(tmp.getRoot().toPath().resolve("reminders.json"));
        } catch (RuntimeException e) {
            throw e;
        }
    }
}
