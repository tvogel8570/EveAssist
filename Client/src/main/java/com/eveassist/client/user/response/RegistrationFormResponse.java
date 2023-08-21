package com.eveassist.client.user.response;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class RegistrationFormResponse {
    @NotEmpty
    @Email(message = "You must enter a valid email")
    String email;
    @NotEmpty
    String screenName;
    @NotEmpty
    String password;
    @NotEmpty
    String confirmPassword;
}
