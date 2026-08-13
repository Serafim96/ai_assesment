package ru.assistant.infra;

public class StoreIOException extends RuntimeException {

    public StoreIOException(String message, Throwable cause) {
        super(message, cause);
    }
}
