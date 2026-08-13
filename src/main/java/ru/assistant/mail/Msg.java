package ru.assistant.mail;

import java.util.Objects;

public class Msg {

    private final String id;
    private final String from;
    private final String subject;
    private final String body;
    private final String receivedAtIso;

    public Msg(String id, String from, String subject, String body, String receivedAtIso) {
        this.id = id;
        this.from = from;
        this.subject = subject;
        this.body = body;
        this.receivedAtIso = receivedAtIso;
    }

    public String getId() {
        return id;
    }

    public String getFrom() {
        return from;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public String getReceivedAtIso() {
        return receivedAtIso;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Msg)) {
            return false;
        }
        Msg other = (Msg) o;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
