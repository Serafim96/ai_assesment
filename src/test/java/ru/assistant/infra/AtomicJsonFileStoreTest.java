package ru.assistant.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AtomicJsonFileStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void savedValueIsVisibleToNewInstanceOfSameFile() throws IOException {
        Path file = tmp.getRoot().toPath().resolve("store.json");
        AtomicJsonFileStore<List<String>> storeA = newStore(file);

        storeA.save(Arrays.asList("a", "b", "c"));

        AtomicJsonFileStore<List<String>> storeB = newStore(file);
        List<String> loaded = storeB.load(new ArrayList<>());

        assertEquals(Arrays.asList("a", "b", "c"), loaded);
    }

    @Test
    public void missingFileReturnsProvidedDefault() throws IOException {
        Path file = tmp.getRoot().toPath().resolve("does-not-exist.json");
        AtomicJsonFileStore<List<String>> store = newStore(file);

        List<String> loaded = store.load(new ArrayList<>());

        assertEquals(new ArrayList<>(), loaded);
    }

    @Test
    public void corruptedFileReturnsDefaultInsteadOfThrowing() throws IOException {
        Path file = tmp.getRoot().toPath().resolve("corrupt.json");
        Files.write(file, "{ not valid json ][".getBytes(StandardCharsets.UTF_8));
        AtomicJsonFileStore<List<String>> store = newStore(file);

        List<String> loaded = store.load(new ArrayList<>());

        assertEquals(new ArrayList<>(), loaded);
    }

    @Test
    public void saveCreatesParentDirectoriesIfMissing() throws IOException {
        Path file = tmp.getRoot().toPath().resolve("nested/dir/store.json");
        AtomicJsonFileStore<List<String>> store = newStore(file);

        store.save(Arrays.asList("x"));

        assertTrue(Files.exists(file));
    }

    @Test
    public void targetFileNeverLeftWithTempSuffixAfterSave() throws IOException {
        Path file = tmp.getRoot().toPath().resolve("store2.json");
        AtomicJsonFileStore<List<String>> store = newStore(file);

        store.save(Arrays.asList("y"));

        try (java.util.stream.Stream<Path> files = Files.list(tmp.getRoot().toPath())) {
            boolean anyTempLeftover = files.anyMatch(p -> p.getFileName().toString().contains(".tmp"));
            assertTrue(!anyTempLeftover);
        }
    }

    @SuppressWarnings("unchecked")
    private AtomicJsonFileStore<List<String>> newStore(Path file) {
        return new AtomicJsonFileStore<>(file, mapper.getTypeFactory().constructCollectionType(List.class, String.class), mapper);
    }
}
