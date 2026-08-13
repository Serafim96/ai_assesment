package ru.assistant.agent;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SeenStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void markIfNewReturnsTrueOnceThenFalseForSameId() throws IOException {
        SeenStore store = new SeenStore(seenFile());

        assertTrue(store.markIfNew("msg-1"));
        assertFalse(store.markIfNew("msg-1"));
    }

    @Test
    public void markIfNewSurvivesRestartWithNewInstanceOnSameFile() throws IOException {
        Path file = seenFile();
        SeenStore first = new SeenStore(file);
        first.markIfNew("msg-1");

        SeenStore afterRestart = new SeenStore(file);

        assertFalse(afterRestart.markIfNew("msg-1"));
    }

    @Test
    public void corruptedFileStartsWithEmptySeenSetInsteadOfThrowing() throws IOException {
        Path file = seenFile();
        Files.write(file, "{ not valid json".getBytes(StandardCharsets.UTF_8));

        SeenStore store = new SeenStore(file);

        assertTrue(store.markIfNew("msg-1"));
    }

    @Test
    public void unmarkAllowsIdToBeMarkedAsNewAgain() throws IOException {
        SeenStore store = new SeenStore(seenFile());
        store.markIfNew("msg-1");

        store.unmark("msg-1");

        assertTrue(store.markIfNew("msg-1"));
    }

    private Path seenFile() {
        return tmp.getRoot().toPath().resolve("seen.json");
    }
}
