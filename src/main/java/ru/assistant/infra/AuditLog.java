package ru.assistant.infra;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class AuditLog {

    private static final String GENESIS_HASH = repeat('0', 64);

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuditLog(Path file) {
        this.file = file;
    }

    public synchronized void append(String eventKey, String detailsJson) {
        AuditRecord previous = readLastRecord();
        long seq = previous == null ? 1 : previous.seq + 1;
        String prevHash = previous == null ? GENESIS_HASH : previous.hash;
        String hash = computeHash(seq, eventKey, detailsJson, prevHash);

        AuditRecord record = new AuditRecord();
        record.seq = seq;
        record.eventKey = eventKey;
        record.detailsJson = detailsJson;
        record.prevHash = prevHash;
        record.hash = hash;

        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String line = mapper.writeValueAsString(record) + System.lineSeparator();
            Files.write(file, line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to append audit record", e);
        }
    }

    public boolean verify() {
        return verifyAndGetFirstCorruptSeq() == -1;
    }

    public long verifyAndGetFirstCorruptSeq() {
        List<AuditRecord> records = readAllRecords();
        String expectedPrevHash = GENESIS_HASH;
        for (AuditRecord record : records) {
            String expectedHash = computeHash(record.seq, record.eventKey, record.detailsJson, expectedPrevHash);
            if (!expectedPrevHash.equals(record.prevHash) || !expectedHash.equals(record.hash)) {
                return record.seq;
            }
            expectedPrevHash = record.hash;
        }
        return -1;
    }

    private String computeHash(long seq, String eventKey, String detailsJson, String prevHash) {
        return Sha256.hex(seq + "|" + eventKey + "|" + detailsJson + "|" + prevHash);
    }

    private AuditRecord readLastRecord() {
        List<AuditRecord> records = readAllRecords();
        return records.isEmpty() ? null : records.get(records.size() - 1);
    }

    private List<AuditRecord> readAllRecords() {
        if (!Files.exists(file)) {
            return java.util.Collections.emptyList();
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<AuditRecord> records = new java.util.ArrayList<>();
            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                records.add(mapper.readValue(line, AuditRecord.class));
            }
            return records;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read audit log", e);
        }
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    static class AuditRecord {
        public long seq;
        public String eventKey;
        public String detailsJson;
        public String prevHash;
        public String hash;
    }
}
