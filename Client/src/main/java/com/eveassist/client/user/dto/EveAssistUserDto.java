package com.eveassist.client.user.dto;

import com.eveassist.client.user.entity.EveAssistUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;

import java.util.UUID;

/**
 * A Projection for the {@link EveAssistUser} entity
 */

public record EveAssistUserDto(
        long id,
        @NotEmpty
        UUID uniqueUser,
        @NotEmpty
        @Email(message = "You must enter a valid email")
        String email,
        @NotEmpty
        String userName) {

    @SuppressWarnings("unused")
    public static final class EveAssistUserDtoBuilder {
        private long id;
        private @NotEmpty UUID uniqueUser;
        private @NotEmpty
        @Email(message = "You must enter a valid email") String email;
        private @NotEmpty String userName;

        private EveAssistUserDtoBuilder() {
        }

        public static EveAssistUserDtoBuilder anEveAssistUserDto() {
            return new EveAssistUserDtoBuilder();
        }

        public EveAssistUserDtoBuilder withId(long id) {
            this.id = id;
            return this;
        }

        public EveAssistUserDtoBuilder withUniqueUser(UUID uniqueUser) {
            this.uniqueUser = uniqueUser;
            return this;
        }

        public EveAssistUserDtoBuilder withEmail(String email) {
            this.email = email;
            return this;
        }

        public EveAssistUserDtoBuilder withUserName(String userName) {
            this.userName = userName;
            return this;
        }

        public EveAssistUserDto build() {
            return new EveAssistUserDto(id, uniqueUser, email, userName);
        }
    }
}