package com.eveassist.api.esi.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.Serial;

@Slf4j
@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Bad parameter sent to ESI")
public class EsiParameterException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = -2964326183870381222L;

    public EsiParameterException(String msg) {
        log.warn("ESI parameter error [{}]", msg);
    }
}
