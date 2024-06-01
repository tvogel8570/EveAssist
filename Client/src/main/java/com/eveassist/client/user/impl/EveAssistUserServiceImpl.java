package com.eveassist.client.user.impl;

import com.eveassist.client.user.EveAssistUserRepository;
import com.eveassist.client.user.EveAssistUserService;
import com.eveassist.client.user.dto.EveAssistPasswordDetails;
import com.eveassist.client.user.dto.EveAssistUserDto;
import com.eveassist.client.user.response.LoginFormResponse;
import com.eveassist.client.user.response.RegistrationFormResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class EveAssistUserServiceImpl implements EveAssistUserService {
    private final EveAssistUserRepository userRepository;

    @Override
    public UserDetails registerNewUser(EveAssistUserDto userDto, EveAssistPasswordDetails passwordDetails) {
        return null;
    }

    @Override
    public String registerNewUser(RegistrationFormResponse formData) {
        return "";
    }

    @Override
    public Boolean confirmEmailString(String confirm) {
        return null;
    }

    @Override
    public Boolean login(LoginFormResponse loginFormResponse) {
        return Boolean.TRUE;
    }
}
