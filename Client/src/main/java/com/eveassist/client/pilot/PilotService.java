package com.eveassist.client.pilot;

import com.eveassist.client.pilot.dto.PilotAuthDto;
import com.eveassist.client.pilot.dto.PilotDto;

import java.util.List;
import java.util.Optional;

public interface PilotService {
    PilotDto savePilot(PilotAuthDto pilotAuth);

    List<PilotDto> getAllPilots();

    void linkState(String name, String state);

    Optional<String> getUserFromState(String state);
}
