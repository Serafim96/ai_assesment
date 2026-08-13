package ru.assistant.tools;

/**
 * Checked exception whose message is safe to surface to the model or the end user.
 * Never wrap internal details (paths, stack traces, secrets) into the message.
 */
public class ToolError extends Exception {

    public ToolError(String message) {
        super(message);
    }

    public ToolError(String message, Throwable cause) {
        super(message, cause);
    }
}
