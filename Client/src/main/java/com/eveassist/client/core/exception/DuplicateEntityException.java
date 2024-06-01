package com.eveassist.client.core.exception;

import java.io.Serial;

public class DuplicateEntityException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = -776449005972096364L;

    public DuplicateEntityException(String message) {
        super(message);
    }
}
