package com.eveassist.market;

import com.eveassist.esi.CharacterAsset;
import com.eveassist.esi.EsiClient;
import com.eveassist.esi.EsiResult;
import com.eveassist.esi.PublicContract;
import com.eveassist.esi.PublicContractItem;
import com.eveassist.esi.StructureLookup;
import java.util.List;

/** An ESI that answers market orders only, so a test can write it as a lambda. */
@FunctionalInterface
interface OrdersEsi extends EsiClient {

    @Override
    default EsiResult<List<PublicContract>> publicContracts(int regionId) {
        throw new UnsupportedOperationException();
    }

    @Override
    default EsiResult<List<PublicContractItem>> publicContractItems(long contractId) {
        throw new UnsupportedOperationException();
    }

    @Override
    default EsiResult<List<CharacterAsset>> characterAssets(long eveCharacterId) {
        throw new UnsupportedOperationException();
    }

    @Override
    default StructureLookup structure(long eveCharacterId, long structureId) {
        throw new UnsupportedOperationException();
    }
}
