package com.eveassist.api.esi.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.io.Serializable;
import java.time.LocalDateTime;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public record CharactersNotificationsDto(
        Boolean isRead,
        Integer notificationId,
        Integer senderId,
        String senderType,
        String text,
        LocalDateTime timestamp,
        String type
) implements Serializable {
}
