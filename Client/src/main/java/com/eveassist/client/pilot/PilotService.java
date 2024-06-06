package com.eveassist.client.pilot;

import com.eveassist.client.pilot.dto.PilotAuthDto;
import com.eveassist.client.pilot.dto.PilotListInfo;

import java.util.List;
import java.util.UUID;

public interface PilotService {
    void createPilot(String eveUserId, PilotAuthDto pilotAuth);

    List<PilotListInfo> getPilotListForUser(UUID userId);

    void updateUserPilotsFromEsi(UUID uniqueUser, String eaAuthToken, List<Long> requestedPilotIds);
}
