package com.eveassist.api.sde.chr;

import com.eveassist.api.sde.chr.entity.*;

public interface ChrDao {

    ChrAncestry getAncestry(Integer id);

    ChrAttribute getAttribute(Integer id);

    ChrBloodline getBloodline(Integer id);

    ChrFaction getFaction(Integer id);

    ChrRace getRace(Integer id);
}
