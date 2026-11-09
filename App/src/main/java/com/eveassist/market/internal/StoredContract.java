package com.eveassist.market.internal;

import java.math.BigDecimal;
import java.util.List;

/** A public item exchange contract whose lines have been read. */
record StoredContract(long contractId, BigDecimal price, List<ContractLine> lines) {
}
