package com.eveassist.client.user.response;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class LoginFormResponse {
    String email;
    String screenName;
    @NotEmpty
    String password;
}
