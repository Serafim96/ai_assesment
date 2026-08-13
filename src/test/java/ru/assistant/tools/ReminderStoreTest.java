package ru.assistant.tools;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class ReminderStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void addReturnsEntryWithNonEmptyId() throws IOException {
        ReminderStore store = new ReminderStore(reminderFile());

        ReminderStore.ReminderEntry entry = store.add("buy milk", "2026-08-14T09:00:00Z");

        assertNotNull(entry.getId());
        assertFalse(entry.getId().isEmpty());
        assertEquals("buy milk", entry.getText());
        assertEquals("2026-08-14T09:00:00Z", entry.getDueIso());
    }

    @Test
    public void addPersistsAcrossNewInstanceOnSameFile() throws IOException {
        Path file = reminderFile();
        ReminderStore first = new ReminderStore(file);
        first.add("buy milk", "2026-08-14T09:00:00Z");

        ReminderStore afterRestart = new ReminderStore(file);
        List<ReminderStore.ReminderEntry> all = afterRestart.find(null);

        assertEquals(1, all.size());
        assertEquals("buy milk", all.get(0).getText());
    }

    @Test
    public void findIsCaseInsensitiveSubstringMatch() throws IOException {
        ReminderStore store = new ReminderStore(reminderFile());
        store.add("Buy Milk", "2026-08-14T09:00:00Z");
        store.add("Call mom", "2026-08-15T09:00:00Z");

        List<ReminderStore.ReminderEntry> found = store.find("milk");

        assertEquals(1, found.size());
        assertEquals("Buy Milk", found.get(0).getText());
    }

    @Test
    public void findWithEmptyOrNullQueryReturnsAllEntries() throws IOException {
        ReminderStore store = new ReminderStore(reminderFile());
        store.add("Buy Milk", "2026-08-14T09:00:00Z");
        store.add("Call mom", "2026-08-15T09:00:00Z");

        assertEquals(2, store.find("").size());
        assertEquals(2, store.find(null).size());
    }

    private Path reminderFile() {
        return tmp.getRoot().toPath().resolve("reminders.json");
    }
}
