package com.eveassist.client.user;

import com.eveassist.client.user.dto.EveAssistPasswordDetails;
import com.eveassist.client.user.dto.EveAssistUserDto;
import org.springframework.security.core.userdetails.UserDetails;

public interface EveAssistUserService {
    UserDetails registerNewUser(EveAssistUserDto userDto, EveAssistPasswordDetails passwordDetails);
}
