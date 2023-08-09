package com.eveassist.api.sde.chr;

import com.eveassist.api.sde.chr.entity.*;

public interface ChrDao {

    ChrAncestry getAncestry(Long id);

    ChrAttribute getAttribute(Long id);

    ChrBloodline getBloodline(Long id);

    ChrFaction getFaction(Long id);

    ChrRace getRace(Long id);
}
