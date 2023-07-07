package com.eveassist.api.sde.chr;

import com.eveassist.api.sde.chr.entity.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@AllArgsConstructor
@Service
public class ChrLookup {
    private final ChrAncestryRepository chrAncestryRepository;
    private final ChrAttributeRepository chrAttributeRepository;
    private final ChrBloodlineRepository chrBloodlineRepository;
    private final ChrFactionRepository chrFactionRepository;
    private final ChrRaceRepository chrRaceRepository;

    public Optional<ChrAncestry> lookupAncestry(Integer ancestryId) {
        return chrAncestryRepository.findById(ancestryId);
    }

    public Optional<ChrAttribute> lookupAttribute(Integer attributeId) {
        return chrAttributeRepository.findById(attributeId);
    }

    public Optional<ChrBloodline> lookupBloodline(Integer bloodlineId) {
        return chrBloodlineRepository.findById(bloodlineId);
    }

    public Optional<ChrFaction> lookupFaction(Integer factionId) {
        return chrFactionRepository.findById(factionId);
    }

    public Optional<ChrRace> lookupRace(Integer raceId) {
        return chrRaceRepository.findById(raceId);
    }
}
