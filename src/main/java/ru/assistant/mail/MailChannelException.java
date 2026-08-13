package ru.assistant.mail;

public class MailChannelException extends Exception {

    public MailChannelException(String message) {
        super(message);
    }

    public MailChannelException(String message, Throwable cause) {
        super(message, cause);
    }
}
