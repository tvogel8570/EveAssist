package com.eveassist.esi;

import java.math.BigDecimal;

public record MarketOrder(long orderId, int typeId, long locationId, boolean buy, BigDecimal price, long volumeRemain) {
}
