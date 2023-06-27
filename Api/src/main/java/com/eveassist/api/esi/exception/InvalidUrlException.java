package com.eveassist.api.esi.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.Serial;
import java.net.MalformedURLException;

@Slf4j
@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Bad URL")
public class InvalidUrlException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 8605078760604767117L;

    public InvalidUrlException(MalformedURLException originalEx, String methodName, String methodSpecificMsg) {
        log.warn("Exception in {} with URL [{}] [{}]", methodName, methodSpecificMsg, originalEx.getMessage());
    }
}
