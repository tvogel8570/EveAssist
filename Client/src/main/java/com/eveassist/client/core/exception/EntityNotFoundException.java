package com.eveassist.client.core.exception;

import java.io.Serial;

public class EntityNotFoundException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = -4414263788588178448L;

    public EntityNotFoundException(String message) {
        super(message);
    }
}
