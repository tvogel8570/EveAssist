package com.eveassist.api.user;


import com.eveassist.api.user.dto.EveAssistUserListDto;
import com.eveassist.api.user.entity.EveAssistUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EveAssistUserRepository extends JpaRepository<EveAssistUser, Long> {
    EveAssistUser findByEmailIgnoreCase(String email);

    EveAssistUser findByUniqueUser(String uniqueUser);

    @Query("select new com.eveassist.api.user.dto.EveAssistUserListDto(e.uniqueUser, e.email, e.screenName) from EveAssistUser e")
    List<EveAssistUserListDto> getAllUsersList();
}
