package ru.assistant.infra;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class AtomicJsonFileStore<T> {

    private static final Logger log = LoggerFactory.getLogger(AtomicJsonFileStore.class);

    private final Path target;
    private final JavaType type;
    private final ObjectMapper mapper;

    public AtomicJsonFileStore(Path target, JavaType type, ObjectMapper mapper) {
        this.target = target;
        this.type = type;
        this.mapper = mapper;
    }

    public T load(T defaultValue) {
        if (!Files.exists(target)) {
            return defaultValue;
        }
        try {
            return mapper.readValue(target.toFile(), type);
        } catch (IOException e) {
            log.warn("Failed to read store file {}, falling back to default", target.getFileName(), e);
            return defaultValue;
        }
    }

    public void save(T value) {
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempFile = Files.createTempFile(parent, "atomic-json-", ".tmp");
            try {
                mapper.writeValue(tempFile.toFile(), value);
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException e) {
            throw new StoreIOException("Failed to save store file: " + target.getFileName(), e);
        }
    }
}
