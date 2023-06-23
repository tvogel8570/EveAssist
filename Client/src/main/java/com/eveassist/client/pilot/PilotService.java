package com.eveassist.client.pilot;

import com.eveassist.client.pilot.dto.PilotAuthDto;
import com.eveassist.client.pilot.dto.PilotDto;

public interface PilotService {
    PilotDto savePilot(PilotAuthDto pilotAuth);
}
