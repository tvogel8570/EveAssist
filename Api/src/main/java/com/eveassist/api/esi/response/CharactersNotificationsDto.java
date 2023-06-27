package com.eveassist.api.esi.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.io.Serializable;
import java.time.LocalDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
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
