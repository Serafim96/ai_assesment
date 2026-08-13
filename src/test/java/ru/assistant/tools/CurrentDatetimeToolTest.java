package ru.assistant.tools;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class CurrentDatetimeToolTest {

    @Test
    public void executeReturnsFixedClockInstantAsIsoString() throws ToolError {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-08-13T10:00:00Z"), ZoneOffset.UTC);
        CurrentDatetimeTool tool = new CurrentDatetimeTool(fixedClock);

        String result = tool.execute(Collections.<String, Object>emptyMap());

        assertEquals("2026-08-13T10:00:00Z", result);
    }

    @Test
    public void nameIsCurrentDatetime() {
        CurrentDatetimeTool tool = new CurrentDatetimeTool(Clock.systemUTC());

        assertEquals("current_datetime", tool.name());
    }
}
