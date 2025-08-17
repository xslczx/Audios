package com.xslczx.audios;

public class AudioException extends RuntimeException {

    public AudioException(String message, Throwable cause) {
        super(message, cause);
    }

    public AudioException(String message) {
        this(message, null);
    }

    public String getMessageWithCause() {
        String message = getMessage();
        Throwable cause = getCause();
        return message + (cause != null ? ": " + cause.getMessage() : "");
    }
}
