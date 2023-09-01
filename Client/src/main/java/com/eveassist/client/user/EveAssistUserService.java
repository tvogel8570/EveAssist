package com.eveassist.client.user;

import com.eveassist.client.user.dto.EveAssistPasswordDetails;
import com.eveassist.client.user.dto.EveAssistUserDto;
import com.eveassist.client.user.response.LoginFormResponse;
import com.eveassist.client.user.response.RegistrationFormResponse;
import org.springframework.security.core.userdetails.UserDetails;

public interface EveAssistUserService {
    UserDetails registerNewUser(EveAssistUserDto userDto, EveAssistPasswordDetails passwordDetails);

    String registerNewUser(RegistrationFormResponse formData);

    Boolean confirmEmailString(String confirm);

    Boolean login(LoginFormResponse loginFormResponse);
}
