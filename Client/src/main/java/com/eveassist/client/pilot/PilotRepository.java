package com.eveassist.client.pilot;

import com.eveassist.client.pilot.entity.Pilot;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface PilotRepository extends PagingAndSortingRepository<Pilot, Long> {
}