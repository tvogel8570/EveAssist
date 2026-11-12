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
    default void forgetCharacter(long eveCharacterId) {
        throw new UnsupportedOperationException();
    }

    @Override
    default void forgetCorporation(long eveCorporationId) {
        throw new UnsupportedOperationException();
    }

    @Override
    default StructureLookup structure(long eveCharacterId, long structureId) {
        throw new UnsupportedOperationException();
    }

    @Override
    default EsiResult<Long> characterCorporationId(long eveCharacterId) {
        throw new UnsupportedOperationException();
    }

    @Override
    default EsiResult<java.util.Set<String>> characterRoles(long eveCharacterId) {
        throw new UnsupportedOperationException();
    }

    @Override
    default EsiResult<String> corporationName(long eveCorporationId) {
        throw new UnsupportedOperationException();
    }

    @Override
    default EsiResult<List<CharacterAsset>> corporationAssets(long eveCorporationId, long viaEveCharacterId) {
        throw new UnsupportedOperationException();
    }

    @Override
    default EsiResult<java.util.Map<Long, String>> characterAssetNames(long eveCharacterId, List<Long> itemIds) {
        throw new UnsupportedOperationException();
    }

    @Override
    default EsiResult<java.util.Map<Long, String>> corporationAssetNames(long eveCorporationId,
            long viaEveCharacterId, List<Long> itemIds) {
        throw new UnsupportedOperationException();
    }

    @Override
    default EsiResult<java.util.Map<Integer, String>> corporationHangars(long eveCorporationId,
            long viaEveCharacterId) {
        throw new UnsupportedOperationException();
    }
}
