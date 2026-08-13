package ru.assistant.tools;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.assistant.infra.AtomicJsonFileStore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ReminderStore {

    private final AtomicJsonFileStore<List<ReminderEntry>> store;

    public ReminderStore(Path file) {
        ObjectMapper mapper = new ObjectMapper();
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, ReminderEntry.class);
        this.store = new AtomicJsonFileStore<>(file, type, mapper);
    }

    public synchronized ReminderEntry add(String text, String dueIso) {
        List<ReminderEntry> current = new ArrayList<>(store.load(new ArrayList<ReminderEntry>()));
        ReminderEntry entry = new ReminderEntry(UUID.randomUUID().toString(), text, dueIso);
        current.add(entry);
        store.save(current);
        return entry;
    }

    public synchronized List<ReminderEntry> find(String query) {
        List<ReminderEntry> current = store.load(new ArrayList<ReminderEntry>());
        if (query == null || query.isEmpty()) {
            return new ArrayList<>(current);
        }
        String needle = query.toLowerCase(Locale.ROOT);
        List<ReminderEntry> result = new ArrayList<>();
        for (ReminderEntry entry : current) {
            if (entry.getText() != null && entry.getText().toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(entry);
            }
        }
        return result;
    }

    public static class ReminderEntry {

        private String id;
        private String text;
        private String dueIso;

        public ReminderEntry() {
        }

        public ReminderEntry(String id, String text, String dueIso) {
            this.id = id;
            this.text = text;
            this.dueIso = dueIso;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getDueIso() {
            return dueIso;
        }

        public void setDueIso(String dueIso) {
            this.dueIso = dueIso;
        }
    }
}
