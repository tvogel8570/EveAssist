package com.eveassist.client.user.dto;

import com.eveassist.client.user.entity.EveAssistUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;

import java.util.UUID;

/**
 * A Projection for the {@link EveAssistUser} entity
 */
@Builder
public record EveAssistUserListDto(
        @NotEmpty
        UUID uniqueUser,
        @NotEmpty
        @Email(message = "You must enter a valid email")
        String email,
        @NotEmpty
        String userName) {
}