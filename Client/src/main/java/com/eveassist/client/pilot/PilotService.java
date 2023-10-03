package com.eveassist.client.pilot;

import com.eveassist.client.pilot.dto.PilotAuthDto;
import com.eveassist.client.pilot.dto.PilotDto;
import com.eveassist.client.pilot.dto.PilotListInfo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PilotService {
    PilotDto savePilot(String eveUserId, PilotAuthDto pilotAuth);

    List<PilotListInfo> getPilotsWithDetailsForUser(UUID userId, String token);

    void linkState(String name, String state);

    Optional<String> getUserFromState(String state);
}
