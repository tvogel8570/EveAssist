package com.eveassist.client.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.Serial;

@Slf4j
@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Bad value from ESI")
public class EsiParsingException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = -623149789441929967L;

    public EsiParsingException(JsonProcessingException originalEx, String methodName, String methodSpecificMsg) {
        log.warn("Error parsing ESI return value in [{}] for [{}] [{}]", methodName,
                methodSpecificMsg, originalEx.getMessage());
    }
}
