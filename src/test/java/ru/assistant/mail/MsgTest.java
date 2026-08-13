package ru.assistant.mail;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class MsgTest {

    @Test
    public void exposesAllFieldsThroughGetters() {
        Msg msg = new Msg("id-1", "sender@example.com", "Subject", "Body text", "2026-08-13T10:00:00Z");

        assertEquals("id-1", msg.getId());
        assertEquals("sender@example.com", msg.getFrom());
        assertEquals("Subject", msg.getSubject());
        assertEquals("Body text", msg.getBody());
        assertEquals("2026-08-13T10:00:00Z", msg.getReceivedAtIso());
    }

    @Test
    public void equalsAndHashCodeAreBasedOnIdOnly() {
        Msg a = new Msg("same-id", "a@x.com", "Subj A", "Body A", "2026-08-13T10:00:00Z");
        Msg b = new Msg("same-id", "b@y.com", "Subj B", "Body B", "2026-08-14T11:00:00Z");
        Msg c = new Msg("different-id", "a@x.com", "Subj A", "Body A", "2026-08-13T10:00:00Z");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
