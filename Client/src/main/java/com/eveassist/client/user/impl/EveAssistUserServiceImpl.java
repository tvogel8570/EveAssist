package com.eveassist.client.user.impl;

import com.eveassist.client.user.EveAssistUserRepository;
import com.eveassist.client.user.EveAssistUserService;
import com.eveassist.client.user.dto.EveAssistPasswordDetails;
import com.eveassist.client.user.dto.EveAssistUserDto;
import com.eveassist.client.user.response.RegistrationFormResponse;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class EveAssistUserServiceImpl implements EveAssistUserService {
    private final EveAssistUserRepository userRepository;

    public EveAssistUserServiceImpl(EveAssistUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails registerNewUser(EveAssistUserDto userDto, EveAssistPasswordDetails passwordDetails) {
        return null;
    }

    @Override
    public String registerNewUser(RegistrationFormResponse formData) {
        return "";
    }
}
