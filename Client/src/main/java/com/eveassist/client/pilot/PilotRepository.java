package com.eveassist.client.pilot;

import com.eveassist.client.pilot.dto.PilotListInfo;
import com.eveassist.client.pilot.entity.Pilot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListPagingAndSortingRepository;

import java.util.List;
import java.util.UUID;

public interface PilotRepository extends ListPagingAndSortingRepository<Pilot, Long> {

    @Query("""
            select new com.eveassist.client.pilot.dto.PilotListInfo(p.ownerHash, p.evePilotId, p.name, p
            .portraitUrlTiny)
              from Pilot p where p.eveAssistUser.uniqueUser = ?1 order by p.name""")
    List<PilotListInfo> findByEveAssistUser_UniqueUserOrderByNameAsc(UUID uniqueUser, Pageable pageable);
}