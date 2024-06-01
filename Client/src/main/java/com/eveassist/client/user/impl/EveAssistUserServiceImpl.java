package com.eveassist.client.user.impl;

import com.eveassist.client.core.exception.DuplicateEntityException;
import com.eveassist.client.core.exception.EaBusinessException;
import com.eveassist.client.user.EveAssistUserMapper;
import com.eveassist.client.user.EveAssistUserRepository;
import com.eveassist.client.user.EveAssistUserService;
import com.eveassist.client.user.dto.EveAssistUserDto;
import com.eveassist.client.user.entity.EveAssistUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional
public class EveAssistUserServiceImpl implements EveAssistUserService {
    private final EveAssistUserRepository userRepository;
    private final EveAssistUserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public boolean existsUserByUniqueUser(UUID userUnique) {
        return userRepository.existsByUniqueUser(userUnique);
    }

    @Override
    public EveAssistUserDto addUser(EveAssistUserDto userDto) {
        try {
            EveAssistUser save = userRepository.saveAndFlush(userMapper.toEntity(userDto));
            return userMapper.toDto(save);
        } catch (DataIntegrityViolationException e) {
            log.info("DataIntegrityViolationException saving user [%s] - [%s]".formatted(userDto, e.getMessage()));
            throw new DuplicateEntityException("One or more values submitted for user conflicts with an existing user");
        } catch (Exception e) {
            log.info("Unexpected Exception saving user [%s] - [%s]".formatted(userDto, e.getMessage()));
            throw new EaBusinessException("Unable to save user");
        }

    }
}
