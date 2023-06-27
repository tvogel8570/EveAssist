package com.eveassist.api.user.impl;

import com.eveassist.api.user.EveAssistUserRepository;
import com.eveassist.api.user.EveAssistUserService;
import com.eveassist.api.user.SecurityPrincipal;
import com.eveassist.api.user.dto.EveAssistPasswordDetails;
import com.eveassist.api.user.dto.EveAssistUserDto;
import com.eveassist.api.user.entity.EveAssistUser;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class EveAssistUserServiceImpl implements UserDetailsService, EveAssistUserService {
    private final EveAssistUserRepository userRepository;

    public EveAssistUserServiceImpl(EveAssistUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @param username - user's email is default username. the username identifying the user whose data is required.
     *                 Case-insensitive search
     * @return fully populated user record (never null)
     * @throws UsernameNotFoundException – if the user could not be found or the user has no GrantedAuthorities
     */
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        EveAssistUser eveAssistUser = userRepository.findByEmailIgnoreCase(username);
        if (eveAssistUser == null || eveAssistUser.getAuthorities().isEmpty())
            throw new UsernameNotFoundException(String.format("user with email [%s] not found", username));
        return eveAssistUser;
    }

    @Override
    public SecurityPrincipal login(EveAssistPasswordDetails passwordDetails) {
        return null;
    }

    @Override
    public UserDetails registerNewUser(EveAssistUserDto userDto, EveAssistPasswordDetails passwordDetails) {
        return null;
    }

    @Override
    public void forgotPassword(String email) {
        // TODO implement if needed
    }
}
