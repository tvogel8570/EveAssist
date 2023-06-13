package com.eveassist.api.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import org.hibernate.validator.constraints.Length;

/**
 * A Projection for the {@link com.eveassist.api.user.entity.EveAssistUser} entity
 */
@Builder
public record EveAssistUserDto(
        @Length(min = 30, max = 30)
        String uniqueUser,
        @NotEmpty
        @Email(message = "You must enter a valid email")
        String email,
        @NotEmpty
        String screenName,
        boolean loginOk) {
}