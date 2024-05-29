package com.eveassist.client.core;

import java.io.Serial;

public class EaBusinessException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 5141909973266540822L;

    public EaBusinessException(String msg) {
        super(msg);
    }
}
