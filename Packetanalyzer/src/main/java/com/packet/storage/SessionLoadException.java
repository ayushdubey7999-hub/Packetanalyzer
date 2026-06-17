package com.packet.storage;

/** Raised when a session file is missing, corrupt, or not a supported session format. */
public final class SessionLoadException extends Exception {

    public SessionLoadException(String message) {
        super(message);
    }

    public SessionLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
