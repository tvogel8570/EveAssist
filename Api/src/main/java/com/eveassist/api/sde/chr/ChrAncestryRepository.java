package com.eveassist.api.sde.chr;

import com.eveassist.api.sde.chr.entity.ChrAncestry;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChrAncestryRepository extends CrudRepository<ChrAncestry, Integer> {

}
