package ru.assistant.agent;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.assistant.infra.AtomicJsonFileStore;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public class SeenStore {

    private final AtomicJsonFileStore<Set<String>> store;
    private final Set<String> seen;

    public SeenStore(Path file) {
        ObjectMapper mapper = new ObjectMapper();
        JavaType type = mapper.getTypeFactory().constructCollectionType(Set.class, String.class);
        this.store = new AtomicJsonFileStore<>(file, type, mapper);
        this.seen = new LinkedHashSet<>(store.load(new LinkedHashSet<String>()));
    }

    public synchronized boolean markIfNew(String id) {
        if (seen.contains(id)) {
            return false;
        }
        seen.add(id);
        store.save(seen);
        return true;
    }

    public synchronized void unmark(String id) {
        if (seen.remove(id)) {
            store.save(seen);
        }
    }
}
