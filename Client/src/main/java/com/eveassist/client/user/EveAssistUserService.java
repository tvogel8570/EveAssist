package com.eveassist.client.user;

import com.eveassist.client.user.dto.EveAssistUserDto;

import java.util.UUID;

public interface EveAssistUserService {

    boolean existsUserByUniqueUser(UUID userUnique);

    EveAssistUserDto addUser(EveAssistUserDto build);
}
