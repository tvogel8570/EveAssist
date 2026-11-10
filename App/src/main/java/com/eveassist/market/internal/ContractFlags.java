package com.eveassist.market.internal;

import com.eveassist.esi.PublicContractItem;
import com.eveassist.sde.TypeId;
import java.util.List;
import java.util.function.Predicate;

/**
 * Why a contract is set aside. Items with no market price, such as blueprint originals and Abyssal items, make a
 * contract hard to split into prices, and each kind will need a rule of its own. Until then such contracts are flagged
 * and not looked at again. A contract can carry both flags.
 *
 * @param bpo     a line is a blueprint original (see {@link PublicContractItem#blueprintOriginal()})
 * @param abyssal a line is an Abyssal (mutated) item
 */
record ContractFlags(boolean bpo, boolean abyssal) {

    static ContractFlags of(List<PublicContractItem> items, Predicate<TypeId> isAbyssal) {
        var bpo = items.stream().anyMatch(PublicContractItem::blueprintOriginal);
        var abyssal = items.stream().anyMatch(item -> isAbyssal.test(new TypeId(item.typeId())));
        return new ContractFlags(bpo, abyssal);
    }

    boolean any() {
        return bpo || abyssal;
    }
}
