package com.eveassist.client.user;

import com.eveassist.client.user.dto.EveAssistPasswordDetails;
import com.eveassist.client.user.dto.EveAssistUserDto;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public interface EveAssistUserService extends UserDetailsService {
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;

    SecurityPrincipal login(EveAssistPasswordDetails passwordDetails);

    UserDetails registerNewUser(EveAssistUserDto userDto, EveAssistPasswordDetails passwordDetails);

    void forgotPassword(String email);
}
