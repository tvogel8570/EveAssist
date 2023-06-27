package com.eveassist.api.esi.response;

import java.io.Serializable;
import java.time.LocalDateTime;


public record CharactersDto(
        Integer alliance_id,
        LocalDateTime birthday,
        Integer bloodline_id,
        Integer corporation_id,
        String description,
        Integer faction_id,
        String gender,
        String name,
        Integer race_id,
        Double security_status,
        String title) implements Serializable {
}
