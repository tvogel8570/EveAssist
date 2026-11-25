package com.eveassist.industry.internal.application;

/** A stored document is in a format this version of the application has no reader for, or is not valid JSON of that format. */
public class UnreadableDocumentException extends RuntimeException {

    public UnreadableDocumentException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnreadableDocumentException(String message) {
        super(message);
    }
}
