package com.eveassist.client.user;


import com.eveassist.client.user.dto.EveAssistUserListDto;
import com.eveassist.client.user.entity.EveAssistUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface EveAssistUserRepository extends JpaRepository<EveAssistUser, Long> {
    EveAssistUser findByEmailIgnoreCase(String email);

    EveAssistUser findByUniqueUser(UUID uniqueUser);

    @Query("""
            select new com.eveassist.client.user.dto.EveAssistUserListDto(e.uniqueUser, e.email, e.screenName)
            from EveAssistUser e""")
    List<EveAssistUserListDto> getAllUsersList();
}
