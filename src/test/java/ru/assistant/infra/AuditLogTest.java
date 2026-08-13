package ru.assistant.infra;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AuditLogTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void verifyReturnsTrueForMissingFile() {
        AuditLog log = new AuditLog(auditFile());

        assertTrue(log.verify());
    }

    @Test
    public void verifyReturnsTrueAfterSeveralAppends() {
        AuditLog log = new AuditLog(auditFile());

        log.append(LogEvents.AGENT_MAIL_SEEN, "{\"id\":\"1\"}");
        log.append(LogEvents.AGENT_TOOL_CALL, "{\"tool\":\"current_datetime\"}");
        log.append(LogEvents.AGENT_REPLY_SENT, "{\"id\":\"1\"}");

        assertTrue(log.verify());
    }

    @Test
    public void appendPersistsAcrossNewInstanceOnSameFile() {
        Path file = auditFile();
        AuditLog first = new AuditLog(file);
        first.append(LogEvents.AGENT_MAIL_SEEN, "{\"id\":\"1\"}");

        AuditLog second = new AuditLog(file);
        second.append(LogEvents.AGENT_TOOL_CALL, "{\"tool\":\"current_datetime\"}");

        assertTrue(second.verify());
    }

    @Test
    public void verifyDetectsTamperingAndReportsFirstCorruptSeq() throws IOException {
        Path file = auditFile();
        AuditLog log = new AuditLog(file);
        log.append(LogEvents.AGENT_MAIL_SEEN, "{\"id\":\"1\"}");
        log.append(LogEvents.AGENT_TOOL_CALL, "{\"tool\":\"current_datetime\"}");
        log.append(LogEvents.AGENT_REPLY_SENT, "{\"id\":\"1\"}");

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        lines.set(1, lines.get(1).replace("agent_tool_call", "agent_tool_call_TAMPERED"));
        Files.write(file, lines, StandardCharsets.UTF_8);

        AuditLog reopened = new AuditLog(file);
        assertEquals(false, reopened.verify());
        assertEquals(2, reopened.verifyAndGetFirstCorruptSeq());
    }

    private Path auditFile() {
        return tmp.getRoot().toPath().resolve("audit.jsonl");
    }
}
